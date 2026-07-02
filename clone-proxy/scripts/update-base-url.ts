import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey } from "@solana/web3.js";
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import * as dotenv from "dotenv";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "../.env") });

function readArg(flag: string): string | undefined {
  const prefix = `${flag}=`;

  for (let i = 2; i < process.argv.length; i++) {
    const arg = process.argv[i];

    if (arg === flag) {
      const value = process.argv[i + 1];
      if (!value || value.startsWith("--")) {
        console.error(`${flag} requires a value.`);
        process.exit(1);
      }
      return value;
    }

    if (arg.startsWith(prefix)) {
      const value = arg.slice(prefix.length);
      if (!value) {
        console.error(`${flag} requires a value.`);
        process.exit(1);
      }
      return value;
    }
  }

  return undefined;
}

function readNpmConfigValue(name: string): string | undefined {
  const value = process.env[`npm_config_${name}`];
  if (!value || value === "true" || value === "false") {
    return undefined;
  }

  return value;
}

function resolveWalletPath(walletArg?: string): string {
  const cloneProxyRoot = path.resolve(__dirname, "..");

  if (!walletArg) {
    return path.join(cloneProxyRoot, "wallet", "wallet.json");
  }

  if (path.isAbsolute(walletArg)) {
    return walletArg;
  }

  const candidates = [
    path.resolve(process.cwd(), walletArg),
    path.resolve(cloneProxyRoot, walletArg),
  ];

  if (!walletArg.includes("/") && !walletArg.includes("\\")) {
    candidates.push(path.resolve(cloneProxyRoot, "wallet", walletArg));
  }

  return (
    candidates.find((candidate) => fs.existsSync(candidate)) ?? candidates[0]
  );
}

function collectPositionalArgs(): string[] {
  const knownFlags = new Set(["--url", "--wallet"]);
  const positionals: string[] = [];

  for (let i = 2; i < process.argv.length; i++) {
    const arg = process.argv[i];

    if (knownFlags.has(arg)) {
      i++;
      continue;
    }

    if ([...knownFlags].some((flag) => arg.startsWith(`${flag}=`))) {
      continue;
    }

    if (arg === "--" || arg.startsWith("--")) {
      continue;
    }

    positionals.push(arg);
  }

  return positionals;
}

function looksLikeUrl(value: string): boolean {
  return (
    /^[a-z][a-z\d+.-]*:\/\//i.test(value) || /^[^/\s]+:\d+(?:\/|$)/.test(value)
  );
}

function looksLikeWalletPath(value: string): boolean {
  return value.endsWith(".json") || fs.existsSync(resolveWalletPath(value));
}

function recoverNpmStrippedArgs(): { url?: string; wallet?: string } {
  const urlWasStripped = process.env.npm_config_url === "true";
  const walletWasStripped = process.env.npm_config_wallet === "true";

  if (!urlWasStripped && !walletWasStripped) {
    return {};
  }

  const remaining = collectPositionalArgs();
  let url: string | undefined;
  let wallet: string | undefined;

  if (urlWasStripped) {
    const urlIndex = remaining.findIndex(looksLikeUrl);
    const selectedIndex = urlIndex === -1 ? 0 : urlIndex;
    url = remaining.splice(selectedIndex, 1)[0];
  }

  if (walletWasStripped) {
    const walletIndex = remaining.findIndex(looksLikeWalletPath);
    const selectedIndex = walletIndex === -1 ? 0 : walletIndex;
    wallet = remaining.splice(selectedIndex, 1)[0];
  }

  return { url, wallet };
}

async function main() {
  const rpcUrl = process.env.RPC_URL;
  const programIdStr = process.env.PROGRAM_ID;
  if (!rpcUrl) throw new Error("RPC_URL not set in .env");
  if (!programIdStr) throw new Error("PROGRAM_ID not set in .env");

  const recoveredArgs = recoverNpmStrippedArgs();

  const newBaseUrl =
    readArg("--url") ??
    readNpmConfigValue("url") ??
    recoveredArgs.url ??
    process.env.CLONE_BASE_URL;
  if (!newBaseUrl) {
    console.error(
      "Missing new base URL. Pass --url <newUrl> or set CLONE_BASE_URL in .env",
    );
    process.exit(1);
  }

  const walletArg =
    readArg("--wallet") ?? readNpmConfigValue("wallet") ?? recoveredArgs.wallet;
  const walletPath = resolveWalletPath(walletArg);

  if (!fs.existsSync(walletPath)) {
    console.error(`Wallet file not found at ${walletPath}`);
    if (walletArg && !path.isAbsolute(walletArg)) {
      console.error(
        `Relative wallet paths are resolved from ${process.cwd()} and ${path.resolve(__dirname, "..")}.`,
      );
    }
    process.exit(1);
  }

  const secretKey = Uint8Array.from(
    JSON.parse(fs.readFileSync(walletPath, "utf-8")),
  );
  const owner = Keypair.fromSecretKey(secretKey);

  const programId = new PublicKey(programIdStr);
  const connection = new Connection(rpcUrl, "confirmed");
  const wallet = new anchor.Wallet(owner);
  const provider = new anchor.AnchorProvider(connection, wallet, {
    preflightCommitment: "confirmed",
  });
  anchor.setProvider(provider);

  const idl = JSON.parse(
    fs.readFileSync(
      path.resolve(__dirname, "../programs/idl/orbis_protocol.json"),
      "utf-8",
    ),
  );
  const program = new anchor.Program(idl as any, provider);

  const [cloneInfoPda] = PublicKey.findProgramAddressSync(
    [Buffer.from("clone_info"), owner.publicKey.toBuffer()],
    programId,
  );

  console.log("=== Update Clone Base URL ===");
  console.log(`Wallet file   : ${walletPath}`);
  console.log(`Owner         : ${owner.publicKey.toBase58()}`);
  console.log(`Clone Info PDA: ${cloneInfoPda.toBase58()}`);
  console.log(`New Base URL  : ${newBaseUrl}`);

  let existing: any;
  try {
    existing = await program.account.cloneInfo.fetch(cloneInfoPda);
  } catch {
    console.error(
      "Clone is not registered on-chain. Run client-register-clone first.",
    );
    process.exit(1);
  }

  console.log(`Current URL   : ${existing.baseUrl}`);

  if (existing.baseUrl === newBaseUrl) {
    console.log("Base URL already matches. Nothing to update.");
    process.exit(0);
  }

  if (existing.owner.toBase58() !== owner.publicKey.toBase58()) {
    console.error(
      `Owner mismatch. Clone owner is ${existing.owner.toBase58()}, signer is ${owner.publicKey.toBase58()}.`,
    );
    process.exit(1);
  }

  console.log("\nSubmitting update_clone_base_url...");

  const tx = await program.methods
    .updateCloneBaseUrl(newBaseUrl)
    .accounts({
      cloneInfo: cloneInfoPda,
      owner: owner.publicKey,
    } as any)
    .rpc();

  console.log(`\n✅ Base URL updated`);
  console.log(`   TX: ${tx}`);

  const updated: any = await program.account.cloneInfo.fetch(cloneInfoPda);
  console.log(`   New on-chain baseUrl: ${updated.baseUrl}`);
}

main().catch((err) => {
  console.error("❌ Failed:", err.message ?? err);
  process.exit(1);
});
