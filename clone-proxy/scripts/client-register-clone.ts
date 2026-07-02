import * as anchor from "@coral-xyz/anchor";
import { Program } from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey, SystemProgram, SYSVAR_RENT_PUBKEY } from "@solana/web3.js";
import {
    TOKEN_PROGRAM_ID,
    getOrCreateAssociatedTokenAccount
} from "@solana/spl-token";
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import * as dotenv from "dotenv";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "../.env") });

async function main() {
    const walletIdx = process.argv.indexOf("--wallet");
    const walletPath = walletIdx !== -1
        ? process.argv[walletIdx + 1]
        : path.join(__dirname, "../wallet/wallet4.json");

    if (!fs.existsSync(walletPath)) {
        console.error(`Wallet file not found at ${walletPath}`);
        process.exit(1);
    }

    const secretKey = Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8")));
    const payer = Keypair.fromSecretKey(secretKey);

    const connection = new Connection(process.env.RPC_URL!, "confirmed");
    const wallet = new anchor.Wallet(payer);
    const provider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
    anchor.setProvider(provider);

    const idl = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../programs/idl/orbis_protocol.json"), "utf-8"));
    const program = new anchor.Program(idl as any, provider);

    const BASE_URL = process.env.CLONE_BASE_URL || "http://localhost:3000";
    const ORBIS_MINT = new PublicKey(process.env.ORBIS_MINT || "GkEwz1aZTTmJ4Xao3WfA3UEhBhPGrfZQ6VSMQ3xcQzfp");

    const [configPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("global_config")],
        program.programId
    );

    const [cloneInfoPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("clone_info"), payer.publicKey.toBuffer()],
        program.programId
    );

    const [cloneVaultPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("clone_vault"), cloneInfoPda.toBuffer()],
        program.programId
    );

    const userAta = await getOrCreateAssociatedTokenAccount(
        connection,
        payer,
        ORBIS_MINT,
        payer.publicKey
    );

    const config: any = await program.account.globalConfig.fetch(configPda);
    const treasuryAta = await getOrCreateAssociatedTokenAccount(
        connection,
        payer,
        config.orbisMint,
        config.treasury
    );

    console.log("Registering Clone for:", payer.publicKey.toBase58());
    console.log("Base URL:", BASE_URL);

    try {
        const tx = await program.methods
            .registerClone(BASE_URL)
            .accounts({
                cloneInfo: cloneInfoPda,
                cloneVault: cloneVaultPda,
                signerTokenAccount: userAta.address,
                treasuryTokenAccount: treasuryAta.address,
                config: configPda,
                signer: payer.publicKey,
                tokenProgram: TOKEN_PROGRAM_ID,
                systemProgram: SystemProgram.programId,
                rent: SYSVAR_RENT_PUBKEY,
            } as any)
            .rpc();

        console.log("✅ Success! Clone Registered:", tx);
    } catch (err) {
        console.error("❌ Registration failed:", err);
    }
}

main().catch(console.error);
