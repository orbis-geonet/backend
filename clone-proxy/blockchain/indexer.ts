import { Connection, Keypair, PublicKey } from "@solana/web3.js";
import bs58 from "bs58";
import { BATCH_DISC, decodeBatchEvent, DecodedBatch } from "./decoder.js";
import { COLLECTION_ORDER } from "../config.js";
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
        console.log(`Discovered ${upserted} clones from on-chain CloneInfo PDAs`);
    } catch (e: any) {
        console.error(`discoverAllProviders failed: ${e.message}`);
    }
}

export async function scanHistory(connection: Connection, addressToScan: PublicKey, untilSignature?: string): Promise<DecodedBatch[]> {
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
    const batches: DecodedBatch[] = [];
    for (let i = 0; i < signatures.length; i++) {
        const sig = signatures[i];
        try {
            const tx = await connection.getTransaction(sig, { commitment: "confirmed", maxSupportedTransactionVersion: 0 });
            if (tx) {
                const batchesInTx: Set<string> = new Set();
                const parseBuffer = (buffer: Buffer) => {
                    if (buffer.length < 8) return;
                    const disc = buffer.subarray(0, 8);
                    if (disc.equals(BATCH_DISC)) {
                        const decoded = decodeBatchEvent(buffer);
                        if (decoded) {
                            const dedupKey = `${decoded.batchId}-${decoded.provider}`;
                            if (!batchesInTx.has(dedupKey)) {
                                batchesInTx.add(dedupKey);
                                decoded.signature = sig;
                                decoded.slot = tx.slot || 0;
                                // Use on-chain blockTime as the source of truth for the batch
                                decoded.timestamp = tx.blockTime || Math.floor(Date.now() / 1000);
                                batches.push(decoded);
                            }
                        }
                    }
                };

                if (tx.meta?.innerInstructions) {
                    for (const inner of tx.meta.innerInstructions) {
                        for (const ix of inner.instructions) {
                            try {
                                const dataStr = (ix as any).data;
                                if (dataStr) {
                                    const buffer = Buffer.from(bs58.decode(dataStr));
                                    parseBuffer(buffer);
                                }
                            } catch (e) { }
                        }
                    }
                }

                if (tx.transaction?.message) {
                    const compiledInstructions = tx.transaction.message.compiledInstructions || (tx.transaction.message as any).instructions;
                    if (compiledInstructions) {
                        for (const ix of compiledInstructions as any[]) {
                            try {
                                const dataStr = ix.data;
                                if (dataStr) {
                                    const buffer = Buffer.from(bs58.decode(dataStr));
                                    parseBuffer(buffer);
                                }
                            } catch (e) { }
                        }
                    }
                }

                if (tx.meta?.logMessages) {
                    for (const log of tx.meta.logMessages) {
                        if (!log.includes("Program data: ")) continue;
                        try {
                            const base64Data = log.split("Program data: ")[1];
                            const buffer = Buffer.from(base64Data, "base64");
                            parseBuffer(buffer);
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
    return batches;
}


export async function cacheToMongo(db: any, batches: DecodedBatch[], connection: Connection, programId: PublicKey, localProvider?: string, payer?: Keypair) {
    const byProvider: Record<string, DecodedBatch[]> = {};
    for (const b of batches) {
        byProvider[b.provider] = byProvider[b.provider] || [];
        byProvider[b.provider].push(b);
    }
    for (const provider in byProvider) {
        if (localProvider && provider === localProvider) {
            console.log(`   - Skipping historical self-batches from ${provider.slice(0, 8)}...`);
            continue;
        }
        const sorted = byProvider[provider].sort((a, b) => a.batchId - b.batchId);
        const collectionRoots: Record<string, string> = {};
        const collectionEndpoints: Record<string, string> = {};

        for (const batch of sorted) {
            for (const root of batch.collectionRoots) {
                collectionRoots[root.collectionName] = root.root;
            }
            if (batch.batchPointer && batch.batchPointer.includes("=")) {
                const pairs = batch.batchPointer.split("|");
                for (const pair of pairs) {
                    const [idStr, path] = pair.split("=");
                    const id = parseInt(idStr);
                    const name = COLLECTION_ORDER[id];
                    if (name && path) collectionEndpoints[name] = path;
                }
            }
        }

        const latestBatch = sorted[sorted.length - 1];
        const providerDoc = await db.collection("network_providers").findOne({ provider });
        if (!providerDoc) {
            console.log(`   - Skipping batch fields for ${provider.slice(0, 8)}: provider not yet discovered from on-chain PDAs`);
            continue;
        }
        const nextCollectionRoots = { ...(providerDoc.collectionRoots || {}), ...collectionRoots };
        const nextCollectionEndpoints = { ...(providerDoc.collectionEndpoints || {}), ...collectionEndpoints };
        await db.collection("network_providers").updateOne(
            { provider },
            { $set: { collectionRoots: nextCollectionRoots, collectionEndpoints: nextCollectionEndpoints, latestBatchId: latestBatch.batchId, batchUpdatedAt: new Date() } }
        );
        const baseUrl = providerDoc.baseUrl || null;

        const allEvents = sorted.flatMap((b) => b.events.map(e => ({
            ...e,
            txHash: b.signature,
            onChainTime: b.timestamp,
            batchId: b.batchId,
            batchPointer: b.batchPointer,
        })));
        for (const event of allEvents) {
            const filter: any = {
                txHash: event.txHash,
                provider: provider,
                collectionName: event.collectionName
            };
            if (event.keyHash && event.keyHash !== "0000000000000000") {
                filter.keyHash = event.keyHash;
            } else if (event.secondaryHash && event.secondaryHash !== "00000000") {
                filter.secondaryHash = event.secondaryHash;
            } else if (event.nameHash && event.nameHash !== "00000000") {
                filter.nameHash = event.nameHash;
            } else {
                continue;
            }
            const doc = {
                keyHash: event.keyHash,
                secondaryHash: event.secondaryHash,
                nameHash: event.nameHash,
                parentHash: event.parentHash,
                parentHash2: event.parentHash2,
                authorHash: event.authorHash,
                geoHash: event.geoHash,
                timestamp: new Date((event.onChainTime || event.timestamp || Math.floor(Date.now() / 1000)) * 1000),
                txHash: event.txHash,
                batchId: event.batchId,
                batchPointer: event.batchPointer,
                action: event.action,
                collectionName: event.collectionName,
                collectionId: event.collectionId,
                provider,
            };
            await db.collection("network_events").updateOne(filter, { $set: doc, $setOnInsert: { status: "pending" } }, { upsert: true });
        }

        const localInfo = payer ? await fetchCloneInfo(connection, payer.publicKey, programId) : null;
        const localBaseUrl = localInfo?.baseUrl || null;
        const isSelfUrl = baseUrl && localBaseUrl && baseUrl.replace(/\/$/, "") === localBaseUrl.replace(/\/$/, "");
        /*
        if (isSelfUrl) {
            console.log(`   - Skipping auto-fetch for ${provider.slice(0, 8)}: registered baseUrl matches local node URL`);
        }
        */
        if (process.env.AUTO_FETCH_EVENTS === "true" && baseUrl && payer && !isSelfUrl) {
            const { proxyToProvider } = await import("../proxy.js");
            const { syncStateToDb } = await import("../models/borsh-schemas.js");
            for (const batch of sorted) {
                if (!batch.batchPointer) continue;
                try {
                    const result = await proxyToProvider(
                        [{ provider, baseUrl: baseUrl! }],
                        batch.batchPointer,
                        { provider },
                        db,
                        payer!,
                        connection
                    );
                    if (result?.data) {
                        const syncResult = await syncStateToDb(db, Buffer.from(JSON.stringify(result.data)));
                        const cachedCollections = Object.entries(syncResult.collections)
                            .filter(([, count]) => count > 0)
                            .map(([collectionName]) => collectionName);

                        if (cachedCollections.length === 0) {
                            await db.collection("network_events").updateMany(
                                { provider, txHash: batch.signature },
                                {
                                    $set: {
                                        cacheStatus: "failed",
                                        cacheError: "Auto-fetch returned no cacheable documents",
                                        updatedAt: new Date(),
                                    }
                                }
                            );
                            console.warn(`   - Batch pointer auto-fetch returned no cacheable documents for ${batch.batchPointer}; leaving events pending.`);
                            continue;
                        }

                        console.log(`   - Batch pointer auto-fetch cached collections for ${batch.batchPointer}: ${cachedCollections.map(name => `${name}=${syncResult.collections[name]}`).join(", ")}`);
                        await db.collection("network_events").updateMany(
                            { provider, txHash: batch.signature, collectionName: { $in: cachedCollections } },
                            {
                                $set: {
                                    status: "synced",
                                    cacheStatus: "auto-fetch-completed",
                                    cachedCollections,
                                    syncedAt: new Date(),
                                    updatedAt: new Date(),
                                },
                                $unset: { cacheError: "" }
                            }
                        );

                        const eventCollections = [...new Set(batch.events.map(event => event.collectionName).filter(Boolean))];
                        const missingCollections = eventCollections.filter(collectionName => !cachedCollections.includes(collectionName));
                        if (missingCollections.length > 0) {
                            await db.collection("network_events").updateMany(
                                { provider, txHash: batch.signature, collectionName: { $in: missingCollections } },
                                {
                                    $set: {
                                        cacheStatus: "missing-batch-payload",
                                        cacheError: `Auto-fetch payload did not include cacheable docs for: ${missingCollections.join(", ")}`,
                                        updatedAt: new Date(),
                                    }
                                }
                            );
                            console.warn(`   - Batch pointer auto-fetch did not cache ${missingCollections.join(", ")} for ${batch.batchPointer}; leaving those events pending.`);
                        }
                    }
                } catch (e: any) {
                    console.warn(`   - Batch pointer auto-fetch failed for ${batch.batchPointer}: ${e.message}`);
                }
            }
        }
    }

    const latestSig = batches.reduce((latest, b) => (!latest || b.slot > latest.slot ? b : latest), batches[0]);
    if (latestSig) {
        await db.collection("network_sync_state").updateOne(
            { _id: "last_scan" as any },
            { $set: { lastScannedAt: new Date(), totalBatches: batches.length, lastSignature: latestSig.signature } },
            { upsert: true }
        );
    }
}

export function startRealtimeListener(connection: Connection, programId: PublicKey, db: any, batches: DecodedBatch[], localProvider?: string, payer?: Keypair) {
    console.log(`Realtime: Monitoring program ${programId.toBase58()}... (localProvider: ${localProvider || 'none'})`);
    connection.onLogs(
        programId,
        async (logs) => {
            const batchesInTx: Set<string> = new Set();
            const decodedBatches: DecodedBatch[] = [];
            const parseBuffer = (buffer: Buffer, blockTime: number, txSlot: number) => {
                if (buffer.length < 8) return;
                const disc = buffer.subarray(0, 8);

                if (disc.equals(BATCH_DISC)) {
                    const decoded = decodeBatchEvent(buffer);
                    if (decoded) {
                        const dedupKey = `${decoded.batchId}-${decoded.provider}`;
                        if (!batchesInTx.has(dedupKey)) {
                            batchesInTx.add(dedupKey);
                            if (localProvider && decoded.provider === localProvider) {
                                console.log(`Realtime: Skipping self-batch #${decoded.batchId} from ${decoded.provider.slice(0, 8)}...`);
                                return;
                            }

                            console.log(`Realtime: Processing batch #${decoded.batchId} from ${decoded.provider.slice(0, 8)}... (${decoded.events.length} events)`);
                            decoded.signature = logs.signature;
                            decoded.slot = txSlot;
                            decoded.timestamp = blockTime || Math.floor(Date.now() / 1000);
                            decodedBatches.push(decoded);
                        }
                    }
                }
            };

            const extractedBuffers: Buffer[] = [];
            let hasBatchInstruction = false;
            let hasGenesisChange = false;

            for (const log of logs.logs) {
                if (log.includes("Instruction: SyncCollectionBatch")) {
                    hasBatchInstruction = true;
                }
                if (log.includes("Instruction: GrantGenesis") || log.includes("Instruction: RevokeGenesis")) {
                    hasGenesisChange = true;
                }
                if (!log.includes("Program data: ")) continue;
                try {
                    const base64Data = log.split("Program data: ")[1];
                    const buffer = Buffer.from(base64Data, "base64");
                    const disc = buffer.subarray(0, 8);

                    if (disc.equals(BATCH_DISC)) {
                        extractedBuffers.push(buffer);
                    }
                } catch (e) { }
            }

            if (hasGenesisChange) {
                console.log(`Realtime: Detected genesis grant/revoke instruction in ${logs.signature.slice(0, 8)} - refreshing providers`);
                discoverAllProviders(db, connection, programId).catch(e => console.error(`Provider refresh failed: ${e.message}`));
            }

            if (hasBatchInstruction || extractedBuffers.length > 0) {
                const tx = await connection.getTransaction(logs.signature, { commitment: "confirmed", maxSupportedTransactionVersion: 0 });
                const blockTime = tx?.blockTime || Math.floor(Date.now() / 1000);
                const slot = tx?.slot || 0;

                // 1. Process buffers found in logs
                for (const buf of extractedBuffers) {
                    parseBuffer(buf, blockTime, slot);
                }

                // 2. Process buffers found in internal instructions (fall back)
                if (tx?.meta?.innerInstructions) {
                    for (const inner of tx.meta.innerInstructions) {
                        for (const ix of inner.instructions) {
                            try {
                                const dataStr = (ix as any).data;
                                if (dataStr) {
                                    const buffer = Buffer.from(bs58.decode(dataStr));
                                    parseBuffer(buffer, blockTime, slot);
                                }
                            } catch (e) { }
                        }
                    }
                }

                if (decodedBatches.length > 0) {
                    cacheToMongo(db, decodedBatches, connection, programId, localProvider, payer).then(() => {
                        console.log(`Realtime: Successfully indexed ${decodedBatches.length} batch(es) from ${logs.signature.slice(0, 8)}`);
                        batches.push(...decodedBatches);
                    }).catch(e => console.error(`Realtime: Failed to index ${logs.signature.slice(0, 8)}:`, e));
                }
            }
        },
        "confirmed"
    );
}

export function buildProviderLookup(batches: DecodedBatch[]) {
    const byCollection: Record<string, Record<string, Set<string>>> = {};
    for (const batch of batches) {
        const provider = batch.provider;
        for (const event of batch.events) {
            const coll = event.collectionName;
            if (!byCollection[coll]) byCollection[coll] = {};
            const addHash = (hname: string, hval: string) => {
                if (!hval || hval === "00000000" || hval === "0000000000000000") return;
                if (!byCollection[coll][hname]) byCollection[coll][hname] = new Set();
                byCollection[coll][hname].add(`${hval}|${provider}`);
            };
            addHash("keyHash", event.keyHash);
            addHash("secondaryHash", event.secondaryHash);
            addHash("nameHash", event.nameHash);
            addHash("parentHash", event.parentHash);
            addHash("parentHash2", event.parentHash2);
            addHash("authorHash", event.authorHash);
            addHash("geoHash", event.geoHash);
        }
    }
    return byCollection;
}
