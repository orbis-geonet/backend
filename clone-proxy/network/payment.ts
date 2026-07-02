import { Connection, Keypair, PublicKey, SystemProgram, SYSVAR_RENT_PUBKEY } from "@solana/web3.js";
import { getOrCreateAssociatedTokenAccount } from "@solana/spl-token";
import { TOKEN_PROGRAM_ID } from "@solana/spl-token";
import * as anchor from "@coral-xyz/anchor";
import { BN } from "bn.js";
import { createHash } from "crypto";
import nacl from "tweetnacl";
import axios from "axios";
import { PROGRAM_ID } from "../config.js";
import { resolveExtraDataRequest } from "./extra-data-strategies.js";
import { readFileSync } from "node:fs";

const LOCAL_IDL = JSON.parse(readFileSync(new URL("../programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));

type PendingVoucher = {
    requester: string;
    escrow: string;
    totalFee: string;
    timestamp: number;
    timeout: NodeJS.Timeout;
};

const pendingVouchers = new Map<string, PendingVoucher>();
const cloneApiKeyCache = new Map<string, string>();
const escrowPaymentQueues = new Map<string, Promise<void>>();

async function runEscrowPayment(escrow: string, task: () => Promise<void>) {
    const previous = escrowPaymentQueues.get(escrow) ?? Promise.resolve();
    const next = previous.catch(() => undefined).then(task);
    escrowPaymentQueues.set(escrow, next);
    try {
        await next;
    } finally {
        if (escrowPaymentQueues.get(escrow) === next) {
            escrowPaymentQueues.delete(escrow);
        }
    }
}

async function getOrbisKeyForClone(baseUrl: string, payer: Keypair): Promise<string> {
    const cached = cloneApiKeyCache.get(baseUrl);
    if (cached) return cached;
    const message = Buffer.from("ORBIS_API_KEY_REQ");
    const sig = nacl.sign.detached(Uint8Array.from(message), payer.secretKey);
    const res = await axios.post(`${baseUrl}/v3/api-key`, {
        publicKey: payer.publicKey.toBase58(),
        signature: Buffer.from(sig).toString("base64")
    });
    const token: string = res.data.token;
    cloneApiKeyCache.set(baseUrl, token);
    return token;
}

export async function checkCloneRegistration(connection: Connection, requester: string): Promise<boolean> {
    try {
        const [cloneInfoPda] = PublicKey.findProgramAddressSync(
            [Buffer.from("clone_info"), new PublicKey(requester).toBuffer()],
            PROGRAM_ID
        );
        const info = await connection.getAccountInfo(cloneInfoPda);
        return info !== null;
    } catch {
        return false;
    }
}

export interface FetchAndPayResult {
    data: Buffer;
    dataJson?: any;
    source: string;
    claimTx?: string;
}

export async function calculateFee(connection: Connection, dataSize: number): Promise<InstanceType<typeof BN>> {
    const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], PROGRAM_ID);
    const wallet = new anchor.Wallet(Keypair.generate());
    const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
    const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);
    const config: any = await program.account.globalConfig.fetch(configPda);
    if (dataSize === 0) return new BN(0);
    const feePerMb = new BN(config.feePerMb.toString());
    const minFee = new BN(config.minFee.toString());
    const mbCount = Math.floor(dataSize / 1_048_576);
    const byteFee = feePerMb.muln(mbCount);
    return byteFee.gt(minFee) ? byteFee : minFee;
}

async function getEscrowBalance(program: anchor.Program, escrowPda: PublicKey): Promise<InstanceType<typeof BN>> {
    try {
        const escrowData: any = await program.account.streamingEscrow.fetch(escrowPda);
        return new BN(escrowData.amountLocked).sub(new BN(escrowData.amountClaimed));
    } catch {
        return new BN(0);
    }
}

async function ensureEscrowBalance(
    payer: Keypair,
    connection: Connection,
    program: anchor.Program,
    providerPubkey: PublicKey,
    requiredFee: InstanceType<typeof BN>
) {
    const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], PROGRAM_ID);
    const [payerCloneInfoPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("clone_info"), payer.publicKey.toBuffer()],
        PROGRAM_ID
    );
    const [escrowPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("streaming_escrow"), payer.publicKey.toBuffer(), providerPubkey.toBuffer()],
        PROGRAM_ID
    );
    const [escrowVaultPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("escrow_vault"), escrowPda.toBuffer()],
        PROGRAM_ID
    );

    const currentBalance = await getEscrowBalance(program, escrowPda);
    if (currentBalance.lt(requiredFee)) {
        const amountToLock = requiredFee.sub(currentBalance);
        console.log(`     -> Escrow balance low. Topping up ${amountToLock.toString()} units...`);

        const globalConfig: any = await program.account.globalConfig.fetch(configPda);
        const orbisMint = globalConfig.orbisMint;
        const mainTokenAccount = await anchor.utils.token.associatedAddress({ mint: orbisMint, owner: payer.publicKey });
        const mainAccount = await connection.getTokenAccountBalance(mainTokenAccount);
        const mainBalance = new BN(mainAccount.value.amount);

        if (mainBalance.lt(amountToLock)) {
            throw new Error(`Insufficient ORBIS balance. Needed: ${amountToLock.toString()}, Available: ${mainBalance.toString()}`);
        }

        const escrowAccountInfo = await connection.getAccountInfo(escrowPda);
        if (!escrowAccountInfo) {
            await program.methods.initStreamingEscrow(requiredFee)
                .accounts({
                    streamingEscrow: escrowPda,
                    escrowVault: escrowVaultPda,
                    orbisMint,
                    requester: payer.publicKey,
                    requesterCloneInfo: payerCloneInfoPda,
                    requesterTokenAccount: mainTokenAccount,
                    provider: providerPubkey,
                    tokenProgram: TOKEN_PROGRAM_ID,
                    systemProgram: SystemProgram.programId,
                    rent: SYSVAR_RENT_PUBKEY,
                })
                .rpc();
        } else {
            await program.methods.topUpStreamingEscrow(amountToLock)
                .accounts({
                    streamingEscrow: escrowPda,
                    escrowVault: escrowVaultPda,
                    requester: payer.publicKey,
                    requesterCloneInfo: payerCloneInfoPda,
                    requesterTokenAccount: mainTokenAccount,
                    provider: providerPubkey,
                    tokenProgram: TOKEN_PROGRAM_ID,
                })
                .rpc();
        }
    }
}

export async function fetchAndPay(
    payer: Keypair,
    connection: Connection,
    leaf: any,
    db: any,
    baseUrlOverride?: string,
    method: string = "GET",
    body?: any,
    extraHeaders: Record<string, string> = {}
): Promise<FetchAndPayResult> {
    const providerPubkey = new PublicKey(leaf.provider);

    let baseUrl = baseUrlOverride;
    if (!baseUrl) {
        const wallet = new anchor.Wallet(payer);
        const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
        const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);

        const [providerCloneInfoPda] = PublicKey.findProgramAddressSync(
            [Buffer.from("clone_info"), providerPubkey.toBuffer()],
            PROGRAM_ID
        );
        const cloneInfo: any = await program.account.cloneInfo.fetch(providerCloneInfoPda);
        baseUrl = cloneInfo.baseUrl.endsWith("/") ? cloneInfo.baseUrl.slice(0, -1) : cloneInfo.baseUrl;
    } else {
        baseUrl = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
    }

    const [escrowPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("streaming_escrow"), payer.publicKey.toBuffer(), providerPubkey.toBuffer()],
        PROGRAM_ID
    );

    const separator = leaf.batchPointer.includes("?") ? "&" : "?";
    const requestUrl = `${baseUrl}${leaf.batchPointer}${separator}escrow=${escrowPda.toBase58()}&requester=${payer.publicKey.toBase58()}`;
    console.log(`     -> Requesting data+quote from provider: ${requestUrl} [${method}]`);

    let orbisKey = await getOrbisKeyForClone(baseUrl!, payer);
    let response: any;
    try {
        const requestHeaders = { ...extraHeaders, "X-Orbis-Key": orbisKey };
        response = method.toUpperCase() === "POST" || method.toUpperCase() === "PUT"
            ? await axios({ method: method.toLowerCase(), url: requestUrl, data: body, headers: { ...requestHeaders, "Content-Type": "application/json" } })
            : await axios.get(requestUrl, { headers: requestHeaders });
    } catch (e: any) {
        if (e.response?.status === 401) {
            cloneApiKeyCache.delete(baseUrl!);
            orbisKey = await getOrbisKeyForClone(baseUrl!, payer);
            const requestHeaders = { ...extraHeaders, "X-Orbis-Key": orbisKey };
            response = method.toUpperCase() === "POST" || method.toUpperCase() === "PUT"
                ? await axios({ method: method.toLowerCase(), url: requestUrl, data: body, headers: { ...requestHeaders, "Content-Type": "application/json" } })
                : await axios.get(requestUrl, { headers: requestHeaders });
        } else {
            throw e;
        }
    }
    const { data: base64Data, requiredFee: feeStr } = response.data;
    const binaryData = Buffer.from(base64Data, "base64");
    const requiredFee = new BN(feeStr);

    const localHash = createHash("sha256").update(binaryData).digest("hex");
    console.log(`     -> Data received: ${binaryData.length} bytes, SHA256: ${localHash}. Quote: ${requiredFee.toString()}`);

    if (leaf.hash && localHash !== leaf.hash) {
        throw new Error(`Hash mismatch! Expected: ${leaf.hash}, Got: ${localHash}`);
    }

    const markEventSynced = async (details: Record<string, any> = {}) => {
        if (!leaf.event?._id) return;
        await db.collection("network_events").updateOne(
            { _id: leaf.event._id },
            {
                $set: {
                    status: "synced",
                    localHash,
                    syncedAt: new Date(),
                    updatedAt: new Date(),
                    ...details,
                },
                $unset: {
                    syncError: "",
                    cacheError: "",
                },
            }
        );
        console.log(`     -> Marked event as synced.`);
    };

    const recordEventSyncFailure = async (message: string) => {
        if (!leaf.event?._id) return;
        await db.collection("network_events").updateOne(
            { _id: leaf.event._id },
            {
                $set: {
                    status: "pending",
                    localHash,
                    syncError: message,
                    cacheError: message,
                    cacheStatus: "failed",
                    updatedAt: new Date(),
                },
            }
        );
    };


    const stringData = binaryData.toString().trim();
    const isDataEmpty = stringData === "[]" || stringData === "{}" || stringData === '{"content":[],"nextPage":null}' || stringData === "";

    if (!isDataEmpty) {
        void runEscrowPayment(escrowPda.toBase58(), async () => {
        try {
            const wallet = new anchor.Wallet(payer);
            const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
            const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);

            await ensureEscrowBalance(payer, connection, program, providerPubkey, requiredFee);

            const escrowOnChain: any = await program.account.streamingEscrow.fetch(escrowPda);
            const nonceBuf = Buffer.alloc(8);
            nonceBuf.writeBigUInt64LE(BigInt(escrowOnChain.amountClaimed.toString()));

            const dataSize = BigInt(binaryData.length);
            const sizeBuf = Buffer.alloc(8);
            sizeBuf.writeBigUInt64LE(dataSize);
            const hashBuf = Buffer.from(localHash, "hex");
            const signedMsg = Buffer.concat([escrowPda.toBuffer(), nonceBuf, sizeBuf, hashBuf]);
            const sig = nacl.sign.detached(Uint8Array.from(signedMsg), payer.secretKey);

            let extraDataRequest: any = null;
            const cacheDisabled = process.argv.includes("--no-cache") || process.env.NO_CACHE === "true";
            if (!cacheDisabled) {
                try {
                    const jsonObj = JSON.parse(binaryData.toString());
                    const docs = Array.isArray(jsonObj) ? jsonObj : [jsonObj];
                    extraDataRequest = resolveExtraDataRequest(docs, leaf.batchPointer);
                    console.log(`     -> Resolved extraDataRequest for path "${leaf.batchPointer.split("?")[0]}": ${JSON.stringify(extraDataRequest)}`);
                } catch (e) {
                    throw new Error(`Failed to extract extra data request: ${e}`);
                }
            } else {
                console.log(`     -> --no-cache flag active. Skipping extra data request.`);
            }

            console.log(`     -> Submitting background voucher for ${leaf.provider || 'unnamed'}...`);
            let voucherRes;
            try {
                voucherRes = await axios.post(`${baseUrl}/v3/vouchers`, {
                    signature: Buffer.from(sig).toString("base64"),
                    size: binaryData.length,
                    hash: localHash,
                    escrow: escrowPda.toBase58(),
                    extraDataRequest
                }, { headers: { "X-Orbis-Key": orbisKey } });
            } catch (e: any) {
                if (e.response && e.response.status === 403 && e.response.data?.error === "Escrow provider mismatch" && (process.argv.includes("--override") || process.env.OVERRIDE === "true")) {
                    const match = e.response.data.details?.match(/does not match me \((.*?)\)/);
                    if (match && match[1]) {
                        const newProviderStr = match[1];
                        console.log(`     -> --override flag enabled. Re-creating escrow for actual provider: ${newProviderStr}...`);

                        const newProviderPubkey = new PublicKey(newProviderStr);
                        await ensureEscrowBalance(payer, connection, program, newProviderPubkey, requiredFee);

                        const [newEscrowPda] = PublicKey.findProgramAddressSync(
                            [Buffer.from("streaming_escrow"), payer.publicKey.toBuffer(), newProviderPubkey.toBuffer()],
                            PROGRAM_ID
                        );

                        const newEscrowOnChain: any = await program.account.streamingEscrow.fetch(newEscrowPda);
                        const newNonceBuf = Buffer.alloc(8);
                        newNonceBuf.writeBigUInt64LE(BigInt(newEscrowOnChain.amountClaimed.toString()));
                        const signedMsg2 = Buffer.concat([newEscrowPda.toBuffer(), newNonceBuf, sizeBuf, hashBuf]);
                        const sig2 = nacl.sign.detached(Uint8Array.from(signedMsg2), payer.secretKey);

                        console.log(`     -> Re-submitting background voucher for overridden provider...`);
                        voucherRes = await axios.post(`${baseUrl}/v3/vouchers`, {
                            signature: Buffer.from(sig2).toString("base64"),
                            size: binaryData.length,
                            hash: localHash,
                            escrow: newEscrowPda.toBase58(),
                            extraDataRequest
                        }, { headers: { "X-Orbis-Key": orbisKey } });
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }

            if (voucherRes.data.status === "claimed") {
                console.log(`     -> Background payment confirmed! TX: ${voucherRes.data.tx}`);
                if (voucherRes.data.extraData && extraDataRequest) {
                    const extraBuffer = Buffer.from(voucherRes.data.extraData, "base64");
                    const { syncStateToDb } = await import("../models/borsh-schemas.js");
                    const resourceId = extraDataRequest.type === "users" ? "USER_STATE" : undefined;
                    await syncStateToDb(db, extraBuffer, resourceId);
                    try { console.log(`     -> Extra data JSON:`, JSON.parse(extraBuffer.toString("utf-8"))); } catch { console.log(`     -> Extra data (raw):`, extraBuffer.toString("utf-8")); }
                    console.log(`     -> Synced extra data to local db.`);
                    await markEventSynced({ cacheStatus: "completed" });
                } else if (extraDataRequest) {
                    throw new Error(`Provider claimed payment but did not return extra data for ${extraDataRequest.queryType}`);
                } else {
                    await markEventSynced({
                        cacheStatus: cacheDisabled ? "disabled" : "not-requested",
                        syncSkippedReason: cacheDisabled ? "cache-disabled" : "no-extra-data-request",
                    });
                }
            } else {
                console.log(`     -> Background voucher submitted (pending claim)`);
            }
        } catch (e: any) {
            const message = e.response?.data?.details || e.message || String(e);
            console.error(`     -> Background payment/cache failed: ${message}`);
            await recordEventSyncFailure(message);
        }
    }).catch((e: any) => {
        console.error(`     -> Escrow payment queue failed: ${e.message || String(e)}`);
    });
    } else {
        console.log(`     -> Returned data is empty. Skipping escrow payment and extra-data phase.`);
        await markEventSynced({ cacheStatus: "empty-response", syncSkippedReason: "empty-response" });
    }

    return { data: binaryData, source: baseUrl! };
}

export function startVoucherTimer(requester: string, escrow: string, totalFee: string, connection: Connection, payer: Keypair) {
    if (pendingVouchers.has(escrow)) {
        clearTimeout(pendingVouchers.get(escrow)!.timeout);
    }

    const timerId = setTimeout(async () => {
        console.log(`\n[Payment] Payment window closed for escrow: ${escrow}`);
        console.log(`   Requester: ${requester}`);
        console.log(`   -> Flagging requester on-chain for non-payment...`);

        try {
            const wallet = new anchor.Wallet(payer);
            const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
            const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);

            const [requesterCloneInfoPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("clone_info"), new PublicKey(requester).toBuffer()], PROGRAM_ID
            );

            const [escrowPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("streaming_escrow"), new PublicKey(requester).toBuffer(), payer.publicKey.toBuffer()],
                PROGRAM_ID
            );

            await program.methods.flagUnpaidRequest()
                .accounts({
                    requesterCloneInfo: requesterCloneInfoPda,
                    requester: new PublicKey(requester),
                    provider: payer.publicKey,
                    streamingEscrow: escrowPda,
                } as any).rpc();

            console.log(`   -> Successfully flagged requester on-chain.`);
        } catch (err: any) {
            console.error(`   -> On-chain flag failed: ${err.message}`);
        }
        pendingVouchers.delete(escrow);
    }, 60000);

    pendingVouchers.set(escrow, {
        requester, escrow, totalFee, timestamp: Date.now(), timeout: timerId
    });
}

export function clearVoucherTimer(escrow: string) {
    const pending = pendingVouchers.get(escrow);
    if (pending) {
        clearTimeout(pending.timeout);
        pendingVouchers.delete(escrow);
        console.log(`   [Payment] Voucher received. Timer cleared for escrow: ${escrow}`);
    }
}

