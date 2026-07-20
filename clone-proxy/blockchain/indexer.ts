import { Connection, Keypair, PublicKey } from "@solana/web3.js";
import bs58 from "bs58";
import axios from "axios";
import { MANIFEST_DISC, decodeManifestEvent, DecodedManifest } from "./decoder.js";
import { SELF_CONSUME } from "../config.js";
import { sha256Hex } from "../network/manifest.js";
import { log } from "../logger.js";
import { readFileSync } from "node:fs";

export async function fetchCloneInfo(connection: Connection, providerPubkey: PublicKey, programId: PublicKey): Promise<{ baseUrl: string, trustScore: number, isGenesis: boolean } | null> {
    try {
        const [cloneInfoPda] = PublicKey.findProgramAddressSync([Buffer.from("clone_info"), providerPubkey.toBuffer()], programId);

        const wallet = new (await import("@coral-xyz/anchor")).Wallet(Keypair.generate());
        const anchorProvider = new (await import("@coral-xyz/anchor")).AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
        const idl = JSON.parse(readFileSync(new URL("../programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));
        const program = new (await import("@coral-xyz/anchor")).Program(idl as any, anchorProvider);

        const info: any = await program.account.cloneInfo.fetch(cloneInfoPda);
        const baseUrl = info.baseUrl as string;
        const trustScore = Number(info.trustScore);
        const isGenesis = Boolean(info.isGenesis);

        return { baseUrl: baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl, trustScore, isGenesis };
    } catch (e: any) {
        return null;
    }
}

export async function discoverAllProviders(db: any, connection: Connection, programId: PublicKey) {
    try {
        const wallet = new (await import("@coral-xyz/anchor")).Wallet(Keypair.generate());
        const anchorProvider = new (await import("@coral-xyz/anchor")).AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
        const idl = JSON.parse(readFileSync(new URL("../programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));
        const program = new (await import("@coral-xyz/anchor")).Program(idl as any, anchorProvider);

        const allClones: any[] = await program.account.cloneInfo.all();
        let upserted = 0;
        for (const entry of allClones) {
            const info = entry.account;
            const provider = info.owner.toBase58();
            const rawBaseUrl = (info.baseUrl as string) || "";
            const baseUrl = rawBaseUrl.endsWith("/") ? rawBaseUrl.slice(0, -1) : rawBaseUrl;
            const trustScore = Number(info.trustScore || 0);
            const isGenesis = Boolean(info.isGenesis);

            await db.collection("network_providers").updateOne(
                { provider },
                {
                    $set: { baseUrl, trustScore, isGenesis, updatedAt: new Date() },
                    $setOnInsert: { provider, collectionRoots: {}, collectionEndpoints: {}, latestBatchId: 0 }
                },
                { upsert: true }
            );
            upserted++;
        }
        log.info(`Discovered ${upserted} clones from on-chain CloneInfo PDAs`);
    } catch (e: any) {
        log.error(`discoverAllProviders failed: ${e.message}`);
    }
}

export async function scanHistory(connection: Connection, addressToScan: PublicKey, untilSignature?: string): Promise<DecodedManifest[]> {
    if (untilSignature) {
        const latestSigs = await connection.getSignaturesForAddress(addressToScan, { limit: 1 });
        if (latestSigs.length > 0 && latestSigs[0].signature === untilSignature) {
            return [];
        }
    }

    const signatures: string[] = [];
    let before: string | undefined;

    while (true) {
        const sigs = await connection.getSignaturesForAddress(addressToScan, { before, limit: 1000 });
        if (!sigs.length) break;

        let reachedOld = false;
        for (const s of sigs) {
            if (s.signature === untilSignature) {
                reachedOld = true;
                break;
            }
            signatures.push(s.signature);
        }

        if (reachedOld || sigs.length < 1000) break;
        before = sigs[sigs.length - 1].signature;
        await new Promise(r => setTimeout(r, 1000));
    }

    if (signatures.length === 0) return [];

    console.log(`Found ${signatures.length} new transactions to scan. Processing serially...`);
    const manifests: DecodedManifest[] = [];
    for (let i = 0; i < signatures.length; i++) {
        const sig = signatures[i];
        try {
            const tx = await connection.getTransaction(sig, { commitment: "confirmed", maxSupportedTransactionVersion: 0 });
            if (tx) {
                const seen: Set<string> = new Set();
                const parseBuffer = (buffer: Buffer) => {
                    if (buffer.length < 8) return;
                    const disc = buffer.subarray(0, 8);
                    if (disc.equals(MANIFEST_DISC)) {
                        const decoded = decodeManifestEvent(buffer);
                        if (decoded) {
                            const dedupKey = `${decoded.batchId}-${decoded.provider}`;
                            if (!seen.has(dedupKey)) {
                                seen.add(dedupKey);
                                decoded.signature = sig;
                                decoded.slot = tx.slot || 0;
                                decoded.timestamp = tx.blockTime || Math.floor(Date.now() / 1000);
                                manifests.push(decoded);
                            }
                        }
                    }
                };

                if (tx.meta?.innerInstructions) {
                    for (const inner of tx.meta.innerInstructions) {
                        for (const ix of inner.instructions) {
                            try {
                                const dataStr = (ix as any).data;
                                if (dataStr) parseBuffer(Buffer.from(bs58.decode(dataStr)));
                            } catch (e) { }
                        }
                    }
                }

                if (tx.meta?.logMessages) {
                    for (const log of tx.meta.logMessages) {
                        if (!log.includes("Program data: ")) continue;
                        try {
                            const base64Data = log.split("Program data: ")[1];
                            parseBuffer(Buffer.from(base64Data, "base64"));
                        } catch (e) { }
                    }
                }
            }
        } catch (e: any) {
            console.warn(`   ! Warning on TX ${sig.slice(0, 8)}: ${e.message}`);
        }

        if (i % 5 === 0) {
            console.log(`   - Progress: ${i + 1}/${signatures.length} processed...`);
        }
    }
    return manifests;
}

async function materializeManifestEntries(db: any, m: DecodedManifest, manifest: any) {
    const entries = Array.isArray(manifest.entries) ? manifest.entries : [];
    const batchPointer = manifest.batchPointer || `/v3/batches/${m.batchId}`;
    for (const e of entries) {
        if (e.action === 2) {
            await db.collection("network_events").deleteMany({
                provider: m.provider,
                collectionName: e.collectionName,
                keyHash: e.keyHash,
                secondaryHash: e.secondaryHash,
            });
            continue;
        }
        const doc = {
            keyHash: e.keyHash,
            secondaryHash: e.secondaryHash,
            nameHash: e.nameHash,
            parentHash: e.parentHash,
            parentHash2: e.parentHash2,
            authorHash: e.authorHash,
            geoHash: e.geoHash,
            timestamp: new Date((e.timestamp || m.timestamp || Math.floor(Date.now() / 1000)) * 1000),
            collectionName: e.collectionName,
            collectionId: e.collectionId,
            action: e.action,
            provider: m.provider,
            batchId: m.batchId,
            batchPointer,
            manifestHash: m.manifestHash,
            payloadHash: m.payloadHash,
            txHash: m.signature,
            source: "manifest_v2",
            commitStatus: "committed",
            ei: e.ei,
            updatedAt: new Date(),
        };
        await db.collection("network_events").updateOne(
            { provider: m.provider, batchId: m.batchId, ei: e.ei },
            { $set: doc, $setOnInsert: { status: "pending", createdAt: new Date() } },
            { upsert: true }
        );
    }
    log.info(`Materialized ${entries.length} manifest entries from #${m.batchId} (${m.provider.slice(0, 8)}...)`);
}

async function eagerFetchPayload(db: any, m: DecodedManifest, baseUrl: string, connection: Connection, payer: Keypair) {
    try {
        const { proxyToProvider } = await import("../proxy.js");
        const { syncStateToDb } = await import("../models/borsh-schemas.js");
        const result = await proxyToProvider(
            [{ provider: m.provider, baseUrl }],
            `/v3/batches/${m.batchId}`,
            { provider: m.provider },
            db,
            payer,
            connection
        );
        if (result?.data) {
            const syncResult = await syncStateToDb(db, Buffer.from(JSON.stringify(result.data)));
            const cached = Object.entries(syncResult.collections).filter(([, c]) => (c as number) > 0).map(([n]) => n);
            if (cached.length > 0) {
                await db.collection("network_events").updateMany(
                    { provider: m.provider, batchId: m.batchId, collectionName: { $in: cached } },
                    { $set: { status: "synced", cacheStatus: "auto-fetch-completed", cachedCollections: cached, syncedAt: new Date(), updatedAt: new Date() } }
                );
                log.info(`Auto-fetched payload for manifest #${m.batchId}: ${cached.join(", ")}`);
            }
        }
    } catch (e: any) {
        log.warn(`Auto-fetch payload failed for #${m.batchId}: ${e.message}`);
    }
}

export async function cacheToMongo(db: any, manifests: DecodedManifest[], connection: Connection, programId: PublicKey, localProvider?: string, payer?: Keypair) {
    for (const m of manifests) {
        const isSelf = !!(localProvider && m.provider === localProvider);
        if (isSelf && !SELF_CONSUME) {
            log.info(`   - Skipping self manifest #${m.batchId} from ${m.provider.slice(0, 8)}...`);
            continue;
        }

        let providerDoc = await db.collection("network_providers").findOne({ provider: m.provider });
        if (!providerDoc) {
            await discoverAllProviders(db, connection, programId);
            providerDoc = await db.collection("network_providers").findOne({ provider: m.provider });
        }
        if (!providerDoc?.baseUrl) {
            log.warn(`   - No baseUrl for provider ${m.provider.slice(0, 8)}; skipping manifest #${m.batchId}`);
            continue;
        }
        const baseUrl = providerDoc.baseUrl.replace(/\/$/, "");

        let raw: string;
        try {
            const res = await axios.get(`${baseUrl}${m.manifestPointer}?provider=${m.provider}`, { responseType: "text", transformResponse: (r) => r });
            raw = typeof res.data === "string" ? res.data : JSON.stringify(res.data);
        } catch (e: any) {
            log.warn(`   - Failed to fetch manifest ${m.manifestPointer} from ${baseUrl}: ${e.message}`);
            continue;
        }

        const computed = sha256Hex(raw);
        if (computed !== m.manifestHash) {
            log.warn(`   - Manifest hash mismatch #${m.batchId}: expected ${m.manifestHash}, got ${computed}. Discarding.`);
            continue;
        }

        let manifest: any;
        try {
            manifest = JSON.parse(raw);
        } catch (e: any) {
            log.warn(`   - Manifest JSON parse failed #${m.batchId}: ${e.message}`);
            continue;
        }

        await materializeManifestEntries(db, m, manifest);

        await db.collection("network_providers").updateOne(
            { provider: m.provider },
            { $set: { latestBatchId: m.batchId, batchUpdatedAt: new Date() } }
        );

        if (process.env.AUTO_FETCH_EVENTS === "true" && payer && !isSelf) {
            await eagerFetchPayload(db, m, baseUrl, connection, payer);
        }
    }

    const latest = manifests.reduce((acc: DecodedManifest | null, b) => (!acc || b.slot > acc.slot ? b : acc), null);
    if (latest) {
        await db.collection("network_sync_state").updateOne(
            { _id: "last_scan" as any },
            { $set: { lastScannedAt: new Date(), totalBatches: manifests.length, lastSignature: latest.signature } },
            { upsert: true }
        );
    }
}

export function startRealtimeListener(connection: Connection, programId: PublicKey, db: any, seen: DecodedManifest[], localProvider?: string, payer?: Keypair) {
    log.info(`Realtime: Monitoring program ${programId.toBase58()}... (localProvider: ${localProvider || 'none'})`);
    connection.onLogs(
        programId,
        async (logs) => {
            const dedup: Set<string> = new Set();
            const decodedManifests: DecodedManifest[] = [];
            const parseBuffer = (buffer: Buffer, blockTime: number, txSlot: number) => {
                if (buffer.length < 8) return;
                const disc = buffer.subarray(0, 8);
                if (disc.equals(MANIFEST_DISC)) {
                    const decoded = decodeManifestEvent(buffer);
                    if (decoded) {
                        const dedupKey = `${decoded.batchId}-${decoded.provider}`;
                        if (!dedup.has(dedupKey)) {
                            dedup.add(dedupKey);
                            log.info(`Realtime: Processing manifest #${decoded.batchId} from ${decoded.provider.slice(0, 8)}... (${decoded.entryCount} entries)`);
                            decoded.signature = logs.signature;
                            decoded.slot = txSlot;
                            decoded.timestamp = blockTime || Math.floor(Date.now() / 1000);
                            decodedManifests.push(decoded);
                        }
                    }
                }
            };

            const extractedBuffers: Buffer[] = [];
            let hasManifestInstruction = false;
            let hasGenesisChange = false;

            for (const log of logs.logs) {
                if (log.includes("Instruction: SyncIndexManifest")) hasManifestInstruction = true;
                if (log.includes("Instruction: GrantGenesis") || log.includes("Instruction: RevokeGenesis")) hasGenesisChange = true;
                if (!log.includes("Program data: ")) continue;
                try {
                    const base64Data = log.split("Program data: ")[1];
                    const buffer = Buffer.from(base64Data, "base64");
                    if (buffer.subarray(0, 8).equals(MANIFEST_DISC)) extractedBuffers.push(buffer);
                } catch (e) { }
            }

            if (hasGenesisChange) {
                log.info(`Realtime: Detected genesis grant/revoke instruction in ${logs.signature.slice(0, 8)} - refreshing providers`);
                discoverAllProviders(db, connection, programId).catch(e => log.error(`Provider refresh failed: ${e.message}`));
            }

            if (hasManifestInstruction || extractedBuffers.length > 0) {
                const tx = await connection.getTransaction(logs.signature, { commitment: "confirmed", maxSupportedTransactionVersion: 0 });
                const blockTime = tx?.blockTime || Math.floor(Date.now() / 1000);
                const slot = tx?.slot || 0;

                for (const buf of extractedBuffers) parseBuffer(buf, blockTime, slot);

                if (tx?.meta?.innerInstructions) {
                    for (const inner of tx.meta.innerInstructions) {
                        for (const ix of inner.instructions) {
                            try {
                                const dataStr = (ix as any).data;
                                if (dataStr) parseBuffer(Buffer.from(bs58.decode(dataStr)), blockTime, slot);
                            } catch (e) { }
                        }
                    }
                }

                if (decodedManifests.length > 0) {
                    cacheToMongo(db, decodedManifests, connection, programId, localProvider, payer).then(() => {
                        log.info(`Realtime: Successfully indexed ${decodedManifests.length} manifest(s) from ${logs.signature.slice(0, 8)}`);
                        seen.push(...decodedManifests);
                    }).catch(e => log.error(`Realtime: Failed to index ${logs.signature.slice(0, 8)}: ${e?.message || e}`));
                }
            }
        },
        "confirmed"
    );
}

export async function ensureNetworkIndexes(db: any) {
    await db.collection("network_events").createIndex({ collectionName: 1, status: 1, keyHash: 1 }).catch(() => {});
    await db.collection("network_events").createIndex({ collectionName: 1, status: 1, secondaryHash: 1 }).catch(() => {});
    await db.collection("network_events").createIndex({ collectionName: 1, status: 1, authorHash: 1 }).catch(() => {});
    await db.collection("network_events").createIndex({ collectionName: 1, status: 1, nameHash: 1 }).catch(() => {});
    await db.collection("network_events").createIndex({ collectionName: 1, status: 1, parentHash: 1 }).catch(() => {});
    await db.collection("network_events").createIndex({ collectionName: 1, status: 1, geoHash: 1 }).catch(() => {});
    await db.collection("network_events").createIndex(
        { provider: 1, batchId: 1, ei: 1 },
        { unique: true, partialFilterExpression: { ei: { $exists: true } } }
    ).catch(() => {});
}

