import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey, Transaction } from "@solana/web3.js";
import { getOrCreateAssociatedTokenAccount, TOKEN_PROGRAM_ID } from "@solana/spl-token";
import { SPL_ACCOUNT_COMPRESSION_PROGRAM_ID, SPL_NOOP_PROGRAM_ID } from "@solana/spl-account-compression";
import { Db, ChangeStreamDocument } from "mongodb";
import { BN } from "bn.js";
import { readFileSync } from "node:fs";
import {
    COLLECTION_ORDER,
    COLLECTION_META,
    PROGRAM_ID,
    laneForCollection,
    MANIFEST_TUNABLES,
    Lane,
} from "../config.js";
import {
    buildEntryHashes,
    zeroHashes,
    makeEntry,
    searchableHashesChanged,
    docTimestampSeconds,
    canonicalStringify,
    sha256Hex,
    ManifestEntry,
    EntryHashes,
} from "../network/manifest.js";

const MONGO_WATCH_RESTART_MS = parsePositiveInt(process.env.MONGO_WATCH_RESTART_MS, 30_000);

function parsePositiveInt(value: string | undefined, fallback: number): number {
    if (!value) return fallback;
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function logPrefix(): string {
    return `[${new Date().toISOString()}]`;
}

function logInfo(message: string) {
    console.log(`${logPrefix()} [INFO] ${message}`);
}

function logWarn(message: string) {
    console.warn(`${logPrefix()} [WARN] ${message}`);
}

function logError(message: string, detail?: any) {
    if (detail === undefined) {
        console.error(`${logPrefix()} [ERROR] ${message}`);
        return;
    }
    console.error(`${logPrefix()} [ERROR] ${message}`, detail);
}

function getErrorMessage(err: any): string {
    return err?.message || String(err);
}

function actionFromOpType(opType: string): number {
    switch (opType) {
        case "insert": return 0;
        case "update": return 1;
        case "replace": return 1;
        case "delete": return 2;
        default: return 1;
    }
}

function pickHashes(source: any): EntryHashes {
    return {
        keyHash: source.keyHash,
        secondaryHash: source.secondaryHash,
        nameHash: source.nameHash,
        parentHash: source.parentHash,
        parentHash2: source.parentHash2,
        authorHash: source.authorHash,
        geoHash: source.geoHash,
    };
}

export async function startBatchPushService(db: Db, connection: Connection, payer: Keypair) {
    try {
        const hello = await db.command({ hello: 1 });
        const isReplicaSet = !!hello.setName || !!hello.isWritablePrimary;
        if (!isReplicaSet) {
            throw new Error("REPLICA_SET_REQUIRED: Change Streams (live collections watch) require a MongoDB Replica Set.");
        }
    } catch (e: any) {
        if (e.message.includes("REPLICA_SET_REQUIRED")) throw e;
        try {
            const isMaster = await db.command({ isMaster: 1 });
            if (!isMaster.setName) {
                throw new Error("REPLICA_SET_REQUIRED: Change Streams (live collections watch) require a MongoDB Replica Set.");
            }
        } catch (e2: any) {
            throw new Error("REPLICA_SET_REQUIRED: Change Streams require a MongoDB Replica Set. LIVE WATCHING DISABLED.");
        }
    }

    const wallet = new anchor.Wallet(payer);
    const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });

    const idl = JSON.parse(readFileSync(new URL("../programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));
    const program = new anchor.Program(idl as any, anchorProvider);

    const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], PROGRAM_ID);
    const config: any = await program.account.globalConfig.fetch(configPda);

    const [cloneInfoPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("clone_info"), payer.publicKey.toBuffer()],
        PROGRAM_ID
    );

    const userAta = await getOrCreateAssociatedTokenAccount(connection, payer, config.orbisMint, payer.publicKey);
    const treasuryAta = await getOrCreateAssociatedTokenAccount(connection, payer, config.orbisMint, config.treasury);

    const provider = payer.publicKey.toBase58();

    await db.collection("network_outbox").createIndex({ lane: 1, status: 1, seq: 1 }).catch(() => {});
    await db.collection("network_outbox").createIndex({ collection: 1, primaryKey: 1, status: 1 }).catch(() => {});
    await db.collection("network_index_batches").createIndex({ provider: 1, batchId: 1 }, { unique: true }).catch(() => {});

    async function nextSeq(): Promise<number> {
        const r: any = await db.collection("network_counters").findOneAndUpdate(
            { _id: "outbox_seq" as any },
            { $inc: { counter: 1 } },
            { upsert: true, returnDocument: "after" }
        );
        const doc = r && typeof r.value === "object" && r.value !== null ? r.value : r;
        return doc?.counter ?? 1;
    }

    async function nextBatchId(): Promise<number> {
        const r: any = await db.collection("batch_tracker").findOneAndUpdate(
            { _id: "current" as any },
            { $inc: { batchId: 1 }, $set: { lastPush: new Date() } },
            { upsert: true, returnDocument: "after" }
        );
        const doc = r && typeof r.value === "object" && r.value !== null ? r.value : r;
        return doc?.batchId ?? 1;
    }

    async function enqueue(colName: string, doc: any, action: number) {
        const meta = COLLECTION_META[colName];
        const lane = laneForCollection(colName);
        if (!meta || !lane) return;
        const primaryKeyValue = doc?.[meta.primaryKey];
        if (primaryKeyValue == null) return;
        const primaryKey = String(primaryKeyValue);
        const now = new Date();
        const seq = await nextSeq();
        await db.collection("network_outbox").updateOne(
            { collection: colName, primaryKey, status: "queued" },
            {
                $set: {
                    action,
                    lane,
                    networkActionId: doc?.networkActionId ?? null,
                    changedAt: now,
                    seq,
                },
                $setOnInsert: {
                    collection: colName,
                    primaryKey,
                    status: "queued",
                    createdAt: now,
                },
            },
            { upsert: true }
        );
    }

    const flushing: Record<Lane, boolean> = { public: false, derived: false };

    function laneCountTrigger(lane: Lane): number {
        return lane === "public" ? MANIFEST_TUNABLES.publicFlushCount : MANIFEST_TUNABLES.derivedFlushCount;
    }

    function laneAgeTrigger(lane: Lane): number {
        return lane === "public" ? MANIFEST_TUNABLES.publicFlushAgeMs : MANIFEST_TUNABLES.derivedFlushAgeMs;
    }

    async function sendManifestTx(
        batchId: number,
        manifestPointer: string,
        manifestHash: string,
        payloadHash: string,
        entryCount: number,
        actionCount: number,
        fromTs: number,
        toTs: number
    ): Promise<string> {
        const ix = await program.methods
            .syncIndexManifest(
                2,
                batchId,
                Array.from(Buffer.from(manifestHash, "hex")),
                Array.from(Buffer.from(payloadHash, "hex")),
                manifestPointer,
                entryCount,
                actionCount,
                new BN(fromTs),
                new BN(toTs)
            )
            .accounts({
                merkleTree: config.merkleTree,
                config: configPda,
                userTokenAccount: userAta.address,
                treasuryTokenAccount: treasuryAta.address,
                orbisMint: config.orbisMint,
                cloneSigner: payer.publicKey,
                cloneInfo: cloneInfoPda,
                compressionProgram: SPL_ACCOUNT_COMPRESSION_PROGRAM_ID,
                noopProgram: SPL_NOOP_PROGRAM_ID,
                tokenProgram: TOKEN_PROGRAM_ID,
            } as any)
            .instruction();

        const latestBlockhash = await connection.getLatestBlockhash("confirmed");
        const tx = new Transaction().add(ix);
        tx.feePayer = payer.publicKey;
        tx.recentBlockhash = latestBlockhash.blockhash;
        tx.sign(payer);

        const txHash = await connection.sendRawTransaction(tx.serialize(), { preflightCommitment: "confirmed" });
        const confirmation = await connection.confirmTransaction(
            {
                signature: txHash,
                blockhash: latestBlockhash.blockhash,
                lastValidBlockHeight: latestBlockhash.lastValidBlockHeight,
            },
            "confirmed"
        );
        if (confirmation.value.err) {
            const confirmationError = typeof confirmation.value.err === "string" ? confirmation.value.err : JSON.stringify(confirmation.value.err);
            throw new Error(`Transaction confirmation failed: ${confirmationError}`);
        }
        return txHash;
    }

    async function flushLane(lane: Lane) {
        if (flushing[lane]) return;
        flushing[lane] = true;
        try {
            const rows = await db.collection("network_outbox")
                .find({ lane, status: "queued" })
                .sort({ seq: 1 })
                .limit(laneCountTrigger(lane) * 4)
                .toArray();
            if (rows.length === 0) return;

            const entries: ManifestEntry[] = [];
            const payload: Record<string, any[]> = {};
            const processedIds: any[] = [];
            const actionIds = new Set<string>();
            let ei = 0;
            let fromTs = Number.MAX_SAFE_INTEGER;
            let toTs = 0;

            for (const row of rows) {
                processedIds.push(row._id);
                if (row.networkActionId) actionIds.add(String(row.networkActionId));
                const changedSec = Math.floor(new Date(row.changedAt || row.createdAt).getTime() / 1000);
                fromTs = Math.min(fromTs, changedSec);
                toTs = Math.max(toTs, changedSec);

                const meta = COLLECTION_META[row.collection];
                if (!meta) continue;
                const stateId = `${row.collection}:${row.primaryKey}`;
                const prevState = await db.collection("network_index_state").findOne({ _id: stateId as any });

                if (row.action === 2) {
                    const hashes = prevState ? pickHashes(prevState) : zeroHashes(row.primaryKey);
                    entries.push(makeEntry(ei++, row.collection, 2, hashes, changedSec));
                    await db.collection("network_index_state").deleteOne({ _id: stateId as any });
                    continue;
                }

                const doc = await db.collection(row.collection).findOne({ [meta.primaryKey]: row.primaryKey });
                if (!doc) continue;
                const hashes = buildEntryHashes(row.collection, doc);
                const ts = docTimestampSeconds(doc);

                if (prevState) {
                    const prevHashes = pickHashes(prevState);
                    if (searchableHashesChanged(prevHashes, hashes)) {
                        entries.push(makeEntry(ei++, row.collection, 2, prevHashes, ts));
                    }
                }

                entries.push(makeEntry(ei++, row.collection, row.action, hashes, ts));
                await db.collection("network_index_state").updateOne(
                    { _id: stateId as any },
                    {
                        $set: {
                            collection: row.collection,
                            primaryKey: row.primaryKey,
                            ...hashes,
                            updatedAt: new Date(),
                        },
                    },
                    { upsert: true }
                );

                const { _id, ...clean } = doc;
                payload[row.collection] = payload[row.collection] || [];
                payload[row.collection].push(clean);
            }

            if (entries.length === 0) {
                if (processedIds.length > 0) {
                    await db.collection("network_outbox").updateMany(
                        { _id: { $in: processedIds } },
                        { $set: { status: "batched", batchedAt: new Date(), batchId: null } }
                    );
                }
                return;
            }

            if (fromTs === Number.MAX_SAFE_INTEGER) fromTs = toTs;

            const batchId = await nextBatchId();
            const manifestPointer = `/v3/index-batches/${batchId}`;
            const batchPointer = `/v3/batches/${batchId}`;
            const payloadString = canonicalStringify(payload);
            const payloadHash = sha256Hex(payloadString);
            const actionCount = actionIds.size > 0 ? actionIds.size : entries.length;

            const manifestObj = {
                provider,
                batchId,
                lane,
                fromTs,
                toTs,
                entryCount: entries.length,
                actionCount,
                payloadHash,
                batchPointer,
                entries,
            };
            const manifestJson = canonicalStringify(manifestObj);
            const manifestHash = sha256Hex(manifestJson);

            await db.collection("network_batches").updateOne(
                { provider, batchId },
                {
                    $set: {
                        provider,
                        batchId,
                        batchPointer,
                        payload,
                        payloadHash,
                        status: "pending",
                        updatedAt: new Date(),
                    },
                    $setOnInsert: { createdAt: new Date() },
                },
                { upsert: true }
            );

            await db.collection("network_index_batches").updateOne(
                { provider, batchId },
                {
                    $set: {
                        provider,
                        batchId,
                        lane,
                        manifestPointer,
                        manifestHash,
                        payloadHash,
                        entryCount: entries.length,
                        actionCount,
                        fromTs,
                        toTs,
                        manifestJson,
                        status: "committing",
                        updatedAt: new Date(),
                    },
                    $setOnInsert: { createdAt: new Date() },
                },
                { upsert: true }
            );

            logInfo(`Committing manifest #${batchId} lane=${lane}: ${entries.length} entries, ${actionCount} actions, payload ${Object.keys(payload).length} collections`);

            try {
                const txHash = await sendManifestTx(batchId, manifestPointer, manifestHash, payloadHash, entries.length, actionCount, fromTs, toTs);
                logInfo(`Manifest #${batchId} committed on-chain: ${txHash}`);

                await db.collection("network_index_batches").updateOne(
                    { provider, batchId },
                    { $set: { status: "committed", txHash, committedAt: new Date(), updatedAt: new Date() } }
                );
                await db.collection("network_batches").updateOne(
                    { provider, batchId },
                    { $set: { status: "published", txHash, publishedAt: new Date(), updatedAt: new Date() } }
                );
                await db.collection("network_outbox").updateMany(
                    { _id: { $in: processedIds } },
                    { $set: { status: "batched", batchedAt: new Date(), batchId } }
                );
            } catch (err: any) {
                const message = getErrorMessage(err);
                logError(`Manifest #${batchId} commit failed; leaving outbox rows queued for retry`, message);
                await db.collection("network_index_batches").updateOne(
                    { provider, batchId },
                    { $set: { status: "failed", error: message, updatedAt: new Date() } }
                );
            }
        } catch (err: any) {
            logError(`flushLane(${lane}) fatal error`, getErrorMessage(err));
        } finally {
            flushing[lane] = false;
        }
    }

    async function maybeFlush(lane: Lane) {
        if (flushing[lane]) return;
        const count = await db.collection("network_outbox").countDocuments({ lane, status: "queued" });
        if (count === 0) return;
        const oldest = await db.collection("network_outbox").find({ lane, status: "queued" }).sort({ createdAt: 1 }).limit(1).next();
        const ageMs = oldest ? Date.now() - new Date(oldest.createdAt).getTime() : 0;
        if (count >= laneCountTrigger(lane) || ageMs >= laneAgeTrigger(lane)) {
            await flushLane(lane);
        }
    }

    const flushTimer = setInterval(() => {
        maybeFlush("public").catch(err => logError("maybeFlush(public) failed", getErrorMessage(err)));
        maybeFlush("derived").catch(err => logError("maybeFlush(derived) failed", getErrorMessage(err)));
    }, MANIFEST_TUNABLES.flushTickMs);

    const watchCollections = COLLECTION_ORDER.filter(name => laneForCollection(name) !== null);
    const activeChangeStreams = new Set<any>();
    const restartTimers = new Set<NodeJS.Timeout>();
    let shuttingDown = false;

    logInfo(`Manifest producer: watching ${watchCollections.length} collections across public/derived lanes...`);

    async function handleChange(colName: string, change: ChangeStreamDocument) {
        const updateDesc = (change as any).updateDescription;
        const updatedFields = updateDesc?.updatedFields || {};
        const removedFields = updateDesc?.removedFields || [];

        const internalFields = ["_h", "_remote", "_kh"];
        const changedKeys = Object.keys(updatedFields);
        const isPurelyInternal = (changedKeys.length > 0 && changedKeys.every(k => internalFields.includes(k)))
            || (removedFields.length === 1 && removedFields[0] === "_remote");
        if (isPurelyInternal) return;

        const doc = (change as any).fullDocument || (change as any).documentKey;
        if (!doc) return;
        if (doc && doc._remote === true) return;

        const validOps = ["insert", "update", "replace", "delete"];
        if (!validOps.includes(change.operationType)) return;

        const action = actionFromOpType(change.operationType);
        await enqueue(colName, doc, action);

        const lane = laneForCollection(colName);
        const meta = COLLECTION_META[colName];
        const primaryKey = meta ? doc?.[meta.primaryKey] : undefined;
        const verb = change.operationType === "insert" ? "inserted" : change.operationType === "delete" ? "deleted" : "updated";
        logInfo(`Mongo change: ${colName} ${verb} (key ${primaryKey ?? "?"}) — 1 row queued to ${lane ?? "unwatched"} lane`);
        if (lane) {
            maybeFlush(lane).catch(err => logError(`maybeFlush(${lane}) failed`, getErrorMessage(err)));
        }
    }

    function watchCollection(colName: string) {
        const collection = db.collection(colName);
        const changeStream = collection.watch([], { fullDocument: "updateLookup" });
        activeChangeStreams.add(changeStream);
        let restartScheduled = false;

        changeStream.on("change", (change: ChangeStreamDocument) => {
            handleChange(colName, change).catch((err: any) => {
                logError(`Change stream handler failed for '${colName}'`, getErrorMessage(err));
            });
        });

        const scheduleRestart = (reason: string, err?: any) => {
            activeChangeStreams.delete(changeStream);
            if (restartScheduled || shuttingDown) return;
            restartScheduled = true;
            const details = err ? `: ${getErrorMessage(err)}` : "";
            logWarn(`Change stream for '${colName}' ${reason}${details}. Restarting watcher in ${MONGO_WATCH_RESTART_MS}ms.`);
            void changeStream.close().catch((closeErr: any) => {
                logWarn(`Failed to close change stream for '${colName}': ${getErrorMessage(closeErr)}`);
            });
            const timer = setTimeout(() => {
                restartTimers.delete(timer);
                if (!shuttingDown) watchCollection(colName);
            }, MONGO_WATCH_RESTART_MS);
            restartTimers.add(timer);
        };

        changeStream.on("error", (err: any) => {
            scheduleRestart("errored", err);
        });

        changeStream.on("close", () => {
            scheduleRestart("closed");
        });
    }

    for (const colName of watchCollections) {
        watchCollection(colName);
    }

    process.on("SIGINT", async () => {
        shuttingDown = true;
        for (const timer of restartTimers) clearTimeout(timer);
        restartTimers.clear();
        clearInterval(flushTimer);
        await Promise.allSettled([...activeChangeStreams].map((changeStream: any) => changeStream.close()));
        await flushLane("public");
        await flushLane("derived");
    });
}
