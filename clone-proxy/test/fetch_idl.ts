import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey } from "@solana/web3.js";
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import * as dotenv from "dotenv";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "..");

dotenv.config({ path: path.join(rootDir, ".env") });

const KNOWN_FLAGS = [
  "--rpc",
  "--program",
  "--program-id",
  "--idl",
  "--owner",
  "--wallet",
  "--limit",
];

function envName(flag: string): string {
  return `npm_config_${flag.replace(/^--/, "").replace(/-/g, "_")}`;
}

function readArg(...flags: string[]): string | undefined {
  for (let i = 2; i < process.argv.length; i++) {
    const arg = process.argv[i];

    for (const flag of flags) {
      const prefix = `${flag}=`;

      if (arg === flag) {
        const value = process.argv[i + 1];
        if (!value || value.startsWith("--")) {
          throw new Error(`${flag} requires a value.`);
        }
        return value;
      }

      if (arg.startsWith(prefix)) {
        const value = arg.slice(prefix.length);
        if (!value) {
          throw new Error(`${flag} requires a value.`);
        }
        return value;
      }
    }
  }

  for (const flag of flags) {
    const value = process.env[envName(flag)];
    if (value && value !== "true" && value !== "false") {
      return value;
    }
  }

  return undefined;
}

function hasFlag(flag: string): boolean {
  return (
    process.argv.includes(flag) ||
    process.env[envName(flag)] === "true" ||
    process.env[envName(flag)] === "1"
  );
}

function collectPositionals(): string[] {
  const positionals: string[] = [];

  for (let i = 2; i < process.argv.length; i++) {
    const arg = process.argv[i];

    if (KNOWN_FLAGS.includes(arg)) {
      i++;
      continue;
    }

    if (KNOWN_FLAGS.some((flag) => arg.startsWith(`${flag}=`))) {
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

function looksLikeJsonPath(value: string): boolean {
  return value.endsWith(".json");
}

function looksLikeWalletPath(value: string): boolean {
  return (
    looksLikeJsonPath(value) &&
    (value.includes("wallet") || fs.existsSync(resolveWalletPath(value)))
  );
}

function looksLikeIdlPath(value: string): boolean {
  return (
    looksLikeJsonPath(value) &&
    (value.includes("idl") ||
      value.includes("orbis_protocol") ||
      fs.existsSync(resolveIdlPath(value)))
  );
}

function looksLikePublicKey(value: string): boolean {
  try {
    new PublicKey(value);
    return true;
  } catch {
    return false;
  }
}

function takePositional(
  values: string[],
  predicate: (value: string) => boolean,
): string | undefined {
  const index = values.findIndex(predicate);
  if (index === -1) {
    return undefined;
  }

  return values.splice(index, 1)[0];
}

function recoverNpmStrippedArgs(): {
  rpc?: string;
  program?: string;
  owner?: string;
  wallet?: string;
  idl?: string;
  limit?: string;
} {
  const remaining = collectPositionals();
  const recovered: {
    rpc?: string;
    program?: string;
    owner?: string;
    wallet?: string;
    idl?: string;
    limit?: string;
  } = {};

  if (process.env.npm_config_rpc === "true") {
    recovered.rpc =
      takePositional(remaining, looksLikeUrl) ?? remaining.shift();
  }

  if (process.env.npm_config_idl === "true") {
    recovered.idl =
      takePositional(remaining, looksLikeIdlPath) ?? remaining.shift();
  }

  if (process.env.npm_config_wallet === "true") {
    recovered.wallet =
      takePositional(remaining, looksLikeWalletPath) ?? remaining.pop();
  }

  if (
    process.env.npm_config_program === "true" ||
    process.env.npm_config_program_id === "true"
  ) {
    recovered.program =
      takePositional(remaining, looksLikePublicKey) ?? remaining.shift();
  }

  if (process.env.npm_config_owner === "true") {
    recovered.owner =
      takePositional(remaining, looksLikePublicKey) ?? remaining.shift();
  }

  if (process.env.npm_config_limit === "true") {
    recovered.limit =
      takePositional(remaining, (value) => /^\d+$/.test(value)) ??
      remaining.shift();
  }

  return recovered;
}

function resolveWalletPath(walletArg?: string): string {
  if (!walletArg) {
    return path.join(rootDir, "wallet", "wallet.json");
  }

  if (path.isAbsolute(walletArg)) {
    return walletArg;
  }

  const candidates = [
    path.resolve(process.cwd(), walletArg),
    path.resolve(rootDir, walletArg),
  ];

  if (!walletArg.includes("/") && !walletArg.includes("\\")) {
    candidates.push(path.resolve(rootDir, "wallet", walletArg));
  }

  return (
    candidates.find((candidate) => fs.existsSync(candidate)) ?? candidates[0]
  );
}

function resolveIdlPath(idlArg?: string): string {
  if (!idlArg) {
    return path.join(rootDir, "programs", "idl", "orbis_protocol.json");
  }

  if (path.isAbsolute(idlArg)) {
    return idlArg;
  }

  const candidates = [
    path.resolve(process.cwd(), idlArg),
    path.resolve(rootDir, idlArg),
  ];

  return (
    candidates.find((candidate) => fs.existsSync(candidate)) ?? candidates[0]
  );
}

function loadLocalIdl(idlArg: string | undefined, programId: PublicKey): any {
  const idlPath = resolveIdlPath(idlArg);

  if (!fs.existsSync(idlPath)) {
    throw new Error(`IDL file not found at ${idlPath}`);
  }

  const idl = JSON.parse(fs.readFileSync(idlPath, "utf-8"));

  return {
    ...idl,
    address: programId.toBase58(),
    __sourcePath: idlPath,
  };
}

function loadWallet(walletArg?: string): {
  keypair?: Keypair;
  walletPath?: string;
} {
  const walletPath = resolveWalletPath(walletArg);

  if (!fs.existsSync(walletPath)) {
    if (walletArg) {
      throw new Error(`Wallet file not found at ${walletPath}`);
    }
    return {};
  }

  const secretKey = Uint8Array.from(
    JSON.parse(fs.readFileSync(walletPath, "utf-8")),
  );

  return {
    keypair: Keypair.fromSecretKey(secretKey),
    walletPath,
  };
}

function readPublicKey(
  value: string | undefined,
  label: string,
): PublicKey | undefined {
  if (!value) {
    return undefined;
  }

  try {
    return new PublicKey(value);
  } catch {
    throw new Error(`Invalid ${label}: ${value}`);
  }
}

function toPrintable(value: any): any {
  if (value === null || value === undefined) {
    return value;
  }

  if (value instanceof PublicKey) {
    return value.toBase58();
  }

  if (typeof value === "bigint") {
    return value.toString();
  }

  if (typeof value !== "object") {
    return value;
  }

  if (typeof value.toBase58 === "function") {
    return value.toBase58();
  }

  if (
    typeof value.toString === "function" &&
    value.constructor?.name === "BN"
  ) {
    return value.toString();
  }

  if (Array.isArray(value)) {
    return value.map(toPrintable);
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, nestedValue]) => [
      key,
      toPrintable(nestedValue),
    ]),
  );
}

function printJson(title: string, value: unknown): void {
  console.log(`\n=== ${title} ===`);
  console.log(JSON.stringify(toPrintable(value), null, 2));
}

function getAccountClient(program: any, ...names: string[]): any {
  for (const name of names) {
    if (program.account?.[name]) {
      return program.account[name];
    }
  }

  const available = Object.keys(program.account ?? {}).join(", ");
  throw new Error(
    `Account client not found. Tried: ${names.join(", ")}. Available: ${available}`,
  );
}

function printUsage(): void {
  console.log(`
Usage:
  npm run test:idl
  npm run test:idl -- --wallet=wallet/wallet3.json
  npm run test:idl -- --owner=<clone-owner-pubkey>
  npm run test:idl -- --program=<program-id> --owner=<clone-owner-pubkey>
  npm run test:idl -- --idl=programs/idl/orbis_protocol.json
  npm run test:idl -- --on-chain-idl
  npm run test:idl -- --all-clones --limit=10

Defaults:
  --rpc      uses RPC_URL from .env
  --program  uses PROGRAM_ID from .env
  --idl      uses programs/idl/orbis_protocol.json
  --wallet   uses wallet/wallet.json when it exists
`);
}

async function main() {
  if (hasFlag("--help")) {
    printUsage();
    return;
  }

  const recoveredArgs = recoverNpmStrippedArgs();
  const positionals = collectPositionals();

  const rpcUrl = readArg("--rpc") ?? recoveredArgs.rpc ?? process.env.RPC_URL;
  const programIdInput =
    readArg("--program", "--program-id") ??
    recoveredArgs.program ??
    process.env.PROGRAM_ID;
  const ownerInput = readArg("--owner") ?? recoveredArgs.owner;
  const walletArg = readArg("--wallet") ?? recoveredArgs.wallet;
  const idlArg = readArg("--idl") ?? recoveredArgs.idl;
  const limitInput = readArg("--limit") ?? recoveredArgs.limit;

  if (!rpcUrl) {
    throw new Error("RPC_URL not set. Add it to .env or pass --rpc=<url>.");
  }

  if (!programIdInput) {
    throw new Error(
      "PROGRAM_ID not set. Add it to .env or pass --program=<program-id>.",
    );
  }

  if (positionals.length > 0 && !Object.values(recoveredArgs).some(Boolean)) {
    console.log(
      `Ignoring positional args: ${positionals.join(", ")}. Prefer --flag=value when using npm run.`,
    );
  }

  const programId = new PublicKey(programIdInput);
  const { keypair, walletPath } = loadWallet(walletArg);
  const providerKeypair = keypair ?? Keypair.generate();

  const connection = new Connection(rpcUrl, "confirmed");
  const wallet = new anchor.Wallet(providerKeypair);
  const provider = new anchor.AnchorProvider(connection, wallet, {
    preflightCommitment: "confirmed",
    commitment: "confirmed",
  });
  anchor.setProvider(provider);

  console.log("=== Fetch Orbis Program IDL + Accounts ===");
  console.log(`RPC URL      : ${rpcUrl}`);
  console.log(`Program ID   : ${programId.toBase58()}`);
  console.log(
    `Wallet file  : ${walletPath ?? "none; using generated read-only keypair"}`,
  );
  console.log(`Provider key : ${providerKeypair.publicKey.toBase58()}`);

  let idl: any;
  if (hasFlag("--on-chain-idl")) {
    const fetchedIdl = await anchor.Program.fetchIdl(programId, provider);
    if (!fetchedIdl) {
      throw new Error(
        `No Anchor IDL account found for ${programId.toBase58()}. Use the local default IDL instead, or pass --idl=<path>.`,
      );
    }
    idl = {
      ...fetchedIdl,
      address: programId.toBase58(),
      __sourcePath: "on-chain Anchor IDL account",
    };
  } else {
    idl = loadLocalIdl(idlArg, programId);
  }

  console.log("\n=== IDL ===");
  console.log(`Source       : ${idl.__sourcePath}`);
  console.log(`Name         : ${idl.metadata?.name ?? "unknown"}`);
  console.log(`Version      : ${idl.metadata?.version ?? "unknown"}`);
  console.log(`Spec         : ${idl.metadata?.spec ?? "unknown"}`);
  console.log(`Instructions : ${idl.instructions?.length ?? 0}`);
  console.log(
    `Accounts     : ${(idl.accounts ?? []).map((account: any) => account.name).join(", ")}`,
  );

  const program = new anchor.Program(idl as any, provider);
  const globalConfigClient = getAccountClient(
    program,
    "globalConfig",
    "GlobalConfig",
  );
  const cloneInfoClient = getAccountClient(program, "cloneInfo", "CloneInfo");

  const [globalConfigPda, globalConfigBump] = PublicKey.findProgramAddressSync(
    [Buffer.from("global_config")],
    programId,
  );

  console.log("\n=== Global Config PDA ===");
  console.log(`Address      : ${globalConfigPda.toBase58()}`);
  console.log(`Bump         : ${globalConfigBump}`);

  try {
    const globalConfig = await globalConfigClient.fetch(globalConfigPda);
    printJson("Global Config Data", globalConfig);
  } catch (err: any) {
    console.log("\nGlobal config account not found or could not be decoded.");
    console.log(err.message ?? err);
  }

  const owner = readPublicKey(ownerInput, "owner") ?? keypair?.publicKey;

  if (owner) {
    const [cloneInfoPda, cloneInfoBump] = PublicKey.findProgramAddressSync(
      [Buffer.from("clone_info"), owner.toBuffer()],
      programId,
    );

    console.log("\n=== Clone Info PDA ===");
    console.log(`Owner        : ${owner.toBase58()}`);
    console.log(`CloneInfo PDA: ${cloneInfoPda.toBase58()}`);
    console.log(`Bump         : ${cloneInfoBump}`);

    try {
      const cloneInfo = await cloneInfoClient.fetch(cloneInfoPda);
      printJson("Clone Info Data", cloneInfo);
    } catch (err: any) {
      console.log("\nClone info account not found or could not be decoded.");
      console.log(err.message ?? err);
      console.log(
        "This means the owner above is not registered, or PROGRAM_ID/RPC points at a different deployment.",
      );
    }
  } else {
    console.log(
      "\nNo clone owner provided and default wallet was not found; skipping single clone info lookup.",
    );
  }

  if (hasFlag("--all-clones")) {
    const limit = limitInput ? Number.parseInt(limitInput, 10) : 25;
    if (!Number.isFinite(limit) || limit <= 0) {
      throw new Error(`Invalid --limit value: ${limitInput}`);
    }

    const allCloneInfos = await cloneInfoClient.all();
    console.log("\n=== All Clone Info Accounts ===");
    console.log(`Total found  : ${allCloneInfos.length}`);
    console.log(`Showing      : ${Math.min(limit, allCloneInfos.length)}`);

    for (const entry of allCloneInfos.slice(0, limit)) {
      printJson(`Clone Info Account ${entry.publicKey.toBase58()}`, {
        cloneInfoPda: entry.publicKey,
        ...entry.account,
      });
    }
  }
}

main().catch((err) => {
  console.error("Failed:", err.message ?? err);
  process.exit(1);
});
