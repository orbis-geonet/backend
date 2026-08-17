import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey, SystemProgram, Transaction } from "@solana/web3.js";
import {
    ASSOCIATED_TOKEN_PROGRAM_ID,
    TOKEN_PROGRAM_ID,
    createAssociatedTokenAccountIdempotentInstruction,
    getAssociatedTokenAddressSync,
    getMint,
} from "@solana/spl-token";
import { BN } from "bn.js";
import axios from "axios";
import { createHmac, randomBytes } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import { log } from "../logger.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const IDL = JSON.parse(readFileSync(new URL("../programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));
const INTENTS = "tribe_payment_intents";
const TRIBE_PAYMENT_DISC = Buffer.from([21, 131, 229, 200, 27, 98, 35, 230]);
const TRIBE_EVENT_LEN = 8 + 32 + 32 + 8 + 32 + 32 + 32 + 8 + 8 + 8;
const QUOTE_TTL_MS = 10 * 60 * 1000;
const MAX_DISTRIBUTION_ATTEMPTS = 3;
const LAMPORTS_PER_SOL = 1_000_000_000n;
const BPS_DENOMINATOR = 10_000n;
const CLONE_BPS = 700n;
const TREASURY_BPS = 300n;

function short(value: unknown): string {
    const text = String(value || "");
    if (!text) return "-";
    return text.length <= 12 ? text : `${text.slice(0, 6)}...${text.slice(-4)}`;
}

function errorDetails(e: any): string {
    if (!e) return "unknown error";
    return e.stack || e.message || String(e);
}

function paymentLog(trace: string | undefined, message: string) {
    log.info(`[payments:intent:${trace || "worker"}] ${message}`);
}

function paymentWarn(trace: string | undefined, message: string) {
    log.warn(`[payments:intent:${trace || "worker"}] ${message}`);
}

function paymentError(trace: string | undefined, message: string, e: any) {
    log.error(`[payments:intent:${trace || "worker"}] ${message}: ${errorDetails(e)}`);
}

export function paymentsEnabled(): boolean {
    return Boolean(process.env.PAYMENTS_RPC_URL && process.env.PAYMENTS_PROGRAM_ID);
}

function paymentsConnection(): Connection {
    const url = process.env.PAYMENTS_RPC_URL;
    if (!url) throw new Error("PAYMENTS_RPC_URL not set");
    return new Connection(url, "confirmed");
}

function paymentsProgramId(): PublicKey {
    const id = process.env.PAYMENTS_PROGRAM_ID;
    if (!id) throw new Error("PAYMENTS_PROGRAM_ID not set");
    return new PublicKey(id);
}

function paymentsProgram(connection: Connection, signer?: Keypair): anchor.Program {
    const provider = new anchor.AnchorProvider(connection, new anchor.Wallet(signer || Keypair.generate()), { preflightCommitment: "confirmed" });
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

function parseDecimalUnits(value: string | number, decimals: number): bigint {
    const text = typeof value === "number" ? value.toFixed(decimals) : String(value).trim();
    const m = text.match(/^(\d+)(?:\.(\d+))?$/);
    if (!m) throw new Error(`Invalid decimal amount: ${text}`);
    const whole = m[1];
    const fraction = (m[2] || "").slice(0, decimals).padEnd(decimals, "0");
    return BigInt(whole + fraction);
}

function formatUnits(units: bigint, decimals: number): string {
    const base = 10n ** BigInt(decimals);
    const whole = units / base;
    const fraction = (units % base).toString().padStart(decimals, "0").replace(/0+$/, "");
    return fraction ? `${whole}.${fraction}` : whole.toString();
}

function splitTribeAmount(amount: bigint) {
    const cloneCut = (amount * CLONE_BPS) / BPS_DENOMINATOR;
    const treasuryCut = (amount * TREASURY_BPS) / BPS_DENOMINATOR;
    const ownerCut = amount - cloneCut - treasuryCut;
    return { ownerCut, cloneCut, treasuryCut };
}

function paymentDistribution(amount: bigint, decimals: number) {
    const split = splitTribeAmount(amount);
    return {
        ownerCutOrbis: formatUnits(split.ownerCut, decimals),
        cloneCutOrbis: formatUnits(split.cloneCut, decimals),
        treasuryCutOrbis: formatUnits(split.treasuryCut, decimals),
        ownerCutOrbisRaw: split.ownerCut.toString(),
        cloneCutOrbisRaw: split.cloneCut.toString(),
        treasuryCutOrbisRaw: split.treasuryCut.toString(),
    };
}

function distributionFromIntent(intent: any) {
    if (intent.ownerCutOrbis && intent.cloneCutOrbis && intent.treasuryCutOrbis) {
        return {
            ownerCutOrbis: intent.ownerCutOrbis,
            cloneCutOrbis: intent.cloneCutOrbis,
            treasuryCutOrbis: intent.treasuryCutOrbis,
            ownerCutOrbisRaw: intent.ownerCutOrbisRaw ?? null,
            cloneCutOrbisRaw: intent.cloneCutOrbisRaw ?? null,
            treasuryCutOrbisRaw: intent.treasuryCutOrbisRaw ?? null,
        };
    }
    if (!intent.amountOrbis) return null;
    try {
        return paymentDistribution(BigInt(intent.amountOrbis), Number(intent.orbisDecimals ?? 6));
    } catch {
        return null;
    }
}

function solanaCluster(): string {
    const configured = String(process.env.SOLANA_CLUSTER || process.env.CLUSTER || "").trim().toLowerCase();
    if (configured === "mainnet" || configured === "mainnet-beta") return "mainnet";
    if (configured === "devnet" || configured === "testnet") return configured;
    const rpc = String(process.env.PAYMENTS_RPC_URL || "").toLowerCase();
    if (rpc.includes("mainnet")) return "mainnet";
    if (rpc.includes("testnet")) return "testnet";
    if (rpc.includes("devnet")) return "devnet";
    return "mainnet";
}

function solscanUrl(signature: string | null | undefined): string | null {
    if (!signature) return null;
    const cluster = solanaCluster();
    return cluster === "mainnet"
        ? `https://solscan.io/tx/${signature}`
        : `https://solscan.io/tx/${signature}?cluster=${cluster}`;
}

function duplicateMutableAccount(accounts: Record<string, PublicKey>): { first: string; second: string; address: string } | null {
    const seen = new Map<string, string>();
    for (const [name, pubkey] of Object.entries(accounts)) {
        const address = pubkey.toBase58();
        const first = seen.get(address);
        if (first) return { first, second: name, address };
        seen.set(address, name);
    }
    return null;
}

function duplicateMutableAccountMessage(duplicate: { first: string; second: string; address: string }): string {
    return `Invalid payout configuration: ${duplicate.second} matches ${duplicate.first} (${duplicate.address}). Use separate wallets for payment, owner, clone payout, and treasury.`;
}

export function quoteSolToOrbis(amountSol: string | number, decimals: number, orbisPerSolRaw: bigint): { amountLamports: bigint; amountOrbis: bigint; orbisPerSol: number } {
    const amountLamports = parseDecimalUnits(amountSol, 9);
    if (amountLamports <= 0n) throw new Error("amountSol must be greater than 0");
    if (orbisPerSolRaw <= 0n) throw new Error("on-chain SOL to ORBIS rate is not set");
    const amountOrbis = (amountLamports * orbisPerSolRaw + LAMPORTS_PER_SOL - 1n) / LAMPORTS_PER_SOL;
    return { amountLamports, amountOrbis, orbisPerSol: Number(formatUnits(orbisPerSolRaw, decimals)) };
}

let cachedPaymentWallet: Keypair | null = null;

function resolvePaymentWalletPath(walletPath: string): string {
    if (path.isAbsolute(walletPath)) return walletPath;
    const cwdPath = path.resolve(process.cwd(), "wallet", walletPath);
    if (existsSync(cwdPath)) return cwdPath;
    return path.resolve(__dirname, "..", "wallet", walletPath);
}

function getPaymentWallet(trace?: string): Keypair {
    if (cachedPaymentWallet) return cachedPaymentWallet;
    const walletPath = process.env.PAYMENT_WALLET_PATH;
    if (!walletPath) throw new Error("PAYMENT_WALLET_PATH not set");
    const resolved = resolvePaymentWalletPath(walletPath);
    if (!existsSync(resolved)) throw new Error(`Payment wallet file not found at ${resolved}`);
    cachedPaymentWallet = Keypair.fromSecretKey(Uint8Array.from(JSON.parse(readFileSync(resolved, "utf-8"))));
    paymentLog(trace, `payment wallet loaded path=${path.basename(resolved)} pubkey=${cachedPaymentWallet.publicKey.toBase58()}`);
    return cachedPaymentWallet;
}

function legacySolDistributionEnabled(): boolean {
    return process.env.ENABLE_LEGACY_SOL_DISTRIBUTION === "true" && Boolean(process.env.PAYMENT_WALLET_PATH);
}

export interface IntentParams {
    traceId?: string;
    userKey?: string;
    kind: "subscription" | "purchase";
    itemKey?: string;
    quantity?: number;
    payerWallet: string;
    ownerWallet: string;
    priceUsd?: number;
    amountSol?: string | number;
    operatorWallet: string;
    clonePayoutWallet?: string;
}

export interface IntentResult {
    ref: string;
    coin: string;
    amountOrbis: string;
    ownerCutOrbis?: string;
    cloneCutOrbis?: string;
    treasuryCutOrbis?: string;
    rate: number;
    expiresAt: string;
    unsignedTxBase64: string;
    recipients: Record<string, string>;
    error?: string;
    message?: string;
}

export interface SolIntentResult {
    ref: string;
    coin: string;
    amountSol: string;
    amountLamports: string;
    amountOrbis: string;
    ownerCutOrbis?: string;
    cloneCutOrbis?: string;
    treasuryCutOrbis?: string;
    rate: number;
    expiresAt: string;
    unsignedTxBase64: string;
    recipients: Record<string, string>;
    error?: string;
    message?: string;
}

export async function createTribePaymentIntent(db: any, p: IntentParams): Promise<IntentResult> {
    const trace = p.traceId;
    let stage = "start";
    try {
        paymentLog(trace, "ORBIS intent start");
        stage = "connection";
        const connection = paymentsConnection();
        const pid = paymentsProgramId();
        paymentLog(trace, `using rpc=${process.env.PAYMENTS_RPC_URL ? "set" : "missing"} program=${pid.toBase58()}`);
        const program = paymentsProgram(connection);
        stage = "config";
        const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], pid);
        paymentLog(trace, `fetching config pda=${configPda.toBase58()}`);
        const config: any = await (program.account as any).globalConfig.fetch(configPda);
        const mint = new PublicKey(config.orbisMint);
        const treasury = new PublicKey(config.treasury);
        const decimals = (await getMint(connection, mint)).decimals;
        paymentLog(trace, `config ok mint=${mint.toBase58()} treasury=${treasury.toBase58()} decimals=${decimals}`);
        stage = "quote";
        if (p.priceUsd == null) throw new Error("priceUsd is required");
        const amount = quoteUsdToOrbis(p.priceUsd, decimals);
        const rate = Number(process.env.ORBIS_USD_PRICE);
        const distribution = paymentDistribution(amount, decimals);
        paymentLog(trace, `quote ok priceUsd=${p.priceUsd} orbisUsd=${rate} amountOrbis=${formatUnits(amount, decimals)} raw=${amount}`);
        stage = "wallets";
        const payer = new PublicKey(p.payerWallet);
        const owner = new PublicKey(p.ownerWallet);
        const clone = new PublicKey(p.clonePayoutWallet || process.env.CLONE_PAYOUT_WALLET || p.operatorWallet);
        paymentLog(trace, `wallets ok payer=${short(payer.toBase58())} owner=${short(owner.toBase58())} clone=${short(clone.toBase58())}`);
        const payerAta = ata(mint, payer);
        const ownerAta = ata(mint, owner);
        const cloneAta = ata(mint, clone);
        const treasuryAta = ata(mint, treasury);
        const ref = randomBytes(32);
        const refHex = ref.toString("hex");
        const recipients = {
            owner: owner.toBase58(),
            clone: clone.toBase58(),
            treasury: treasury.toBase58(),
            ownerAta: ownerAta.toBase58(),
            cloneAta: cloneAta.toBase58(),
            treasuryAta: treasuryAta.toBase58(),
        };
        const duplicate = duplicateMutableAccount({
            payerTokenAccount: payerAta,
            tribeOwnerTokenAccount: ownerAta,
            cloneTokenAccount: cloneAta,
            treasuryTokenAccount: treasuryAta,
        });
        if (duplicate) {
            const message = duplicateMutableAccountMessage(duplicate);
            paymentWarn(trace, message);
            return {
                ref: refHex,
                coin: "ORBIS",
                amountOrbis: amount.toString(),
                ...distribution,
                rate,
                expiresAt: new Date(Date.now() + QUOTE_TTL_MS).toISOString(),
                unsignedTxBase64: "",
                recipients,
                error: "INVALID_PAYOUT_CONFIGURATION",
                message,
            };
        }
        stage = "payer-balance";
        const payerBalance = await connection.getTokenAccountBalance(payerAta).then((r) => BigInt(r.value.amount)).catch(() => 0n);
        paymentLog(trace, `payer ORBIS balance=${formatUnits(payerBalance, decimals)} raw=${payerBalance}`);
        if (payerBalance < amount) {
            paymentWarn(trace, `insufficient ORBIS ref=${short(refHex)} need=${formatUnits(amount, decimals)} have=${formatUnits(payerBalance, decimals)}`);
            return {
                ref: refHex,
                coin: "ORBIS",
                amountOrbis: amount.toString(),
                ...distribution,
                rate,
                expiresAt: new Date(Date.now() + QUOTE_TTL_MS).toISOString(),
                unsignedTxBase64: "",
                recipients: {},
                error: "INSUFFICIENT_ORBIS",
                message: `No ORBIS balance found in this wallet. You need ${Number(amount) / 10 ** decimals} ORBIS (you have ${Number(payerBalance) / 10 ** decimals}). Add ORBIS to this wallet or pay with SOL instead.`,
            };
        }
        stage = "pay-tribe-instruction";
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
        stage = "transaction";
        const tx = new Transaction().add(
            createAssociatedTokenAccountIdempotentInstruction(payer, ownerAta, owner, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            createAssociatedTokenAccountIdempotentInstruction(payer, cloneAta, clone, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            createAssociatedTokenAccountIdempotentInstruction(payer, treasuryAta, treasury, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            payTribeIx,
        );
        tx.feePayer = payer;
        tx.recentBlockhash = (await connection.getLatestBlockhash("finalized")).blockhash;
        const unsignedTxBase64 = tx.serialize({ requireAllSignatures: false, verifySignatures: false }).toString("base64");
        const now = Date.now();
        const expiresAt = new Date(now + QUOTE_TTL_MS);
        stage = "db-insert";
        await db.collection(INTENTS).insertOne({
            ref: refHex,
            status: "PENDING",
            coin: "ORBIS",
            amountOrbis: amount.toString(),
            orbisDecimals: decimals,
            ...distribution,
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
        paymentLog(trace, `intent saved ref=${short(refHex)} expiresAt=${expiresAt.toISOString()}`);
        return { ref: refHex, coin: "ORBIS", amountOrbis: amount.toString(), ...distribution, rate, expiresAt: expiresAt.toISOString(), unsignedTxBase64, recipients };
    } catch (e: any) {
        paymentError(trace, `ORBIS intent failed stage=${stage}`, e);
        throw e;
    }
}

export async function createSolPaymentIntent(db: any, p: IntentParams): Promise<SolIntentResult> {
    const trace = p.traceId;
    let stage = "start";
    try {
        paymentLog(trace, "SOL intent start");
        stage = "connection";
        const connection = paymentsConnection();
        const pid = paymentsProgramId();
        paymentLog(trace, `using rpc=${process.env.PAYMENTS_RPC_URL ? "set" : "missing"} program=${pid.toBase58()}`);
        const program = paymentsProgram(connection);
        stage = "config";
        const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], pid);
        paymentLog(trace, `fetching config pda=${configPda.toBase58()}`);
        const config: any = await (program.account as any).globalConfig.fetch(configPda);
        const mint = new PublicKey(config.orbisMint);
        const treasury = new PublicKey(config.treasury);
        const decimals = (await getMint(connection, mint)).decimals;
        paymentLog(trace, `config ok mint=${mint.toBase58()} treasury=${treasury.toBase58()} decimals=${decimals}`);
        stage = "sol-payment-config";
        const [solPaymentConfigPda] = PublicKey.findProgramAddressSync([Buffer.from("sol_payment_config")], pid);
        const [reserveVault] = PublicKey.findProgramAddressSync([Buffer.from("sol_orbis_reserve_vault")], pid);
        const solPaymentConfig: any = await (program.account as any).solPaymentConfig.fetch(solPaymentConfigPda);
        const orbisPerSolRaw = BigInt(solPaymentConfig.orbisPerSolRate.toString());
        stage = "wallets";
        const payer = new PublicKey(p.payerWallet);
        const owner = new PublicKey(p.ownerWallet);
        const clone = new PublicKey(p.clonePayoutWallet || process.env.CLONE_PAYOUT_WALLET || p.operatorWallet);
        paymentLog(trace, `wallets ok payer=${short(payer.toBase58())} owner=${short(owner.toBase58())} clone=${short(clone.toBase58())} reserve=${short(reserveVault.toBase58())}`);
        stage = "quote";
        if (p.amountSol == null) throw new Error("amountSol is required");
        const { amountLamports, amountOrbis, orbisPerSol } = quoteSolToOrbis(p.amountSol, decimals, orbisPerSolRaw);
        const distribution = paymentDistribution(amountOrbis, decimals);
        paymentLog(trace, `quote ok amountSol=${formatUnits(amountLamports, 9)} orbisPerSol=${orbisPerSol} amountOrbis=${formatUnits(amountOrbis, decimals)} raw=${amountOrbis}`);
        const ownerAta = ata(mint, owner);
        const cloneAta = ata(mint, clone);
        const treasuryAta = ata(mint, treasury);
        const ref = randomBytes(32);
        const refHex = ref.toString("hex");
        const recipients = {
            reserveVault: reserveVault.toBase58(),
            owner: owner.toBase58(),
            clone: clone.toBase58(),
            treasury: treasury.toBase58(),
            solTreasury: treasury.toBase58(),
            solPaymentConfig: solPaymentConfigPda.toBase58(),
            ownerAta: ownerAta.toBase58(),
            cloneAta: cloneAta.toBase58(),
            treasuryAta: treasuryAta.toBase58(),
        };
        const duplicate = duplicateMutableAccount({
            payerTokenAccount: reserveVault,
            tribeOwnerTokenAccount: ownerAta,
            cloneTokenAccount: cloneAta,
            treasuryTokenAccount: treasuryAta,
        });
        if (duplicate) {
            const message = duplicateMutableAccountMessage(duplicate);
            paymentWarn(trace, message);
            return {
                ref: refHex,
                coin: "SOL",
                amountSol: formatUnits(amountLamports, 9),
                amountLamports: amountLamports.toString(),
                amountOrbis: amountOrbis.toString(),
                ...distribution,
                rate: orbisPerSol,
                expiresAt: new Date(Date.now() + QUOTE_TTL_MS).toISOString(),
                unsignedTxBase64: "",
                recipients,
                error: "INVALID_PAYOUT_CONFIGURATION",
                message,
            };
        }
        stage = "reserve-balance";
        const reserveBalance = await connection.getTokenAccountBalance(reserveVault).then((r) => BigInt(r.value.amount)).catch(() => 0n);
        paymentLog(trace, `reserve ORBIS balance=${formatUnits(reserveBalance, decimals)} raw=${reserveBalance} reserve=${short(reserveVault.toBase58())}`);
        if (reserveBalance < amountOrbis) {
            paymentWarn(trace, `insufficient ORBIS reserve ref=${short(refHex)} need=${formatUnits(amountOrbis, decimals)} have=${formatUnits(reserveBalance, decimals)}`);
            return {
                ref: refHex,
                coin: "SOL",
                amountSol: formatUnits(amountLamports, 9),
                amountLamports: amountLamports.toString(),
                amountOrbis: amountOrbis.toString(),
                ...distribution,
                rate: orbisPerSol,
                expiresAt: new Date(Date.now() + QUOTE_TTL_MS).toISOString(),
                unsignedTxBase64: "",
                recipients,
                error: "INSUFFICIENT_ORBIS_RESERVE",
                message: `ORBIS reserve is not funded enough for this payment. Please contact support with this payment reference.`,
            };
        }
        stage = "payer-balance";
        const payerBalance = BigInt(await connection.getBalance(payer));
        const feeBuffer = 10_000_000n;
        paymentLog(trace, `payer SOL balance=${formatUnits(payerBalance, 9)} need=${formatUnits(amountLamports + feeBuffer, 9)} includingFeeBuffer=${formatUnits(feeBuffer, 9)}`);
        if (payerBalance < amountLamports + feeBuffer) {
            paymentWarn(trace, `insufficient SOL ref=${short(refHex)} need=${formatUnits(amountLamports + feeBuffer, 9)} have=${formatUnits(payerBalance, 9)}`);
            return {
                ref: refHex,
                coin: "SOL",
                amountSol: formatUnits(amountLamports, 9),
                amountLamports: amountLamports.toString(),
                amountOrbis: amountOrbis.toString(),
                ...distribution,
                rate: orbisPerSol,
                expiresAt: new Date(Date.now() + QUOTE_TTL_MS).toISOString(),
                unsignedTxBase64: "",
                recipients: {},
                error: "INSUFFICIENT_SOL",
                message: `Not enough SOL in this wallet. You need about ${formatUnits(amountLamports + feeBuffer, 9)} SOL including fees (you have ${formatUnits(payerBalance, 9)}).`,
            };
        }
        stage = "pay-tribe-sol-instruction";
        const payTribeSolIx = await program.methods
            .payTribeWithSol(new BN(amountLamports.toString()), new BN(amountOrbis.toString()), Array.from(ref))
            .accounts({
                payer,
                solTreasury: treasury,
                solPaymentConfig: solPaymentConfigPda,
                reserveVault,
                tribeOwnerTokenAccount: ownerAta,
                cloneTokenAccount: cloneAta,
                treasuryTokenAccount: treasuryAta,
                config: configPda,
                tokenProgram: TOKEN_PROGRAM_ID,
                systemProgram: SystemProgram.programId,
            } as any)
            .instruction();
        stage = "transaction";
        const tx = new Transaction().add(
            createAssociatedTokenAccountIdempotentInstruction(payer, ownerAta, owner, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            createAssociatedTokenAccountIdempotentInstruction(payer, cloneAta, clone, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            createAssociatedTokenAccountIdempotentInstruction(payer, treasuryAta, treasury, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            payTribeSolIx,
        );
        tx.feePayer = payer;
        tx.recentBlockhash = (await connection.getLatestBlockhash("finalized")).blockhash;
        const unsignedTxBase64 = tx.serialize({ requireAllSignatures: false, verifySignatures: false }).toString("base64");
        const now = Date.now();
        const expiresAt = new Date(now + QUOTE_TTL_MS);
        stage = "db-insert";
        await db.collection(INTENTS).insertOne({
            ref: refHex,
            status: "PENDING",
            coin: "SOL",
            amountLamports: amountLamports.toString(),
            amountSol: formatUnits(amountLamports, 9),
            amountOrbis: amountOrbis.toString(),
            orbisDecimals: decimals,
            ...distribution,
            priceSol: formatUnits(amountLamports, 9),
            orbisPerSol,
            rate: orbisPerSol,
            distributionAttempts: 0,
            userKey: p.userKey ?? null,
            kind: p.kind,
            itemKey: p.itemKey ?? null,
            quantity: p.quantity ?? 1,
            payerWallet: payer.toBase58(),
            recipients,
            createdAt: new Date(now),
            expiresAt,
        });
        paymentLog(trace, `intent saved ref=${short(refHex)} expiresAt=${expiresAt.toISOString()} reserve=${reserveVault.toBase58()}`);
        return {
            ref: refHex,
            coin: "SOL",
            amountSol: formatUnits(amountLamports, 9),
            amountLamports: amountLamports.toString(),
            amountOrbis: amountOrbis.toString(),
            ...distribution,
            rate: orbisPerSol,
            expiresAt: expiresAt.toISOString(),
            unsignedTxBase64,
            recipients,
        };
    } catch (e: any) {
        paymentError(trace, `SOL intent failed stage=${stage}`, e);
        throw e;
    }
}

function extractMemoRef(logLines: string[]): string | null {
    for (const line of logLines) {
        const m = line.match(/Memo \(len \d+\): "([0-9a-f]{64})"/);
        if (m) return m[1];
    }
    return null;
}

function transactionAccountKeys(tx: any): PublicKey[] {
    const message = tx.transaction.message;
    if (typeof message.getAccountKeys === "function") {
        const keys = message.getAccountKeys();
        return [
            ...(keys.staticAccountKeys || []),
            ...(keys.accountKeysFromLookups?.writable || []),
            ...(keys.accountKeysFromLookups?.readonly || []),
        ];
    }
    return message.accountKeys || [];
}

function isExpectedSolPayment(intent: any, tx: any): boolean {
    if (!tx.meta) return false;
    const keys = transactionAccountKeys(tx);
    const payer = String(intent.payerWallet || "");
    const payment = String(intent.recipients?.payment || "");
    const amount = BigInt(intent.amountLamports || "0");
    const payerIndex = keys.findIndex((key: PublicKey) => key.toBase58() === payer);
    const paymentIndex = keys.findIndex((key: PublicKey) => key.toBase58() === payment);
    if (payerIndex < 0 || paymentIndex < 0 || amount <= 0n) return false;
    const payerDelta = BigInt(tx.meta.preBalances[payerIndex]) - BigInt(tx.meta.postBalances[payerIndex]);
    const paymentDelta = BigInt(tx.meta.postBalances[paymentIndex]) - BigInt(tx.meta.preBalances[paymentIndex]);
    return payerDelta >= amount && paymentDelta >= amount;
}

async function processSolEvent(db: any, refHex: string, signature: string, connection: Connection) {
    paymentLog(refHex.slice(0, 8), `SOL log detected tx=${short(signature)} ref=${short(refHex)}`);
    const intent = await db.collection(INTENTS).findOne({ ref: refHex });
    if (!intent || intent.coin !== "SOL") {
        paymentWarn(refHex.slice(0, 8), `SOL log ignored no matching SOL intent tx=${short(signature)}`);
        return;
    }
    if (intent.status === "PAID" || intent.status === "FAILED") {
        paymentLog(refHex.slice(0, 8), `SOL log ignored terminal status=${intent.status} tx=${short(signature)}`);
        return;
    }
    const tx = await connection.getTransaction(signature, { commitment: "confirmed", maxSupportedTransactionVersion: 0 });
    if (!tx) {
        paymentWarn(refHex.slice(0, 8), `SOL tx not available yet tx=${short(signature)}`);
        return;
    }
    if (tx.meta?.err) {
        paymentWarn(refHex.slice(0, 8), `SOL tx has onchain error tx=${short(signature)} error=${JSON.stringify(tx.meta.err)}`);
        return;
    }
    if (!isExpectedSolPayment(intent, tx)) {
        paymentWarn(refHex.slice(0, 8), `SOL tx did not match expected payer/payment/amount tx=${short(signature)} payer=${short(intent.payerWallet)} payment=${short(intent.recipients?.payment)} amountLamports=${intent.amountLamports}`);
        return;
    }
    const upd = await db.collection(INTENTS).updateOne(
        { ref: refHex, status: "PENDING" },
        { $set: { status: "SOL_RECEIVED", solTxSignature: signature, solReceivedAt: new Date(), nextDistributionAttemptAt: new Date() } },
    );
    if (upd.modifiedCount > 0) log.info(`[payments] SOL received ref=${refHex.slice(0, 8)} tx=${signature.slice(0, 8)}`);
    await distributeSolIntent(db, refHex, connection);
}

export async function getTribePaymentStatus(db: any, ref: string) {
    const intent = await db.collection(INTENTS).findOne({ ref });
    if (!intent) return null;
    const signature = intent.txSignature ?? intent.distributionTxSignature ?? intent.solTxSignature ?? null;
    const distribution = distributionFromIntent(intent);
    return {
        ref,
        status: intent.status,
        coin: intent.coin ?? "ORBIS",
        txSignature: intent.txSignature ?? null,
        solTxSignature: intent.solTxSignature ?? null,
        distributionTxSignature: intent.distributionTxSignature ?? null,
        solscanUrl: solscanUrl(signature),
        solanaCluster: solanaCluster(),
        amountOrbis: intent.amountOrbis ?? null,
        amountSol: intent.amountSol ?? null,
        amountLamports: intent.amountLamports ?? null,
        ownerCutOrbis: distribution?.ownerCutOrbis ?? null,
        cloneCutOrbis: distribution?.cloneCutOrbis ?? null,
        treasuryCutOrbis: distribution?.treasuryCutOrbis ?? null,
        ownerCutOrbisRaw: distribution?.ownerCutOrbisRaw ?? null,
        cloneCutOrbisRaw: distribution?.cloneCutOrbisRaw ?? null,
        treasuryCutOrbisRaw: distribution?.treasuryCutOrbisRaw ?? null,
        attempts: intent.distributionAttempts ?? 0,
        message: intent.status === "FAILED" ? "Payment received, but ORBIS distribution failed. Please contact support with this payment reference." : null,
    };
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
    try {
        buf = Buffer.from(base64, "base64");
    } catch {
        return null;
    }
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
    if (!intent || intent.status === "PAID") return;
    if (BigInt(intent.amountOrbis) !== ev.amount) {
        log.warn(`[payments] amount mismatch ref=${ev.refHex.slice(0, 8)} onchain=${ev.amount} quoted=${intent.amountOrbis}`);
        return;
    }
    const r = intent.recipients || {};
    if (r.ownerAta !== ev.ownerAta.toBase58() || r.cloneAta !== ev.cloneAta.toBase58() || r.treasuryAta !== ev.treasuryAta.toBase58()) {
        log.warn(`[payments] recipient mismatch ref=${ev.refHex.slice(0, 8)}`);
        return;
    }
    await markPaidAndConfirmJava(db, ev.refHex, signature, intent.coin === "SOL" ? { distributionTxSignature: signature, distributedAt: new Date() } : {});
}

async function distributeSolIntent(db: any, refHex: string, connection: Connection) {
    const now = new Date();
    const claimResult: any = await db.collection(INTENTS).findOneAndUpdate(
        {
            ref: refHex,
            coin: "SOL",
            status: { $in: ["SOL_RECEIVED", "DISTRIBUTION_RETRY", "DISTRIBUTING"] },
            distributionAttempts: { $lt: MAX_DISTRIBUTION_ATTEMPTS },
            $or: [
                { nextDistributionAttemptAt: { $exists: false } },
                { nextDistributionAttemptAt: { $lte: now } },
                { distributionStartedAt: { $lt: new Date(Date.now() - 120_000) } },
            ],
        },
        {
            $set: { status: "DISTRIBUTING", distributionStartedAt: now },
            $inc: { distributionAttempts: 1 },
        },
        { returnDocument: "after" },
    );
    const intent = claimResult && "value" in claimResult ? claimResult.value : claimResult;
    if (!intent) {
        paymentWarn(refHex.slice(0, 8), "distribution skipped no eligible intent or retry window not reached");
        return;
    }
    try {
        paymentLog(refHex.slice(0, 8), `distribution claimed attempt=${intent.distributionAttempts} amountOrbis=${intent.amountOrbis} owner=${short(intent.recipients?.owner)} clone=${short(intent.recipients?.clone)}`);
        const signature = await sendReservePayTribe(intent, connection);
        paymentLog(refHex.slice(0, 8), `distribution sent tx=${short(signature)}`);
        await markPaidAndConfirmJava(db, intent.ref, signature, { distributionTxSignature: signature, distributedAt: new Date() });
    } catch (e: any) {
        paymentError(refHex.slice(0, 8), "distribution attempt failed", e);
        await markDistributionFailure(db, intent, e);
    }
}

async function sendReservePayTribe(intent: any, connection: Connection): Promise<string> {
    const trace = String(intent.ref || "").slice(0, 8);
    let stage = "start";
    try {
        const paymentWallet = getPaymentWallet(trace);
        const program = paymentsProgram(connection, paymentWallet);
        const pid = paymentsProgramId();
        const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], pid);
        stage = "config";
        paymentLog(trace, `distribution fetching config program=${pid.toBase58()} pda=${configPda.toBase58()}`);
        const config: any = await (program.account as any).globalConfig.fetch(configPda);
        const mint = new PublicKey(config.orbisMint);
        const treasury = new PublicKey(config.treasury);
        const payment = paymentWallet.publicKey;
        const owner = new PublicKey(intent.recipients.owner);
        const clone = new PublicKey(intent.recipients.clone);
        const amount = BigInt(intent.amountOrbis);
        const paymentAta = ata(mint, payment);
        const ownerAta = ata(mint, owner);
        const cloneAta = ata(mint, clone);
        const treasuryAta = ata(mint, treasury);
        const duplicate = duplicateMutableAccount({
            payerTokenAccount: paymentAta,
            tribeOwnerTokenAccount: ownerAta,
            cloneTokenAccount: cloneAta,
            treasuryTokenAccount: treasuryAta,
        });
        if (duplicate) throw new Error(duplicateMutableAccountMessage(duplicate));
        stage = "reserve-balance";
        const reserveBalance = await connection.getTokenAccountBalance(paymentAta).then((r) => BigInt(r.value.amount)).catch(() => 0n);
        paymentLog(trace, `distribution reserve balance=${reserveBalance} required=${amount} paymentAta=${short(paymentAta.toBase58())}`);
        if (reserveBalance < amount) throw new Error("insufficient ORBIS reserve");
        stage = "instruction";
        const ref = Buffer.from(intent.ref, "hex");
        const payTribeIx = await program.methods
            .payTribe(new BN(amount.toString()), Array.from(ref))
            .accounts({
                payer: payment,
                payerTokenAccount: paymentAta,
                tribeOwnerTokenAccount: ownerAta,
                cloneTokenAccount: cloneAta,
                treasuryTokenAccount: treasuryAta,
                config: configPda,
                tokenProgram: TOKEN_PROGRAM_ID,
            } as any)
            .instruction();
        stage = "transaction";
        const latest = await connection.getLatestBlockhash("finalized");
        const tx = new Transaction().add(
            createAssociatedTokenAccountIdempotentInstruction(payment, ownerAta, owner, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            createAssociatedTokenAccountIdempotentInstruction(payment, cloneAta, clone, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            createAssociatedTokenAccountIdempotentInstruction(payment, treasuryAta, treasury, mint, TOKEN_PROGRAM_ID, ASSOCIATED_TOKEN_PROGRAM_ID),
            payTribeIx,
        );
        tx.feePayer = payment;
        tx.recentBlockhash = latest.blockhash;
        tx.sign(paymentWallet);
        stage = "send";
        paymentLog(trace, `distribution sending pay_tribe payer=${short(payment.toBase58())} amount=${amount}`);
        const signature = await connection.sendRawTransaction(tx.serialize(), { preflightCommitment: "confirmed" });
        stage = "confirm";
        const confirmation = await connection.confirmTransaction({ signature, blockhash: latest.blockhash, lastValidBlockHeight: latest.lastValidBlockHeight }, "confirmed");
        if (confirmation.value.err) throw new Error(typeof confirmation.value.err === "string" ? confirmation.value.err : JSON.stringify(confirmation.value.err));
        paymentLog(trace, `distribution confirmed tx=${short(signature)}`);
        return signature;
    } catch (e: any) {
        paymentError(trace, `distribution sendReservePayTribe failed stage=${stage}`, e);
        throw e;
    }
}

async function markPaidAndConfirmJava(db: any, ref: string, signature: string, extra: Record<string, any> = {}) {
    const intent = await db.collection(INTENTS).findOne({ ref });
    if (!intent || intent.status === "PAID") return;
    const upd = await db.collection(INTENTS).updateOne(
        { ref, status: { $ne: "PAID" } },
        { $set: { status: "PAID", txSignature: signature, paidAt: new Date(), ...extra } },
    );
    if (upd.modifiedCount === 0) return;
    log.info(`[payments] PAID ref=${ref.slice(0, 8)} tx=${signature.slice(0, 8)}`);
    paymentLog(ref.slice(0, 8), `marked PAID tx=${short(signature)}`);
    await confirmToJava(db, { ...intent, status: "PAID" }, signature);
}

async function markDistributionFailure(db: any, intent: any, e: any) {
    const attempts = Number(intent.distributionAttempts || 1);
    const terminal = attempts >= MAX_DISTRIBUTION_ATTEMPTS;
    const reason = e?.message || String(e);
    await db.collection(INTENTS).updateOne(
        { ref: intent.ref, status: "DISTRIBUTING" },
        {
            $set: {
                status: terminal ? "FAILED" : "DISTRIBUTION_RETRY",
                distributionError: reason,
                nextDistributionAttemptAt: terminal ? null : new Date(Date.now() + attempts * 30_000),
                failedAt: terminal ? new Date() : null,
            },
        },
    );
    log.warn(`[payments] ORBIS distribution ${terminal ? "failed" : "retry"} ref=${intent.ref.slice(0, 8)} attempt=${attempts}: ${reason}`);
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
        paymentLog(intent.ref.slice(0, 8), "Java confirmation ok");
    } catch (e: any) {
        const reason = e.response?.status ?? e.message;
        log.warn(`[payments] Java confirm failed ref=${intent.ref.slice(0, 8)}: ${reason}`);
        paymentWarn(intent.ref.slice(0, 8), `Java confirmation failed reason=${reason}`);
        await db.collection(INTENTS).updateOne({ ref: intent.ref }, { $set: { confirmedToJava: false, javaConfirmError: String(reason) } });
    }
}

export function startTribePaymentsListener(db: any) {
    if (!paymentsEnabled()) {
        log.warn("[payments] PAYMENTS_RPC_URL / PAYMENTS_PROGRAM_ID not set");
        return;
    }
    const connection = paymentsConnection();
    const pid = paymentsProgramId();
    log.info(`[payments] listening for TribePaymentMade on ${solanaCluster()} program ${pid.toBase58()}`);
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
    if (legacySolDistributionEnabled()) {
        (async () => {
            try {
                const paymentWallet = getPaymentWallet();
                const payment = paymentWallet.publicKey;
                log.info(`[payments] listening for legacy SOL payments on ${payment.toBase58()}`);
                connection.onLogs(payment, async (logs) => {
                    try {
                        const ref = extractMemoRef(logs.logs);
                        if (ref) await processSolEvent(db, ref, logs.signature, connection);
                    } catch (e: any) {
                        log.warn(`[payments] SOL listener error: ${e.message}`);
                    }
                }, "confirmed");
                const solSeen = new Set<string>();
                setInterval(async () => {
                    try {
                        const sigs = await connection.getSignaturesForAddress(payment, { limit: 25 });
                        for (const s of sigs) {
                            if (solSeen.has(s.signature)) continue;
                            solSeen.add(s.signature);
                            const tx = await connection.getTransaction(s.signature, { commitment: "confirmed", maxSupportedTransactionVersion: 0 });
                            const ref = extractMemoRef(tx?.meta?.logMessages ?? []);
                            if (ref) await processSolEvent(db, ref, s.signature, connection);
                        }
                        if (solSeen.size > 1000) solSeen.clear();
                    } catch (e: any) {
                        log.warn(`[payments] SOL reconcile error: ${e.message}`);
                    }
                }, 30_000);
            } catch (e: any) {
                log.warn(`[payments] SOL listener init failed: ${e.message}`);
            }
        })();
    }
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
    if (legacySolDistributionEnabled()) {
        setInterval(async () => {
            try {
                const now = new Date();
                const intents = await db.collection(INTENTS).find({
                    coin: "SOL",
                    status: { $in: ["SOL_RECEIVED", "DISTRIBUTION_RETRY", "DISTRIBUTING"] },
                    distributionAttempts: { $lt: MAX_DISTRIBUTION_ATTEMPTS },
                    $or: [
                        { nextDistributionAttemptAt: { $exists: false } },
                        { nextDistributionAttemptAt: { $lte: now } },
                        { distributionStartedAt: { $lt: new Date(Date.now() - 120_000) } },
                    ],
                }).limit(10).toArray();
                for (const intent of intents) await distributeSolIntent(db, intent.ref, connection);
            } catch (e: any) {
                log.warn(`[payments] distribution worker error: ${e.message}`);
            }
        }, 10_000);
    }
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
