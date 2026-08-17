import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey, SystemProgram, SYSVAR_RENT_PUBKEY } from "@solana/web3.js";
import { TOKEN_PROGRAM_ID, getMint, getOrCreateAssociatedTokenAccount, transfer } from "@solana/spl-token";
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import * as dotenv from "dotenv";
import { BN } from "bn.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = path.resolve(__dirname, "..");
dotenv.config({ path: path.resolve(root, ".env"), quiet: true });

function readArg(name: string): string | undefined {
    const index = process.argv.indexOf(name);
    if (index === -1) return undefined;
    const value = process.argv[index + 1];
    return value && !value.startsWith("--") ? value : undefined;
}

function resolveWalletPath(walletArg?: string): string {
    const selected = walletArg || process.env.WALLET_PATH || "wallet.json";
    if (path.isAbsolute(selected)) return selected;
    const candidates = [
        path.resolve(process.cwd(), selected),
        path.resolve(process.cwd(), "wallet", selected),
        path.resolve(root, selected),
        path.resolve(root, "wallet", selected),
    ];
    const found = candidates.find((candidate) => fs.existsSync(candidate));
    return found || candidates[candidates.length - 1];
}

function parseDecimalUnits(value: string, decimals: number): string {
    const text = value.trim();
    const match = text.match(/^(\d+)(?:\.(\d+))?$/);
    if (!match) throw new Error(`Invalid decimal amount: ${value}`);
    const fraction = (match[2] || "").slice(0, decimals).padEnd(decimals, "0");
    return `${match[1]}${fraction}`;
}

async function main() {
    const command = process.argv[2] || "show";
    if (!["show", "init", "update", "fund"].includes(command)) throw new Error("Usage: sol-payment-config.ts <show|init|update|fund> --rate 12.5 --amount 1000 --wallet wallet.json");

    const rpcUrl = process.env.PAYMENTS_RPC_URL || process.env.RPC_URL;
    if (!rpcUrl) throw new Error("PAYMENTS_RPC_URL or RPC_URL not set");
    const programId = new PublicKey(process.env.PAYMENTS_PROGRAM_ID || process.env.PROGRAM_ID || "");
    const walletPath = resolveWalletPath(readArg("--wallet"));
    if (!fs.existsSync(walletPath)) throw new Error(`Wallet file not found at ${walletPath}`);

    const keypair = Keypair.fromSecretKey(Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8"))));
    const connection = new Connection(rpcUrl, "confirmed");
    const provider = new anchor.AnchorProvider(connection, new anchor.Wallet(keypair), { preflightCommitment: "confirmed" });
    const idl = JSON.parse(fs.readFileSync(path.resolve(root, "programs/idl/orbis_protocol.json"), "utf-8"));
    const program = new anchor.Program(idl as any, provider);
    const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], programId);
    const [solPaymentConfigPda] = PublicKey.findProgramAddressSync([Buffer.from("sol_payment_config")], programId);
    const [reserveVault] = PublicKey.findProgramAddressSync([Buffer.from("sol_orbis_reserve_vault")], programId);
    const config: any = await (program.account as any).globalConfig.fetch(configPda);
    const mint = new PublicKey(config.orbisMint);
    const decimals = (await getMint(connection, mint)).decimals;

    if (command === "show") {
        const solConfig: any = await (program.account as any).solPaymentConfig.fetch(solPaymentConfigPda);
        const reserveBalance = await connection.getTokenAccountBalance(reserveVault).catch(() => null);
        console.log(`Program              : ${programId.toBase58()}`);
        console.log(`Admin                : ${config.admin.toBase58()}`);
        console.log(`Treasury             : ${config.treasury.toBase58()}`);
        console.log(`ORBIS mint           : ${mint.toBase58()}`);
        console.log(`SOL payment config   : ${solPaymentConfigPda.toBase58()}`);
        console.log(`ORBIS reserve vault  : ${reserveVault.toBase58()}`);
        console.log(`Reserve balance      : ${reserveBalance?.value.uiAmountString || "0"}`);
        console.log(`ORBIS per SOL raw    : ${solConfig.orbisPerSolRate.toString()}`);
        return;
    }

    if (command === "fund") {
        const amountArg = readArg("--amount");
        if (!amountArg) throw new Error("--amount is required");
        const amountRaw = parseDecimalUnits(amountArg, decimals);
        const sourceAta = await getOrCreateAssociatedTokenAccount(connection, keypair, mint, keypair.publicKey);
        const tx = await transfer(connection, keypair, sourceAta.address, reserveVault, keypair, BigInt(amountRaw));
        console.log(`Funded ORBIS reserve vault: ${tx}`);
        console.log(`ORBIS reserve vault  : ${reserveVault.toBase58()}`);
        console.log(`Amount raw           : ${amountRaw}`);
        return;
    }

    const rateArg = readArg("--rate");
    if (!rateArg) throw new Error("--rate is required");
    const rateRaw = parseDecimalUnits(rateArg, decimals);

    if (command === "init") {
        const tx = await program.methods
            .initSolPaymentConfig(new BN(rateRaw))
            .accounts({
                solPaymentConfig: solPaymentConfigPda,
                reserveVault,
                orbisMint: mint,
                config: configPda,
                admin: keypair.publicKey,
                tokenProgram: TOKEN_PROGRAM_ID,
                systemProgram: SystemProgram.programId,
                rent: SYSVAR_RENT_PUBKEY,
            } as any)
            .rpc();
        console.log(`Initialized SOL payment config: ${tx}`);
    } else {
        const tx = await program.methods
            .updateSolOrbisRate(new BN(rateRaw))
            .accounts({
                solPaymentConfig: solPaymentConfigPda,
                config: configPda,
                admin: keypair.publicKey,
            } as any)
            .rpc();
        console.log(`Updated SOL payment rate: ${tx}`);
    }

    console.log(`SOL payment config   : ${solPaymentConfigPda.toBase58()}`);
    console.log(`ORBIS reserve vault  : ${reserveVault.toBase58()}`);
    console.log(`ORBIS per SOL raw    : ${rateRaw}`);
}

main().catch((err) => {
    console.error(err.stack || err.message || String(err));
    process.exit(1);
});
