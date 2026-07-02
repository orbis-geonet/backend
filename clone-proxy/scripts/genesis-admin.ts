import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey } from "@solana/web3.js";
import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";
import * as dotenv from "dotenv";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "../.env") });

async function main() {
    const action = process.argv[2];
    const targetStr = process.argv[3];

    if (!action || !targetStr || !["grant", "revoke"].includes(action)) {
        console.error("Usage: tsx genesis-admin.ts <grant|revoke> <target-pubkey> [--wallet <path>]");
        process.exit(1);
    }

    const walletIdx = process.argv.indexOf("--wallet");
    const walletPath = walletIdx !== -1
        ? process.argv[walletIdx + 1]
        : path.join(__dirname, "../wallet/wallet.json");

    const secretKey = Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8")));
    const admin = Keypair.fromSecretKey(secretKey);

    const connection = new Connection(process.env.RPC_URL!, "confirmed");
    const wallet = new anchor.Wallet(admin);
    const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });

    const programId = new PublicKey(process.env.PROGRAM_ID!);
    const idl = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../programs/idl/orbis_protocol.json"), "utf-8"));
    const program = new anchor.Program(idl as any, anchorProvider);

    const targetOwner = new PublicKey(targetStr);
    const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], programId);
    const [cloneInfoPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("clone_info"), targetOwner.toBuffer()],
        programId
    );

    let cloneInfo: any;
    try {
        cloneInfo = await program.account.cloneInfo.fetch(cloneInfoPda);
    } catch {
        console.error(`No CloneInfo PDA found for ${targetStr}. Is this clone registered?`);
        process.exit(1);
    }

    console.log(`Target clone: ${targetStr}`);
    console.log(`  base_url:    ${cloneInfo.baseUrl}`);
    console.log(`  trust_score: ${cloneInfo.trustScore}`);
    console.log(`  is_genesis:  ${cloneInfo.isGenesis}`);

    if (action === "grant" && cloneInfo.isGenesis) {
        console.log("Already a genesis provider. Nothing to do.");
        return;
    }
    if (action === "revoke" && !cloneInfo.isGenesis) {
        console.log("Not a genesis provider. Nothing to do.");
        return;
    }

    const method = action === "grant"
        ? program.methods.grantGenesis()
        : program.methods.revokeGenesis();

    const tx = await method
        .accounts({
            cloneInfo: cloneInfoPda,
            targetOwner,
            config: configPda,
            admin: admin.publicKey,
        } as any)
        .rpc();

    console.log(`\n${action === "grant" ? "Genesis granted" : "Genesis revoked"} for ${targetStr}`);
    console.log(`Transaction: ${tx}`);
    console.log(`https://explorer.solana.com/tx/${tx}?cluster=devnet`);
}

main().catch(err => {
    console.error("Failed:", err.message || err);
    process.exit(1);
});
