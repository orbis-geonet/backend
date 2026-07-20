import { createHash } from "crypto";
import { PublicKey } from "@solana/web3.js";

export interface DecodedManifest {
    packetType: number;
    batchId: number;
    manifestHash: string;
    payloadHash: string;
    manifestPointer: string;
    entryCount: number;
    actionCount: number;
    fromTs: number;
    toTs: number;
    provider: string;
    signature: string;
    slot: number;
    timestamp: number;
}

export const MANIFEST_DISC = createHash("sha256").update("event:IndexManifestCommitted").digest().subarray(0, 8);

export function decodeManifestEvent(buffer: Buffer): DecodedManifest | null {
    try {
        let offset = 8;
        const packetType = buffer.readUInt8(offset); offset += 1;
        const batchId = buffer.readUInt32LE(offset); offset += 4;
        const manifestHash = buffer.subarray(offset, offset + 32).toString("hex"); offset += 32;
        const payloadHash = buffer.subarray(offset, offset + 32).toString("hex"); offset += 32;
        const pointerLen = buffer.readUInt32LE(offset); offset += 4;
        const manifestPointer = buffer.subarray(offset, offset + pointerLen).toString("utf-8"); offset += pointerLen;
        const entryCount = buffer.readUInt32LE(offset); offset += 4;
        const actionCount = buffer.readUInt32LE(offset); offset += 4;
        const fromTs = Number(buffer.readBigInt64LE(offset)); offset += 8;
        const toTs = Number(buffer.readBigInt64LE(offset)); offset += 8;
        const provider = new PublicKey(buffer.subarray(offset, offset + 32)).toBase58();
        return { packetType, batchId, manifestHash, payloadHash, manifestPointer, entryCount, actionCount, fromTs, toTs, provider, signature: "", slot: 0, timestamp: 0 };
    } catch {
        return null;
    }
}
