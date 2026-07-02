import {
    Connection,
    Keypair,
    PublicKey,
    Transaction,
    sendAndConfirmTransaction,
} from "@solana/web3.js";
import * as compression from "@solana/spl-account-compression";
import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";
import dotenv from "dotenv";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "../.env") });

const TARGET_TREE_NODES = 10_000_000;
const MAX_DEPTH = 24;
const MAX_BUFFER_SIZE = 64;
const CANOPY_DEPTH = 0;
const TREE_CAPACITY = 2 ** MAX_DEPTH;

async function run() {
    const rpc_url = process.env.RPC_URL;
    const program_id = process.env.PROGRAM_ID;
    if (!rpc_url) throw new Error("RPC_URL environment variable is missing.");
    if (!program_id) throw new Error("PROGRAM_ID environment variable is missing.");
    const connection = new Connection(rpc_url, "confirmed");

    const walletPath = path.join(__dirname, "../wallet/wallet.json");
    const secretKey = Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8")));
    const payer = Keypair.fromSecretKey(secretKey);

    const treeKeypair = Keypair.generate();
    const programId = new PublicKey(program_id);

    const [configPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("global_config")],
        programId,
    );

    const maxDepthSizePair: any = {
        maxDepth: MAX_DEPTH,
        maxBufferSize: MAX_BUFFER_SIZE,
    };

    console.log("Merkle Tree Address:", treeKeypair.publicKey.toBase58());
    console.log("Authority (Config PDA):", configPda.toBase58());
    console.log("Target Nodes:", TARGET_TREE_NODES.toLocaleString());
    console.log("Max Depth:", MAX_DEPTH);
    console.log("Tree Capacity:", TREE_CAPACITY.toLocaleString(), "leaves");
    console.log("Max Buffer Size:", MAX_BUFFER_SIZE);
    console.log("Canopy Depth:", CANOPY_DEPTH, "(no canopy)");

    const allocTreeIx = await compression.createAllocTreeIx(
        connection,
        treeKeypair.publicKey,
        payer.publicKey,
        maxDepthSizePair,
        CANOPY_DEPTH,
    );

    const initTreeIx = compression.createInitEmptyMerkleTreeIx(
        treeKeypair.publicKey,
        payer.publicKey,
        maxDepthSizePair,
    );

    const transferIx = compression.createTransferAuthorityIx(
        treeKeypair.publicKey,
        payer.publicKey,
        configPda,
    );

    const tx = new Transaction().add(allocTreeIx, initTreeIx, transferIx);

    try {
        const signature = await sendAndConfirmTransaction(
            connection,
            tx,
            [payer, treeKeypair],
            { commitment: "confirmed" },
        );
        console.log("Success!");
        console.log("Transaction Signature:", signature);
    } catch (err) {
        console.error("Initialization failed:");
        console.error(err);
    }
}

run().catch(console.error);
