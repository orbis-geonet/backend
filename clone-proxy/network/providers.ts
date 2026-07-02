import { createHash } from "crypto";

export interface ProviderEntry {
    provider: string;
    baseUrl: string;
    batchPointer?: string;
    leaf?: any;
    event?: any;
    sourceType?: string;
}

export interface ProviderResult {
    data: any;
    source: string;
    provider: string;
    verified: boolean;
}

function hashKey8(key: string): string {
    return createHash("sha256").update(key).digest().subarray(0, 8).toString("hex");
}
function hashKey4(key: string): string {
    return createHash("sha256").update(key).digest().subarray(0, 4).toString("hex");
}

function geoHashEncode(lat: number, lon: number): string {
    const latSnapped = Math.min(399, Math.max(0, Math.floor((lat + 90) / 0.45)));
    const lonSnapped = Math.min(799, Math.max(0, Math.floor((lon + 180) / 0.45)));
    const packed = (latSnapped << 12) | lonSnapped;
    const buf = Buffer.alloc(3);
    buf[0] = (packed >> 16) & 0xff;
    buf[1] = (packed >> 8) & 0xff;
    buf[2] = packed & 0xff;
    const hex = buf.toString("hex");
    console.log(`     -> Geohash Query: lat=${lat}, lon=${lon} => ${hex}`);
    return hex;
}

export async function findProviders(
    db: any,
    collectionName: string,
    lookupType: string,
    value: string,
    lat?: number,
    lon?: number,
    eventId?: string
): Promise<{ providers: ProviderEntry[], hashValue: string }> {
    console.log(`\n[Lookup] Looking for data in '${collectionName}' using ${eventId ? `eventId:${eventId}` : `${lookupType}=${value ? value : `lat:${lat},lon:${lon}`}`}`);
    const providers: ProviderEntry[] = [];
    let hashValue = "";

    if (eventId) {
        try {
            const ev = await db.collection("network_events").findOne({ _id: new (await import("mongodb")).ObjectId(eventId) });
            if (ev) {
                const providerDoc = await db.collection("network_providers").findOne({ provider: ev.provider });
                if (providerDoc?.baseUrl) {
                    console.log(`  -> Discovered targeted Delta Event [Provider: ${ev.provider.slice(0, 8)}...]`);
                    providers.push({
                        provider: ev.provider,
                        baseUrl: providerDoc.baseUrl,
                        batchPointer: `/${collectionName}/${ev._id}`,
                        sourceType: "Delta Batch"
                    });
                }
            }
        } catch (e) { }
    } else {
        if (lookupType === "geo" && lat !== undefined && lon !== undefined) {
            hashValue = geoHashEncode(lat, lon);
        } else {
            const is8 = ["key", "secondary", "email"].includes(lookupType);
            hashValue = is8 ? hashKey8(value) : hashKey4(value);
        }
    }

    if (providers.length === 0) {
        const genesisDocs = await db.collection("network_providers").find({ isGenesis: true }).sort({ trustScore: -1 }).toArray();
        const validGenesis = (genesisDocs as any[]).find(doc => doc.baseUrl && doc.collectionRoots?.[collectionName]);

        if (validGenesis) {
            const endpoint = validGenesis.collectionEndpoints?.[collectionName] || `/${collectionName}`;
            console.log(`  -> Discovery reverting to Genesis Fallback [Provider: ${validGenesis.provider.slice(0, 8)}... (Score: ${validGenesis.trustScore || 0})]`);
            providers.push({
                provider: validGenesis.provider,
                baseUrl: validGenesis.baseUrl,
                batchPointer: endpoint,
                sourceType: "Genesis Cache"
            });
        }
    }

    if (providers.length === 0) {
        console.log(`  -> No providers found (Delta or Genesis).`);
    }

    return { providers, hashValue };
}
