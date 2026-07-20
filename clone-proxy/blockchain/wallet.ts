import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";
import { Keypair } from "@solana/web3.js";
import { log } from "../logger.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export async function getPayer(customPath?: string): Promise<Keypair> {
    const walletPath = customPath
        ? path.join(process.cwd(), "wallet", customPath)
        : path.join(process.cwd(), "wallet", "wallet.json");
    log.info(`Loading wallet from: ${walletPath}`);
    if (!fs.existsSync(walletPath)) throw new Error(`Wallet file not found at ${walletPath}. Please provide it or use --wallet <path>`);
    const secretKey = Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8")));
    return Keypair.fromSecretKey(secretKey);
}
