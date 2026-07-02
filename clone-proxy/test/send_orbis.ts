import {
    Connection,
    Keypair,
    PublicKey
} from "@solana/web3.js";
import {
    createMint,
    getOrCreateAssociatedTokenAccount,
    mintTo,
    transfer,
    getMint
} from "@solana/spl-token";
import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function main() {
    console.log("Starting...");
    const connection = new Connection("https://api.devnet.solana.com", "confirmed");
    const walletPath = path.join(__dirname, "../wallet/wallet.json");
    const secretKey = Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8")));
    const payer = Keypair.fromSecretKey(secretKey);

    const command = process.argv[2];
    const mintAddress = "GBGPmQ6Mxz8FBxFvQwfBTdJYejDUP9G8auZfDnTwPm9";
    const DECIMALS = 9;

    if (command === "mint") {
        const amount = process.argv[3] ? parseInt(process.argv[3]) : 100000;
        const mint = new PublicKey(mintAddress);

        const payerAta = await getOrCreateAssociatedTokenAccount(
            connection,
            payer,
            mint,
            payer.publicKey
        );

        const amountInSubunits = BigInt(amount) * BigInt(Math.pow(10, DECIMALS));

        const mintSig = await mintTo(
            connection,
            payer,
            mint,
            payerAta.address,
            payer,
            amountInSubunits
        );

        console.log(`✅ Success! Minted ${amount} tokens to ${payerAta.address.toBase58()}`);
        console.log("TX:", mintSig);

    } else if (command === "send") {
        const recipientStr = process.argv[3];
        const amount = process.argv[4] ? parseInt(process.argv[4]) : 0;

        if (!recipientStr || amount <= 0) {
            console.log("Usage: node manage-orbis-token.ts send <RECIPIENT_PUBKEY> <AMOUNT>");
            return;
        }

        const mint = new PublicKey(mintAddress);
        const recipient = new PublicKey(recipientStr);

        const senderAta = await getOrCreateAssociatedTokenAccount(
            connection,
            payer,
            mint,
            payer.publicKey
        );

        const recipientAta = await getOrCreateAssociatedTokenAccount(
            connection,
            payer,
            mint,
            recipient
        );

        const amountInSubunits = BigInt(amount) * BigInt(Math.pow(10, DECIMALS));

        const txSig = await transfer(
            connection,
            payer,
            senderAta.address,
            recipientAta.address,
            payer,
            amountInSubunits
        );

        console.log(`✅ Success! Sent ${amount} tokens to ${recipient.toBase58()}`);
        console.log("TX:", txSig);

    } else {
        console.log("Usage:");
        console.log("  node manage-orbis-token.ts mint <AMOUNT>");
        console.log("  node manage-orbis-token.ts send <RECIPIENT_PUBKEY> <AMOUNT>");
    }
}

main().catch(console.error);