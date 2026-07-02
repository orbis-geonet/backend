import nacl from "tweetnacl";
import { Keypair } from "@solana/web3.js";
import fs from "node:fs";
import { Buffer } from "node:buffer";
import process from "node:process";
import axios from "axios";

async function run() {
    const walletPath = process.argv[2] || "./wallet/wallet4.json";
    if (!fs.existsSync(walletPath)) {
        console.error(`Wallet file not found at ${walletPath}`);
        process.exit(1);
    }

    const keyData = JSON.parse(fs.readFileSync(walletPath, "utf-8"));
    const keypair = Keypair.fromSecretKey(new Uint8Array(keyData));

    const message = Buffer.from("ORBIS_API_KEY_REQ");
    const signature = nacl.sign.detached(Uint8Array.from(message), keypair.secretKey);
    const signatureBase64 = Buffer.from(signature).toString("base64");

    console.log("Requesting API Key from Node.js (http://localhost:3000/v3/api-key)...");
    let apiKey: string;
    try {
        const res = await axios.post("http://localhost:3000/v3/api-key", {
            publicKey: keypair.publicKey.toBase58(),
            signature: signatureBase64
        });
        apiKey = res.data.token;
        console.log("-> Successfully obtained API Key:\n" + apiKey + "\n");
    } catch (e: any) {
        console.error("Failed to get API key:", e.response?.data || e.message);
        return;
    }
}

run().catch(console.error);
