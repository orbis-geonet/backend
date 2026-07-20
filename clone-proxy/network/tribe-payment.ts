// Tribe crypto payments (DEVNET, isolated from the mainnet indexer).
//
// Responsibilities:
//   - quote a USD price to an ORBIS amount using a FIXED env rate (no oracle),
//   - build the UNSIGNED pay_tribe transaction the buyer signs in their wallet,
//   - persist a payment intent (Mongo) keyed by a 32-byte payment_ref,
//   - watch the devnet program for TribePaymentMade and mark the intent PAID,
//   - notify the Java backend (Phase 3) that the payment settled.
//
// Everything here uses its own devnet Connection + the devnet program id; it never
// touches the mainnet RPC_URL / PROGRAM_ID used by the data-sync indexer. Env is read
// lazily (inside functions) so this module is safe to import before dotenv runs.
import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey, Transaction } from "@solana/web3.js";
import {
    TOKEN_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
    getAssociatedTokenAddressSync,
    createAssociatedTokenAccountIdempotentInstruction,
    getMint,
} from "@solana/spl-token";
import { BN } from "bn.js";
import { randomBytes, createHmac } from "node:crypto";
import { readFileSync } from "node:fs";
import axios from "axios";
import { log } from "../logger.js";

const IDL = JSON.parse(readFileSync(new URL("../programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));
const INTENTS = "tribe_payment_intents";
// sha256("event:TribePaymentMade")[:8] — verified against the deployed program.
const TRIBE_PAYMENT_DISC = Buffer.from([21, 131, 229, 200, 27, 98, 35, 230]);
const TRIBE_EVENT_LEN = 8 + 32 + 32 + 8 + 32 + 32 + 32 + 8 + 8 + 8; // = 200
const QUOTE_TTL_MS = 10 * 60 * 1000;

export function paymentsEnabled(): boolean {
    return Boolean(process.env.DEVNET_RPC_URL && process.env.DEVNET_PAYMENTS_PROGRAM_ID);
}

function devnetConnection(): Connection {
    const url = process.env.DEVNET_RPC_URL;
    if (!url) throw new Error("DEVNET_RPC_URL not set");
    return new Connection(url, "confirmed");
}

function programId(): PublicKey {
    const id = process.env.DEVNET_PAYMENTS_PROGRAM_ID;
    if (!id) throw new Error("DEVNET_PAYMENTS_PROGRAM_ID not set");
    return new PublicKey(id);
}

function devnetProgram(connection: Connection): anchor.Program {
    const provider = new anchor.AnchorProvider(connection, new anchor.Wallet(Keypair.generate()), { preflightCommitment: "confirmed" });
    return new anchor.Program(IDL as any, provider);
}

function ata(mint: PublicKey, owner: PublicKey): PublicKey {
    return getAssociatedTokenAddressSync(mint, owner, true, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID);
}

function buildLocalApiKey(): string {
    const secret = process.env.ORBIS_API_SECRET || "";
    const payload = Buffer.from(JSON.stringify({ role: "internal", timestamp: Date.now() })).toString("base64url");
    const sig = createHmac("sha256", secret).update(payload).digest("base64url");
    return `${payload}.${sig}`;
}

export function quoteUsdToOrbis(priceUsd: number, decimals: number): bigint {
    const rate = Number(process.env.ORBIS_USD_PRICE || "0");
    if (!rate || rate <= 0) throw new Error("ORBIS_USD_PRICE not set (USD price of 1 ORBIS)");
    const tokens = priceUsd / rate;
    return BigInt(Math.ceil(tokens * 10 ** decimals));
}

export interface IntentParams {
    userKey?: string;
    kind: "subscription" | "purchase";
    itemKey?: string;
    quantity?: number;
    payerWallet: string;
    ownerWallet: string;
    priceUsd: number;
    operatorWallet: string;       // clone operator pubkey — default 7% recipient
    clonePayoutWallet?: string;   // optional override for the 7% recipient
}

export interface IntentResult {
    ref: string;
    amountOrbis: string;
    rate: number;
    expiresAt: string;
    unsignedTxBase64: string;
    recipients: Record<string, string>;
}

export async function createTribePaymentIntent(db: any, p: IntentParams): Promise<IntentResult> {
    const connection = devnetConnection();
    const program = devnetProgram(connection);
    const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], programId());
    const config: any = await program.account.globalConfig.fetch(configPda);
    const mint = new PublicKey(config.orbisMint);
    const treasury = new PublicKey(config.treasury);
    const decimals = (await getMint(connection, mint)).decimals;

    const amount = quoteUsdToOrbis(p.priceUsd, decimals);
    const payer = new PublicKey(p.payerWallet);
    const owner = new PublicKey(p.ownerWallet);
    // 7% recipient: explicit override → CLONE_PAYOUT_WALLET env → the clone operator wallet.
    const clone = new PublicKey(p.clonePayoutWallet || process.env.CLONE_PAYOUT_WALLET || p.operatorWallet);

    const payerAta = ata(mint, payer);
    const ownerAta = ata(mint, owner);
    const cloneAta = ata(mint, clone);
    const treasuryAta = ata(mint, treasury);

    const ref = randomBytes(32);
    const refHex = ref.toString("hex");

    const payTribeIx = await program.methods
        .payTribe(new BN(amount.toString()), Array.from(ref))
        .accounts({
            payer,
            payerTokenAccount: payerAta,
            tribeOwnerTokenAccount: ownerAta,
            cloneTokenAccount: cloneAta,
            treasuryTokenAccount: treasuryAta,
            config: configPda,
            tokenProgram: TOKEN_PROGRAM_ID,
        } as any)
        .instruction();

    // Buyer is fee payer + rent payer for any missing recipient ATAs.
    const tx = new Transaction().add(
        createAssociatedTokenAccountIdempotentInstruction(payer, ownerAta, owner, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
        createAssociatedTokenAccountIdempotentInstruction(payer, cloneAta, clone, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
        createAssociatedTokenAccountIdempotentInstruction(payer, treasuryAta, treasury, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
        payTribeIx,
    );
    tx.feePayer = payer;
    tx.recentBlockhash = (await connection.getLatestBlockhash("finalized")).blockhash;
    const unsignedTxBase64 = tx.serialize({ requireAllSignatures: false, verifySignatures: false }).toString("base64");

    const rate = Number(process.env.ORBIS_USD_PRICE);
    const now = Date.now();
    const expiresAt = new Date(now + QUOTE_TTL_MS);
    const recipients = {
        owner: owner.toBase58(), clone: clone.toBase58(), treasury: treasury.toBase58(),
        ownerAta: ownerAta.toBase58(), cloneAta: cloneAta.toBase58(), treasuryAta: treasuryAta.toBase58(),
    };
    await db.collection(INTENTS).insertOne({
        ref: refHex,
        status: "PENDING",
        amountOrbis: amount.toString(),
        priceUsd: p.priceUsd,
        rate,
        userKey: p.userKey ?? null,
        kind: p.kind,
        itemKey: p.itemKey ?? null,
        quantity: p.quantity ?? 1,
        payerWallet: payer.toBase58(),
        recipients,
        createdAt: new Date(now),
        expiresAt,
    });

    return { ref: refHex, amountOrbis: amount.toString(), rate, expiresAt: expiresAt.toISOString(), unsignedTxBase64, recipients };
}

export async function getTribePaymentStatus(db: any, ref: string) {
    const intent = await db.collection(INTENTS).findOne({ ref });
    if (!intent) return null;
    return { ref, status: intent.status, txSignature: intent.txSignature ?? null, amountOrbis: intent.amountOrbis };
}

interface DecodedTribeEvent {
    payer: PublicKey;
    refHex: string;
    amount: bigint;
    ownerAta: PublicKey;
    cloneAta: PublicKey;
    treasuryAta: PublicKey;
}

function decodeTribeEvent(base64: string): DecodedTribeEvent | null {
    let buf: Buffer;
    try { buf = Buffer.from(base64, "base64"); } catch { return null; }
    if (buf.length < TRIBE_EVENT_LEN) return null;
    if (!buf.subarray(0, 8).equals(TRIBE_PAYMENT_DISC)) return null;
    let o = 8;
    const payer = new PublicKey(buf.subarray(o, o + 32)); o += 32;
    const refHex = buf.subarray(o, o + 32).toString("hex"); o += 32;
    const amount = buf.readBigUInt64LE(o); o += 8;
    const ownerAta = new PublicKey(buf.subarray(o, o + 32)); o += 32;
    const cloneAta = new PublicKey(buf.subarray(o, o + 32)); o += 32;
    const treasuryAta = new PublicKey(buf.subarray(o, o + 32)); o += 32;
    return { payer, refHex, amount, ownerAta, cloneAta, treasuryAta };
}

async function processTribeEvent(db: any, ev: DecodedTribeEvent, signature: string) {
    const intent = await db.collection(INTENTS).findOne({ ref: ev.refHex });
    if (!intent) return;               // not one of our payments
    if (intent.status === "PAID") return; // idempotent

    if (BigInt(intent.amountOrbis) !== ev.amount) {
        log.warn(`[payments] amount mismatch ref=${ev.refHex.slice(0, 8)} onchain=${ev.amount} quoted=${intent.amountOrbis}`);
        return;
    }
    const r = intent.recipients || {};
    if (r.ownerAta !== ev.ownerAta.toBase58() || r.cloneAta !== ev.cloneAta.toBase58() || r.treasuryAta !== ev.treasuryAta.toBase58()) {
        log.warn(`[payments] recipient mismatch ref=${ev.refHex.slice(0, 8)} — ignoring`);
        return;
    }

    // status guard makes this idempotent across the listener + the reconcile poller.
    const upd = await db.collection(INTENTS).updateOne(
        { ref: ev.refHex, status: { $ne: "PAID" } },
        { $set: { status: "PAID", txSignature: signature, paidAt: new Date() } },
    );
    if (upd.modifiedCount === 0) return;
    log.info(`[payments] ✅ PAID ref=${ev.refHex.slice(0, 8)} tx=${signature.slice(0, 8)}`);
    await confirmToJava(db, { ...intent, status: "PAID" }, signature);
}

async function confirmToJava(db: any, intent: any, signature: string) {
    const base = (process.env.JAVA_BACKEND_URL || "").replace(/\/$/, "");
    if (!base) return;
    const url = `${base}/internal/crypto/payment-confirmed`;
    try {
        await axios.post(url, { ref: intent.ref, txSignature: signature }, {
            headers: { "X-API-Key": buildLocalApiKey(), "Content-Type": "application/json" },
            timeout: 20_000,
        });
        await db.collection(INTENTS).updateOne({ ref: intent.ref }, { $set: { confirmedToJava: true } });
        log.info(`[payments] confirmed to Java ref=${intent.ref.slice(0, 8)}`);
    } catch (e: any) {
        // The Java confirm endpoint arrives in Phase 3 — until then this is expected to fail.
        const reason = e.response?.status ?? e.message;
        log.warn(`[payments] Java confirm failed ref=${intent.ref.slice(0, 8)}: ${reason}`);
        await db.collection(INTENTS).updateOne({ ref: intent.ref }, { $set: { confirmedToJava: false, javaConfirmError: String(reason) } });
    }
}

export function startTribePaymentsListener(db: any) {
    if (!paymentsEnabled()) {
        log.warn("[payments] DEVNET_RPC_URL / DEVNET_PAYMENTS_PROGRAM_ID not set — tribe payments listener disabled");
        return;
    }
    const connection = devnetConnection();
    const pid = programId();
    log.info(`[payments] listening for TribePaymentMade on devnet program ${pid.toBase58()}`);

    connection.onLogs(pid, async (logs) => {
        try {
            for (const line of logs.logs) {
                if (!line.includes("Program data: ")) continue;
                const ev = decodeTribeEvent(line.split("Program data: ")[1]);
                if (ev) await processTribeEvent(db, ev, logs.signature);
            }
        } catch (e: any) {
            log.warn(`[payments] listener error: ${e.message}`);
        }
    }, "confirmed");

    // Fallback reconcile: re-scan recent program signatures to catch anything the
    // websocket dropped. processTribeEvent is idempotent, so double-processing is safe.
    const seen = new Set<string>();
    setInterval(async () => {
        try {
            const sigs = await connection.getSignaturesForAddress(pid, { limit: 25 });
            for (const s of sigs) {
                if (seen.has(s.signature)) continue;
                seen.add(s.signature);
                const tx = await connection.getTransaction(s.signature, { commitment: "confirmed", maxSupportedTransactionVersion: 0 });
                for (const line of tx?.meta?.logMessages ?? []) {
                    if (!line.includes("Program data: ")) continue;
                    const ev = decodeTribeEvent(line.split("Program data: ")[1]);
                    if (ev) await processTribeEvent(db, ev, s.signature);
                }
            }
            if (seen.size > 1000) seen.clear();
        } catch (e: any) {
            log.warn(`[payments] reconcile error: ${e.message}`);
        }
    }, 30_000);

    // Expire stale PENDING intents (quote/blockhash no longer valid).
    setInterval(async () => {
        try {
            await db.collection(INTENTS).updateMany(
                { status: "PENDING", expiresAt: { $lt: new Date() } },
                { $set: { status: "EXPIRED" } },
            );
        } catch (e: any) {
            log.warn(`[payments] sweeper error: ${e.message}`);
        }
    }, 60_000);
}
