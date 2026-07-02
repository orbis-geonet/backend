import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey, SystemProgram } from "@solana/web3.js";
import { getMint } from "@solana/spl-token";
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import * as dotenv from "dotenv";
import { BN } from "bn.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "../.env") });

async function main() {
    const rpcUrl = process.env.RPC_URL;
    if (!rpcUrl) throw new Error("RPC_URL not set in .env");

    const programId = new PublicKey(process.env.PROGRAM_ID!);
    const orbisMint = new PublicKey(process.env.ORBIS_MINT!);
    const merkleTree = new PublicKey(process.env.MERKLE_TREE_MINT!);

    const walletPath = path.join(__dirname, "../wallet/wallet.json");

    if (!fs.existsSync(walletPath)) {
        console.error(`Wallet not found: ${walletPath}`);
        process.exit(1);
    }

    const treasuryArg = process.argv.find((a, i) => process.argv[i - 1] === "--treasury");
    const secretKey = Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8")));
    const admin = Keypair.fromSecretKey(secretKey);

    const treasury = treasuryArg ? new PublicKey(treasuryArg) : admin.publicKey;

    const connection = new Connection(rpcUrl, "confirmed");

    console.log("=== Orbis Protocol Initializer ===");
    console.log(`Program ID   : ${programId.toBase58()}`);
    console.log(`Admin        : ${admin.publicKey.toBase58()}`);
    console.log(`Treasury     : ${treasury.toBase58()}`);
    console.log(`ORBIS Mint   : ${orbisMint.toBase58()}`);
    console.log(`Merkle Tree  : ${merkleTree.toBase58()}`);

    const mintInfo = await getMint(connection, orbisMint);
    const decimals = mintInfo.decimals;
    const unit = 10 ** decimals;
    console.log(`ORBIS Decimals: ${decimals} (1 ORBIS = ${unit} raw units)`);

    // Fee schedule (all amounts in raw token units)
    const writeFee        = new BN(Math.round(0.0001 * unit));   // 0.0001 ORBIS per blockchain write
    const feePerMb        = new BN(Math.round(0.0001 * unit));   // 0.0001 ORBIS per MB
    const minFee          = new BN(Math.round(0.0001 * unit));   // 0.0001 ORBIS minimum
    const registrationFee = new BN(Math.round(50 * unit));       // 50 ORBIS one-time registration

    console.log("\nFee schedule:");
    console.log(`  write_fee        : ${writeFee.toString()} (0.0001 ORBIS)`);
    console.log(`  fee_per_mb       : ${feePerMb.toString()} (0.0001 ORBIS/MB)`);
    console.log(`  min_fee          : ${minFee.toString()} (0.0001 ORBIS)`);
    console.log(`  registration_fee : ${registrationFee.toString()} (50 ORBIS)`);

    const wallet = new anchor.Wallet(admin);
    const provider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });

    const idl = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../programs/idl/orbis_protocol.json"), "utf-8"));

    const program = new anchor.Program(idl as any, provider);

    const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], programId);
    console.log(`\nConfig PDA   : ${configPda.toBase58()}`);

    // Check if already initialized
    try {
        const existing: any = await program.account.globalConfig.fetch(configPda);
        console.log("\n⚠️  Config account already exists:");
        console.log(`  admin            : ${existing.admin.toBase58()}`);
        console.log(`  treasury         : ${existing.treasury.toBase58()}`);
        console.log(`  write_fee        : ${existing.writeFee.toString()}`);
        console.log(`  fee_per_mb       : ${existing.feePerMb.toString()}`);
        console.log(`  min_fee          : ${existing.minFee.toString()}`);
        console.log(`  registration_fee : ${existing.registrationFee.toString()}`);
        console.log("\nUse `update-config-fees` script to change fees, or deploy with a new program ID to re-initialize.");
        process.exit(0);
    } catch {
        // Account doesn't exist — proceed with initialization
    }

    console.log("\nInitializing program...");

    const tx = await program.methods
        .initialize(
            treasury,
            orbisMint,
            writeFee,
            feePerMb,
            minFee,
            registrationFee,
        )
        .accounts({
            config: configPda,
            merkleTree: merkleTree,
            admin: admin.publicKey,
            systemProgram: SystemProgram.programId,
        } as any)
        .rpc();

    console.log(`\n✅ Program initialized!`);
    console.log(`   TX: ${tx}`);

    // Verify
    const config: any = await program.account.globalConfig.fetch(configPda);
    console.log("\nOn-chain config:");
    console.log(`  admin            : ${config.admin.toBase58()}`);
    console.log(`  treasury         : ${config.treasury.toBase58()}`);
    console.log(`  merkle_tree      : ${config.merkleTree.toBase58()}`);
    console.log(`  write_fee        : ${config.writeFee.toString()}`);
    console.log(`  fee_per_mb       : ${config.feePerMb.toString()}`);
    console.log(`  min_fee          : ${config.minFee.toString()}`);
    console.log(`  registration_fee : ${config.registrationFee.toString()}`);
}

main().catch((err) => {
    console.error("❌ Failed:", err.message ?? err);
    process.exit(1);
});
