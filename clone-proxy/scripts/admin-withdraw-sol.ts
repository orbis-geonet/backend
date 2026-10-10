import {
  Connection,
  Keypair,
  PublicKey,
  SystemProgram,
  Transaction,
  TransactionInstruction,
  sendAndConfirmTransaction,
} from "@solana/web3.js";
import { createHash } from "node:crypto";
import * as dotenv from "dotenv";
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = path.resolve(__dirname, "..");
const PROGRAM_ID = new PublicKey("x2HARL2kBx2jVHtgWvf8sX8zbZ3sBAvtHJeg8AgosqR");
const MAINNET_GENESIS_HASH = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d";
const U64_MAX = 18_446_744_073_709_551_615n;

dotenv.config({ path: path.resolve(root, ".env"), quiet: true });

function readArg(name: string): string | undefined {
  const index = process.argv.indexOf(name);
  if (index === -1) return undefined;
  const value = process.argv[index + 1];
  return value && !value.startsWith("--") ? value : undefined;
}

function resolveWalletPath(walletArg: string): string {
  if (path.isAbsolute(walletArg)) return walletArg;
  const candidates = [
    path.resolve(process.cwd(), walletArg),
    path.resolve(root, walletArg),
    path.resolve(root, "wallet", walletArg),
  ];
  return (
    candidates.find((candidate) => fs.existsSync(candidate)) || candidates[0]
  );
}

function parseAmount(value: string, decimals: number): bigint {
  const match = value.trim().match(/^(\d+)(?:\.(\d+))?$/);
  if (!match) throw new Error(`Invalid amount: ${value}`);
  if ((match[2] || "").length > decimals)
    throw new Error(`Amount supports at most ${decimals} decimal places`);
  const raw = BigInt(`${match[1]}${(match[2] || "").padEnd(decimals, "0")}`);
  if (raw <= 0n || raw > U64_MAX)
    throw new Error("Amount is outside the supported range");
  return raw;
}

function discriminator(namespace: string, name: string): Buffer {
  return createHash("sha256")
    .update(`${namespace}:${name}`)
    .digest()
    .subarray(0, 8);
}

function instructionData(name: string, amount: bigint): Buffer {
  const amountData = Buffer.alloc(8);
  amountData.writeBigUInt64LE(amount);
  return Buffer.concat([discriminator("global", name), amountData]);
}

async function loadConfig(
  connection: Connection,
  configPda: PublicKey,
): Promise<{ admin: PublicKey }> {
  const account = await connection.getAccountInfo(configPda, "confirmed");
  if (!account || !account.owner.equals(PROGRAM_ID))
    throw new Error("GlobalConfig account is missing or has the wrong owner");
  if (
    account.data.length < 104 ||
    !account.data
      .subarray(0, 8)
      .equals(discriminator("account", "GlobalConfig"))
  ) {
    throw new Error("GlobalConfig account data is invalid");
  }
  return { admin: new PublicKey(account.data.subarray(8, 40)) };
}

async function main() {
  const amountArg = readArg("--amount");
  const walletArg = readArg("--wallet") || process.env.ADMIN_WALLET_PATH;
  const rpcUrl =
    process.env.ADMIN_RPC_URL ||
    process.env.PAYMENTS_RPC_URL ||
    process.env.RPC_URL;
  if (!amountArg || !walletArg || !rpcUrl) {
    throw new Error(
      "Usage: npm run admin:withdraw-sol -- --amount 1 --wallet <admin-keypair.json> [--recipient <pubkey>] [--send]",
    );
  }

  const walletPath = resolveWalletPath(walletArg);
  if (!fs.existsSync(walletPath))
    throw new Error(`Admin wallet file not found: ${walletPath}`);
  const admin = Keypair.fromSecretKey(
    Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8"))),
  );
  const recipient = new PublicKey(readArg("--recipient") || admin.publicKey);
  const amount = parseAmount(amountArg, 9);
  const connection = new Connection(rpcUrl, "confirmed");

  if ((await connection.getGenesisHash()) !== MAINNET_GENESIS_HASH)
    throw new Error("RPC endpoint is not Solana mainnet");

  const [configPda] = PublicKey.findProgramAddressSync(
    [Buffer.from("global_config")],
    PROGRAM_ID,
  );
  const [solVault] = PublicKey.findProgramAddressSync(
    [Buffer.from("exchange_sol_vault")],
    PROGRAM_ID,
  );
  const config = await loadConfig(connection, configPda);
  if (!config.admin.equals(admin.publicKey)) {
    throw new Error(
      `Wallet ${admin.publicKey.toBase58()} is not the configured admin ${config.admin.toBase58()}`,
    );
  }

  const vaultBalance = BigInt(
    await connection.getBalance(solVault, "confirmed"),
  );
  if (vaultBalance < amount)
    throw new Error(
      `SOL vault has ${vaultBalance} lamports but ${amount} were requested`,
    );

  console.log(`Program       : ${PROGRAM_ID.toBase58()}`);
  console.log(`Admin         : ${admin.publicKey.toBase58()}`);
  console.log(`SOL vault     : ${solVault.toBase58()}`);
  console.log(`Recipient     : ${recipient.toBase58()}`);
  console.log(`Vault lamports: ${vaultBalance}`);
  console.log(`Withdraw      : ${amount} lamports`);

  if (!process.argv.includes("--send")) {
    console.log("Preview only. Add --send to submit the withdrawal.");
    return;
  }

  const instruction = new TransactionInstruction({
    programId: PROGRAM_ID,
    keys: [
      { pubkey: configPda, isSigner: false, isWritable: false },
      { pubkey: admin.publicKey, isSigner: true, isWritable: false },
      { pubkey: solVault, isSigner: false, isWritable: true },
      { pubkey: recipient, isSigner: false, isWritable: true },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
    ],
    data: instructionData("admin_withdraw_sol", amount),
  });

  const signature = await sendAndConfirmTransaction(
    connection,
    new Transaction().add(instruction),
    [admin],
    {
      commitment: "confirmed",
      preflightCommitment: "confirmed",
    },
  );
  console.log(`Signature     : ${signature}`);
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
