import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import * as dotenv from "dotenv";
import axios from "axios";
import {
    Connection,
    Keypair,
    PublicKey,
    SystemProgram,
    Transaction,
    TransactionInstruction,
    sendAndConfirmTransaction,
    SYSVAR_RENT_PUBKEY,
} from "@solana/web3.js";
import {
    TOKEN_PROGRAM_ID,
    MintLayout,
    createInitializeMintInstruction,
    createAssociatedTokenAccountInstruction,
    createMintToInstruction,
    getAssociatedTokenAddressSync,
    ASSOCIATED_TOKEN_PROGRAM_ID,
} from "@solana/spl-token";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

dotenv.config({ path: path.resolve(__dirname, "../.env") });

const METADATA_PROGRAM_ID = new PublicKey("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s");
const PINATA_BASE = "https://api.pinata.cloud/pinning";

interface TokenConfig {
    name: string;
    symbol: string;
    decimals: number;
    supply: number;
    metadataUri: string;
}

async function pinFileToPinata(jwt: string, buffer: Buffer, filename: string, contentType: string): Promise<string> {
    const arrayBuffer = new ArrayBuffer(buffer.byteLength);
    new Uint8Array(arrayBuffer).set(buffer);

    const form = new FormData();
    form.append("file", new Blob([arrayBuffer], { type: contentType }), filename);
    const res = await axios.post(`${PINATA_BASE}/pinFileToIPFS`, form, {
        headers: { Authorization: `Bearer ${jwt}` },
        maxBodyLength: Infinity,
        maxContentLength: Infinity,
    });
    return `https://ipfs.io/ipfs/${res.data.IpfsHash}`;
}

async function pinJsonToPinata(jwt: string, content: object, name: string): Promise<string> {
    const res = await axios.post(
        `${PINATA_BASE}/pinJSONToIPFS`,
        { pinataMetadata: { name }, pinataContent: content },
        { headers: { Authorization: `Bearer ${jwt}`, "Content-Type": "application/json" } },
    );
    return `https://ipfs.io/ipfs/${res.data.IpfsHash}`;
}

function encodeStr(value: string): Buffer {
    const bytes = Buffer.from(value, "utf-8");
    const len = Buffer.allocUnsafe(4);
    len.writeUInt32LE(bytes.length);
    return Buffer.concat([len, bytes]);
}

function buildCreateMetadataV3Ix(
    name: string,
    symbol: string,
    uri: string,
    metadataPda: PublicKey,
    mint: PublicKey,
    mintAuthority: PublicKey,
    payer: PublicKey,
    updateAuthority: PublicKey,
): TransactionInstruction {
    const data = Buffer.concat([
        Buffer.from([33]),
        encodeStr(name),
        encodeStr(symbol),
        encodeStr(uri),
        Buffer.from([0, 0]),
        Buffer.from([0]),
        Buffer.from([0]),
        Buffer.from([0]),
        Buffer.from([1]),
        Buffer.from([0]),
    ]);

    return new TransactionInstruction({
        programId: METADATA_PROGRAM_ID,
        keys: [
            { pubkey: metadataPda, isSigner: false, isWritable: true },
            { pubkey: mint, isSigner: false, isWritable: false },
            { pubkey: mintAuthority, isSigner: true, isWritable: false },
            { pubkey: payer, isSigner: true, isWritable: true },
            { pubkey: updateAuthority, isSigner: false, isWritable: false },
            { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
            { pubkey: SYSVAR_RENT_PUBKEY, isSigner: false, isWritable: false },
        ],
        data,
    });
}

async function main() {
    const rpcUrl = process.env.RPC_URL;
    if (!rpcUrl) throw new Error("RPC_URL not set in .env");

    const root = path.resolve(__dirname, "..");

    const tokenConfig: TokenConfig = JSON.parse(
        fs.readFileSync(path.join(root, "token_config.json"), "utf-8")
    );

    const pinataJwt = process.env.PINATA_JWT;
    if (!pinataJwt) throw new Error("PINATA_JWT not set in .env");

    const logoPath = path.join(root, "assets", "orbis_logo.png");
    const logoBuffer = fs.readFileSync(logoPath);

    console.log("Uploading logo to IPFS...");
    const imageUri = await pinFileToPinata(pinataJwt, logoBuffer, "orbis_logo.png", "image/png");
    console.log(`Logo     : ${imageUri}`);

    console.log("Uploading metadata to IPFS...");
    const uri = await pinJsonToPinata(
        pinataJwt,
        { name: tokenConfig.name, symbol: tokenConfig.symbol, image: imageUri },
        `${tokenConfig.symbol}_metadata.json`,
    );
    console.log(`Metadata : ${uri}`);

    const walletIdx = process.argv.indexOf("--wallet");
    const walletPath = walletIdx !== -1
        ? process.argv[walletIdx + 1]
        : path.join(root, "wallet", "wallet.json");

    if (!fs.existsSync(walletPath)) {
        console.error(`Wallet not found: ${walletPath}`);
        process.exit(1);
    }

    const secretKey = Uint8Array.from(JSON.parse(fs.readFileSync(walletPath, "utf-8")));
    const payer = Keypair.fromSecretKey(secretKey);

    const connection = new Connection(rpcUrl, "confirmed");
    const balance = await connection.getBalance(payer.publicKey);

    console.log(`Admin wallet : ${payer.publicKey.toBase58()}`);
    console.log(`Balance      : ${(balance / 1e9).toFixed(4)} SOL`);

    const mintKeypair = Keypair.generate();

    const [metadataPda] = PublicKey.findProgramAddressSync(
        [
            Buffer.from("metadata"),
            METADATA_PROGRAM_ID.toBuffer(),
            mintKeypair.publicKey.toBuffer(),
        ],
        METADATA_PROGRAM_ID
    );

    const mintRent = await connection.getMinimumBalanceForRentExemption(MintLayout.span);

    const createMintTx = new Transaction().add(
        SystemProgram.createAccount({
            fromPubkey: payer.publicKey,
            newAccountPubkey: mintKeypair.publicKey,
            lamports: mintRent,
            space: MintLayout.span,
            programId: TOKEN_PROGRAM_ID,
        }),
        createInitializeMintInstruction(
            mintKeypair.publicKey,
            tokenConfig.decimals,
            payer.publicKey,
            null,
            TOKEN_PROGRAM_ID,
        ),
        buildCreateMetadataV3Ix(
            tokenConfig.name,
            tokenConfig.symbol,
            uri,
            metadataPda,
            mintKeypair.publicKey,
            payer.publicKey,
            payer.publicKey,
            payer.publicKey,
        ),
    );

    console.log("Creating mint + metadata...");
    await sendAndConfirmTransaction(connection, createMintTx, [payer, mintKeypair]);

    console.log(`Mint     : ${mintKeypair.publicKey.toBase58()}`);
    console.log(`Metadata : ${metadataPda.toBase58()}`);

    const ata = getAssociatedTokenAddressSync(
        mintKeypair.publicKey,
        payer.publicKey,
        false,
        TOKEN_PROGRAM_ID,
        ASSOCIATED_TOKEN_PROGRAM_ID,
    );

    const mintAmount = BigInt(tokenConfig.supply) * BigInt(10 ** tokenConfig.decimals);

    const mintSupplyTx = new Transaction().add(
        createAssociatedTokenAccountInstruction(
            payer.publicKey,
            ata,
            payer.publicKey,
            mintKeypair.publicKey,
            TOKEN_PROGRAM_ID,
            ASSOCIATED_TOKEN_PROGRAM_ID,
        ),
        createMintToInstruction(
            mintKeypair.publicKey,
            ata,
            payer.publicKey,
            mintAmount,
            [],
            TOKEN_PROGRAM_ID,
        ),
    );

    console.log("Minting supply...");
    await sendAndConfirmTransaction(connection, mintSupplyTx, [payer]);

    console.log(`ATA      : ${ata.toBase58()}`);
    console.log(`Minted   : ${tokenConfig.supply.toLocaleString()} ${tokenConfig.symbol}`);
}

main().catch(console.error);
