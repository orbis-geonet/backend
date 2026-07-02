import { Connection, Keypair, PublicKey, LAMPORTS_PER_SOL } from "@solana/web3.js";
import { getAccount, getAssociatedTokenAddressSync } from "@solana/spl-token";
import * as anchor from "@coral-xyz/anchor";
import { TOKEN_PROGRAM_ID, getOrCreateAssociatedTokenAccount } from "@solana/spl-token";
import { SystemProgram, SYSVAR_RENT_PUBKEY } from "@solana/web3.js";
import { PROGRAM_ID, ORBIS_MINT, CLONE_BASE_URL } from "./config.js";
import { log } from "./logger.js";
import { readFileSync } from "node:fs";

export async function runStartupChecks(
    connection: Connection,
    payer: Keypair,
    shouldRegister: boolean
): Promise<void> {
    const pubkey = payer.publicKey;

    log.info("--- Startup Checks ---");
    log.info(`Wallet: ${pubkey.toBase58()}`);

    const lamports = await connection.getBalance(pubkey);
    const solBalance = lamports / LAMPORTS_PER_SOL;
    log.info(`SOL Balance: ${solBalance.toFixed(4)} SOL`);

    if (solBalance < 0.01) {
        log.error(`Insufficient SOL balance (${solBalance.toFixed(4)} SOL). A minimum of 0.01 SOL is required to operate.`);
        process.exit(1);
    }

    let orbisBalance = 0;
    try {
        const ata = getAssociatedTokenAddressSync(ORBIS_MINT, pubkey);
        const tokenAccount = await getAccount(connection, ata);
        orbisBalance = Number(tokenAccount.amount);
        log.info(`ORBIS Token Balance: ${orbisBalance}`);
    } catch {
        log.error(`No ORBIS token account found for this wallet. You need ORBIS tokens to operate the clone proxy.`);
        process.exit(1);
    }

    if (orbisBalance === 0) {
        log.error(`ORBIS token balance is 0. You need ORBIS tokens to pay for data streaming fees.`);
        process.exit(1);
    }

    const [cloneInfoPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("clone_info"), pubkey.toBuffer()],
        PROGRAM_ID
    );

    const cloneInfoAccount = await connection.getAccountInfo(cloneInfoPda);
    const isRegistered = cloneInfoAccount !== null;

    log.info(`Registration Status: ${isRegistered ? "Registered" : "Not registered"}`);

    if (!isRegistered && !shouldRegister) {
        log.error(`This wallet is not registered as a clone node on-chain.`);
        log.error(`Run the proxy with the --register flag to register: npx tsx scripts/clone-proxy/index.ts ... --register`);
        process.exit(1);
    }

    if (!isRegistered && shouldRegister) {
        log.info(`--register flag detected. Attempting registration...`);
        await registerClone(connection, payer);
    }

    log.info("--- Startup Checks Passed ---");
}

async function registerClone(connection: Connection, payer: Keypair): Promise<void> {
    try {
        const wallet = new anchor.Wallet(payer);
        const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
        anchor.setProvider(anchorProvider);
        const idl = JSON.parse(readFileSync(new URL("./programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));
        const program = new anchor.Program(idl as any, anchorProvider);

        const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], PROGRAM_ID);
        const [cloneInfoPda] = PublicKey.findProgramAddressSync(
            [Buffer.from("clone_info"), payer.publicKey.toBuffer()],
            PROGRAM_ID
        );
        const [cloneVaultPda] = PublicKey.findProgramAddressSync(
            [Buffer.from("clone_vault"), cloneInfoPda.toBuffer()],
            PROGRAM_ID
        );

        const config: any = await program.account.globalConfig.fetch(configPda);

        const userAta = await getOrCreateAssociatedTokenAccount(
            connection,
            payer,
            ORBIS_MINT,
            payer.publicKey
        );

        const treasuryAta = await getOrCreateAssociatedTokenAccount(
            connection,
            payer,
            config.orbisMint,
            config.treasury
        );

        log.info(`Registering wallet ${payer.publicKey.toBase58()} as a clone node...`);
        log.info(`Base URL: ${CLONE_BASE_URL}`);

        const tx = await program.methods
            .registerClone(CLONE_BASE_URL)
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

        log.info(`Registration successful. Transaction: ${tx}`);
    } catch (err: any) {
        log.error(`Registration failed: ${err.message}`);
        process.exit(1);
    }
}
