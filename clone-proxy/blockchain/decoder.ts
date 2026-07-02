import { createHash } from "crypto";
import { PublicKey } from "@solana/web3.js";
import { COLLECTION_ORDER } from "../config.js";

export interface DecodedCollectionRoot {
    collectionId: number;
    collectionName: string;
    root: string;
}

export interface DecodedBatchEvent {
    collectionId: number;
    collectionName: string;
    action: number;
    keyHash: string;
    secondaryHash: string;
    nameHash: string;
    parentHash: string;
    parentHash2: string;
    authorHash: string;
    geoHash: string;
    timestamp: number;
}

export interface DecodedBatch {
    packetType: number;
    batchId: number;
    collectionRoots: DecodedCollectionRoot[];
    events: DecodedBatchEvent[];
    batchPointer: string;
    provider: string;
    signature: string;
    slot: number;
    timestamp: number;
}

const FIELD_SECONDARY = 1 << 0;
const FIELD_NAME      = 1 << 1;
const FIELD_PARENT    = 1 << 2;
const FIELD_PARENT2   = 1 << 3;
const FIELD_AUTHOR    = 1 << 4;
const FIELD_GEO       = 1 << 5;

export const BATCH_DISC = createHash("sha256").update("event:CollectionBatchSynced").digest().subarray(0, 8);

export function decodeBatchEvent(buffer: Buffer): DecodedBatch | null {
    try {
        let offset = 8;
        const packetType = buffer.readUInt8(offset); offset += 1;
        const batchId = buffer.readUInt32LE(offset); offset += 4;
        const rootsLen = buffer.readUInt32LE(offset); offset += 4;
        const collectionRoots: DecodedCollectionRoot[] = [];
        for (let i = 0; i < rootsLen; i++) {
            const collectionId = buffer.readUInt8(offset); offset += 1;
            const root = buffer.subarray(offset, offset + 32).toString("hex"); offset += 32;
            collectionRoots.push({ collectionId, collectionName: COLLECTION_ORDER[collectionId] || `unknown_${collectionId}`, root });
        }
        const eventsLen = buffer.readUInt32LE(offset); offset += 4;
        const events: DecodedBatchEvent[] = [];
        for (let i = 0; i < eventsLen; i++) {
            const collectionId = buffer.readUInt8(offset); offset += 1;
            const action = buffer.readUInt8(offset); offset += 1;
            const fieldMask = buffer.readUInt8(offset); offset += 1;
            const keyHash = buffer.subarray(offset, offset + 8).toString("hex"); offset += 8;
            const timestamp = buffer.readUInt32LE(offset); offset += 4;
            const optDataLen = buffer.readUInt32LE(offset); offset += 4;
            const optData = buffer.subarray(offset, offset + optDataLen); offset += optDataLen;
            let o = 0;
            const r4 = () => { const v = optData.subarray(o, o + 4).toString("hex"); o += 4; return v; };
            const r3 = () => { const v = optData.subarray(o, o + 3).toString("hex"); o += 3; return v; };
            const secondaryHash = (fieldMask & FIELD_SECONDARY) ? r4() : "00000000";
            const nameHash      = (fieldMask & FIELD_NAME)      ? r4() : "00000000";
            const parentHash    = (fieldMask & FIELD_PARENT)    ? r4() : "00000000";
            const parentHash2   = (fieldMask & FIELD_PARENT2)   ? r4() : "00000000";
            const authorHash    = (fieldMask & FIELD_AUTHOR)    ? r4() : "00000000";
            const geoHash       = (fieldMask & FIELD_GEO)       ? r3() : "000000";
            events.push({ collectionId, collectionName: COLLECTION_ORDER[collectionId] || `unknown_${collectionId}`, action, keyHash, secondaryHash, nameHash, parentHash, parentHash2, authorHash, geoHash, timestamp });
        }
        const pointerLen = buffer.readUInt32LE(offset); offset += 4;
        const batchPointer = buffer.subarray(offset, offset + pointerLen).toString("utf-8"); offset += pointerLen;
        const provider = new PublicKey(buffer.subarray(offset, offset + 32)).toBase58();
        return { packetType, batchId, collectionRoots, events, batchPointer, provider, signature: "", slot: 0, timestamp: 0 };
    } catch { return null; }
}

