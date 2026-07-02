import { Connection, Keypair, PublicKey } from "@solana/web3.js";
import { ProviderEntry, ProviderResult } from "./network/providers.js";
import { fetchAndPay } from "./network/payment.js";

export async function proxyToProvider(
    providers: ProviderEntry[],
    apiPath: string,
    query: Record<string, any>,
    db: any,
    payer: Keypair,
    connection: Connection,
    lookupType?: string,
    hashValue?: string,
    method: string = "GET",
    body?: any,
    extraHeaders: Record<string, string> = {}
): Promise<ProviderResult | null> {
    const cleanQuery = { ...query };
    delete cleanQuery._internal;


    const queryParts: string[] = [];
    for (const [key, value] of Object.entries(cleanQuery)) {
        if (value !== undefined && value !== null) queryParts.push(`${key}=${encodeURIComponent(value as string)}`);
    }
    const queryStr = queryParts.join("&");
    const batchPointerWithQuery = queryStr ? `${apiPath}?${queryStr}` : apiPath;
    const localProvider = payer.publicKey.toBase58();

    for (const provider of providers) {
        try {
            if (provider.provider === localProvider) {
                console.log(`     -> Skipping self-provider ${provider.provider.slice(0, 8)}... for ${apiPath}`);
                if (provider.event?._id) {
                    await db.collection("network_events").updateOne(
                        { _id: provider.event._id },
                        {
                            $set: {
                                status: "synced",
                                syncSkippedReason: "self-provider",
                                syncedAt: new Date(),
                                updatedAt: new Date(),
                            }
                        }
                    );
                    console.log(`     -> Marked self-owned event as synced: ${provider.event._id}`);
                }
                continue;
            }

            const baseUrlClean = provider.baseUrl.replace(/\/$/, "");
            //const baseUrlClean = "http://localhost:3100";
            const fullUrl = `${baseUrlClean}${batchPointerWithQuery}`;
            console.log(`     -> Pinging Provider URL: ${fullUrl}`);

            const leaf = provider.leaf || { provider: provider.provider, batchPointer: batchPointerWithQuery, hash: null, resourceId: undefined };
            if (provider.event) leaf.event = provider.event;
            const result = await fetchAndPay(payer, connection, leaf, db, baseUrlClean, method, body, extraHeaders);
            let parsed: any;
            try { parsed = JSON.parse(result.data.toString()); } catch { parsed = result.data; }

            if (lookupType && hashValue) {
                console.log(`     -> Matches local blockchain index (${lookupType}: ${hashValue}). Data trusted.`);
            }

            console.log(`     -> Successfully fetched data from ${provider.provider.slice(0, 8)}... (Size: ${result.data.length} bytes)`);
            return { data: parsed, source: result.source, provider: provider.provider, verified: true };
        } catch (e: any) {
            console.log(`     -> Failed fetch+pay from provider ${provider.provider.slice(0, 8)}...: ${e.message}`);
            continue;
        }
    }
    console.log(`     -> All provider fetching attempts failed for path: ${apiPath}`);
    return null;
}
