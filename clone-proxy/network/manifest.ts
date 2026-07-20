import { createHash } from "crypto";
import { COLLECTION_META, COLLECTION_ID_MAP } from "../config.js";

export const KEY_HASH_ZERO = "0000000000000000";
export const SHORT_HASH_ZERO = "00000000";
export const GEO_HASH_ZERO = "000000";

export interface ManifestEntry {
    ei: number;
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

export interface EntryHashes {
    keyHash: string;
    secondaryHash: string;
    nameHash: string;
    parentHash: string;
    parentHash2: string;
    authorHash: string;
    geoHash: string;
}

export function hashKeyFull(value: string | number | undefined | null): string {
    if (value === undefined || value === null || value === "") return KEY_HASH_ZERO;
    return createHash("sha256").update(String(value)).digest().subarray(0, 8).toString("hex");
}

export function hashShort(value: string | number | undefined | null): string {
    if (value === undefined || value === null || value === "") return SHORT_HASH_ZERO;
    return createHash("sha256").update(String(value)).digest().subarray(0, 4).toString("hex");
}

export function encodeGeoHash(coordinates: any): string {
    if (!coordinates) return GEO_HASH_ZERO;
    try {
        const coords = typeof coordinates === "string" ? JSON.parse(coordinates) : coordinates;
        let lon: number, lat: number;
        if (coords.coordinates && Array.isArray(coords.coordinates)) {
            [lon, lat] = coords.coordinates;
        } else if (typeof coords.longitude === "number" && typeof coords.latitude === "number") {
            lon = coords.longitude;
            lat = coords.latitude;
        } else {
            return GEO_HASH_ZERO;
        }
        const latSnapped = Math.min(399, Math.max(0, Math.floor((lat + 90) / 0.45)));
        const lonSnapped = Math.min(799, Math.max(0, Math.floor((lon + 180) / 0.45)));
        const packed = (latSnapped << 12) | lonSnapped;
        const buf = Buffer.alloc(3);
        buf[0] = (packed >> 16) & 0xff;
        buf[1] = (packed >> 8) & 0xff;
        buf[2] = packed & 0xff;
        return buf.toString("hex");
    } catch {
        return GEO_HASH_ZERO;
    }
}

export function geoHashFromLatLon(lat: number, lon: number): string {
    return encodeGeoHash({ latitude: lat, longitude: lon });
}

export function buildEntryHashes(collectionName: string, doc: any): EntryHashes {
    const meta = COLLECTION_META[collectionName];
    return {
        keyHash: hashKeyFull(meta?.primaryKey ? doc?.[meta.primaryKey] : undefined),
        secondaryHash: hashShort(meta?.secondaryKey ? doc?.[meta.secondaryKey] : undefined),
        nameHash: hashShort(meta?.nameField ? doc?.[meta.nameField] : undefined),
        parentHash: hashShort(meta?.parentKey ? doc?.[meta.parentKey] : undefined),
        parentHash2: hashShort(meta?.parentKey2 ? doc?.[meta.parentKey2] : undefined),
        authorHash: hashShort(meta?.authorField ? doc?.[meta.authorField] : undefined),
        geoHash: encodeGeoHash(meta?.geoField ? doc?.[meta.geoField] : undefined),
    };
}

export function zeroHashes(primaryKey: string | number | undefined | null): EntryHashes {
    return {
        keyHash: hashKeyFull(primaryKey),
        secondaryHash: SHORT_HASH_ZERO,
        nameHash: SHORT_HASH_ZERO,
        parentHash: SHORT_HASH_ZERO,
        parentHash2: SHORT_HASH_ZERO,
        authorHash: SHORT_HASH_ZERO,
        geoHash: GEO_HASH_ZERO,
    };
}

export function makeEntry(ei: number, collectionName: string, action: number, hashes: EntryHashes, timestamp: number): ManifestEntry {
    return {
        ei,
        collectionId: COLLECTION_ID_MAP.get(collectionName) ?? -1,
        collectionName,
        action,
        keyHash: hashes.keyHash,
        secondaryHash: hashes.secondaryHash,
        nameHash: hashes.nameHash,
        parentHash: hashes.parentHash,
        parentHash2: hashes.parentHash2,
        authorHash: hashes.authorHash,
        geoHash: hashes.geoHash,
        timestamp,
    };
}

export function searchableHashesChanged(prev: EntryHashes, next: EntryHashes): boolean {
    return prev.secondaryHash !== next.secondaryHash
        || prev.nameHash !== next.nameHash
        || prev.geoHash !== next.geoHash;
}

export function docTimestampSeconds(doc: any): number {
    const ts = doc?.timestamp || doc?.createTimestamp || doc?.createdAt || doc?.updatedAt;
    if (!ts) return Math.floor(Date.now() / 1000);
    if (ts instanceof Date) return Math.floor(ts.getTime() / 1000);
    if (typeof ts === "number") return ts > 1e12 ? Math.floor(ts / 1000) : ts;
    if (typeof ts === "string") {
        const parsed = new Date(ts).getTime();
        return Number.isFinite(parsed) ? Math.floor(parsed / 1000) : Math.floor(Date.now() / 1000);
    }
    return Math.floor(Date.now() / 1000);
}

export function canonicalStringify(value: any): string {
    if (value === null || typeof value !== "object") {
        return JSON.stringify(value);
    }
    if (Array.isArray(value)) {
        return "[" + value.map(canonicalStringify).join(",") + "]";
    }
    const keys = Object.keys(value).sort();
    return "{" + keys.map(k => JSON.stringify(k) + ":" + canonicalStringify(value[k])).join(",") + "}";
}

export function sha256Hex(data: string | Buffer): string {
    return createHash("sha256").update(data).digest("hex");
}

export function sha256Bytes(data: string | Buffer): Buffer {
    return createHash("sha256").update(data).digest();
}
