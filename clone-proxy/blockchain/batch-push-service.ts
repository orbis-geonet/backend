import * as anchor from "@coral-xyz/anchor";
import { Connection, Keypair, PublicKey, Transaction } from "@solana/web3.js";
import { getOrCreateAssociatedTokenAccount, TOKEN_PROGRAM_ID } from "@solana/spl-token";
import { SPL_ACCOUNT_COMPRESSION_PROGRAM_ID, SPL_NOOP_PROGRAM_ID } from "@solana/spl-account-compression";
import { Db, ChangeStreamDocument } from "mongodb";
import { createHash } from "crypto";
import { COLLECTION_ORDER, PROGRAM_ID } from "../config.js";
import { readFileSync } from "node:fs";

const COLLECTION_ID_MAP = new Map(COLLECTION_ORDER.map((name, idx) => [name, idx]));

interface CollectionMeta {
    primaryKey: string;
    secondaryKey?: string;
    nameField?: string;
    parentKey?: string;
    parentKey2?: string;
    authorField?: string;
    geoField?: string;
}

const COLLECTION_META: Record<string, CollectionMeta> = {
    'users': { primaryKey: 'userKey', secondaryKey: 'slug', nameField: 'displayName', authorField: 'email', geoField: 'coordinates' },
    'groups': { primaryKey: 'groupKey', secondaryKey: 'slug', nameField: 'name', geoField: 'location' },
    'userPurchase': { primaryKey: 'userPurchaseKey', parentKey: 'userKey' },
    'stripeAccount': { primaryKey: 'stripeAccountKey', parentKey: 'userKey' },
    'stripeTransfer': { primaryKey: 'transferStripeKey', parentKey: 'userKey' },
    'notifications': { primaryKey: 'notificationKey', parentKey: 'forUserKey' },
    'campaigns': { primaryKey: 'name', nameField: 'name' },
    'post_templates': { primaryKey: 'title', parentKey: 'userKey' },
    'userPicture': { primaryKey: 'pictureKey', parentKey: 'userKey' },
    'follows': { primaryKey: 'followerKey', parentKey: 'followingKey', authorField: 'followerKey' },
    'igLink': { primaryKey: 'userKey' },
    'posts': { primaryKey: 'postKey', parentKey: 'groupKey', parentKey2: 'placeKey', authorField: 'userKey', geoField: 'coordinates' },
    'eventAttendees': { primaryKey: 'postKey', parentKey: 'userKey' },
    'placeRates': { primaryKey: 'placeKey', parentKey: 'userKey' },
    'places': { primaryKey: 'placeKey', secondaryKey: 'slug', nameField: 'name', parentKey: 'dominantGroupKey', geoField: 'coordinates' },
    'payment': { primaryKey: 'paymentId', parentKey: 'userSubscriptionKey' },
    'comments': { primaryKey: 'commentKey', parentKey: 'postKey', authorField: 'userKey' },
    'subscription': { primaryKey: 'subscriptionKey', nameField: 'name', parentKey: 'groupKey' },
    'checkins': { primaryKey: 'userKey', parentKey: 'placeKey' },
    'storiesSeen': { primaryKey: 'postKey', parentKey: 'userKey' },
    'userSets': { primaryKey: 'name' },
    'partner': { primaryKey: 'partnerKey', parentKey: 'userKey' },
    'userSubscription': { primaryKey: 'userSubscriptionKey', parentKey: 'userKey' },
    'emails': { primaryKey: 'emailKey', secondaryKey: 'email' },
    'phones': { primaryKey: 'phoneKey', secondaryKey: 'phone' },
    'polygons': { primaryKey: 'polygonKey', geoField: 'polygonCenter' },
    'polygonSchedulerCoordinate': { primaryKey: 'polygonSchedulerCoordinateKey' },
    'reports': { primaryKey: 'name' },
    'stories': { primaryKey: 'groupKey' },
};

const MAX_BATCH_SIZE = 20;
const FLUSH_INTERVAL_MS = 30_000;
const MONGO_WATCH_RESTART_MS = parsePositiveInt(process.env.MONGO_WATCH_RESTART_MS, 30_000);
const EVENT_BASE_SIZE = 19;
const TX_BASE_OVERHEAD = 200;
const COLLECTION_ROOT_SIZE = 1 + 32;
const STRING_LENGTH_PREFIX_SIZE = 4;
const TX_SIZE_LIMIT = 1232;
const LOG_ITEMS_PER_COLLECTION = 3;
const LOG_VALUE_MAX_LENGTH = 48;
const LOG_BAR_WIDTH = 40;
const EXCLUDED_WATCH_COLLECTIONS = new Set(['polygonSchedulerCoordinate']);

const FIELD_SECONDARY = 1 << 0; // 4 bytes
const FIELD_NAME      = 1 << 1; // 4 bytes
const FIELD_PARENT    = 1 << 2; // 4 bytes
const FIELD_PARENT2   = 1 << 3; // 4 bytes
const FIELD_AUTHOR    = 1 << 4; // 4 bytes
const FIELD_GEO       = 1 << 5; // 3 bytes

function parsePositiveInt(value: string | undefined, fallback: number): number {
    if (!value) return fallback;
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function hashKeyFull(value: string | undefined): number[] {
    if (!value) return Array.from(Buffer.alloc(8));
    return Array.from(createHash('sha256').update(value).digest().subarray(0, 8));
}

function hashShort(value: string | undefined): number[] {
    if (!value) return Array.from(Buffer.alloc(4));
    return Array.from(createHash('sha256').update(value).digest().subarray(0, 4));
}

function encodeGeoHash(coordinates: any): number[] {
    const buf = Buffer.alloc(3);
    if (!coordinates) return Array.from(buf);
    try {
        const coords = typeof coordinates === 'string' ? JSON.parse(coordinates) : coordinates;
        let lon: number, lat: number;
        if (coords.coordinates && Array.isArray(coords.coordinates)) {
            [lon, lat] = coords.coordinates;
        } else if (typeof coords.longitude === 'number' && typeof coords.latitude === 'number') {
            lon = coords.longitude;
            lat = coords.latitude;
        } else {
            return Array.from(buf);
        }

        const latSnapped = Math.min(399, Math.max(0, Math.floor((lat + 90) / 0.45)));
        const lonSnapped = Math.min(799, Math.max(0, Math.floor((lon + 180) / 0.45)));

        const packed = (latSnapped << 12) | lonSnapped;
        buf[0] = (packed >> 16) & 0xff;
        buf[1] = (packed >> 8) & 0xff;
        buf[2] = packed & 0xff;
        const hex = buf.toString("hex");
        logInfo(`     -> Geohash Generated: lat=${lat}, lon=${lon} => ${hex}`);
    } catch {
        return Array.from(buf);
    }
    return Array.from(buf);
}

function encodeTimestamp(doc: any): number[] {
    const buf = Buffer.alloc(4);
    const ts = doc?.timestamp || doc?.createTimestamp || doc?.createdAt || doc?.updatedAt || Math.floor(Date.now() / 1000);
    if (!ts) return Array.from(buf);
    let epoch: number;
    if (ts instanceof Date) {
        epoch = Math.floor(ts.getTime() / 1000);
    } else if (typeof ts === 'number') {
        epoch = ts > 1e12 ? Math.floor(ts / 1000) : ts;
    } else if (typeof ts === 'string') {
        epoch = Math.floor(new Date(ts).getTime() / 1000);
    } else {
        return Array.from(buf);
    }
    buf.writeUInt32LE(epoch >>> 0, 0);
    return Array.from(buf);
}

function actionFromOpType(opType: string): number {
    switch (opType) {
        case 'insert': return 0;
        case 'update': return 1;
        case 'replace': return 1;
        case 'delete': return 2;
        default: return 1;
    }
}

function buildBatchEvent(collectionName: string, doc: any, action: number) {
    const meta = COLLECTION_META[collectionName];
    const colId = COLLECTION_ID_MAP.get(collectionName);
    if (!meta || colId === undefined) return null;

    const secondary = hashShort(doc?.[meta.secondaryKey ?? '']);
    const name      = hashShort(doc?.[meta.nameField ?? '']);
    const parent    = hashShort(doc?.[meta.parentKey ?? '']);
    const parent2   = hashShort(doc?.[meta.parentKey2 ?? '']);
    const author    = hashShort(doc?.[meta.authorField ?? '']);
    const geo       = encodeGeoHash(doc?.[meta.geoField ?? '']);

    const isZero = (b: number[]) => b.every(v => v === 0);

    let fieldMask = 0;
    const optBytes: number[] = [];

    if (!isZero(secondary)) { fieldMask |= FIELD_SECONDARY; optBytes.push(...secondary); }
    if (!isZero(name))      { fieldMask |= FIELD_NAME;      optBytes.push(...name); }
    if (!isZero(parent))    { fieldMask |= FIELD_PARENT;    optBytes.push(...parent); }
    if (!isZero(parent2))   { fieldMask |= FIELD_PARENT2;   optBytes.push(...parent2); }
    if (!isZero(author))    { fieldMask |= FIELD_AUTHOR;    optBytes.push(...author); }
    if (!isZero(geo))       { fieldMask |= FIELD_GEO;       optBytes.push(...geo); }

    return {
        collectionId: colId,
        action,
        fieldMask,
        keyHash: hashKeyFull(doc?.[meta.primaryKey]),
        timestamp: encodeTimestamp(doc),
        optData: Buffer.from(optBytes),
    };
}

function calculateEventSize(event: NonNullable<ReturnType<typeof buildBatchEvent>>): number {
    return EVENT_BASE_SIZE + event.optData.length;
}

function getBatchPointer(batchId: number): string {
    return `/v3/batches/${batchId}`;
}

function calculateBatchTxSize(events: NonNullable<ReturnType<typeof buildBatchEvent>>[], rootCount: number, batchPointer: string): number {
    const eventsSize = events.reduce((sum, e) => sum + calculateEventSize(e), 0);
    const rootsSize = rootCount * COLLECTION_ROOT_SIZE;
    const vectorOverhead = 4 + 4 + 4;
    const pointerSize = STRING_LENGTH_PREFIX_SIZE + Buffer.byteLength(batchPointer, "utf-8");
    return TX_BASE_OVERHEAD + rootsSize + eventsSize + vectorOverhead + pointerSize;
}

function logPrefix(): string {
    return `[${new Date().toISOString()}]`;
}

function logInfo(message: string) {
    console.log(`${logPrefix()} ${message}`);
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

function parseTxTooLargeError(err: any): { actualSize: number; limit: number; message: string } | null {
    const message = getErrorMessage(err);
    const match = message.match(/Transaction too large:\s*(\d+)\s*>\s*(\d+)/i);
    if (!match) return null;
    return {
        actualSize: Number(match[1]),
        limit: Number(match[2]),
        message,
    };
}

function makeTxTooLargeResult(actualSize: number, limit: number): { ok: false; reason: "txTooLarge"; error: string; actualSize: number; limit: number } {
    return {
        ok: false,
        reason: "txTooLarge",
        error: `Transaction too large: ${actualSize} > ${limit}`,
        actualSize,
        limit,
    };
}

function compactLengthSize(length: number): number {
    let size = 0;
    let remaining = length;
    do {
        remaining = Math.floor(remaining / 128);
        size++;
    } while (remaining > 0);
    return size;
}

function calculateSignedTransactionSize(tx: Transaction): number {
    return compactLengthSize(tx.signatures.length) + tx.signatures.length * 64 + tx.serializeMessage().length;
}

function sizeGauge(size: number, limit: number): string {
    const filled = Math.min(LOG_BAR_WIDTH, Math.floor((size / limit) * LOG_BAR_WIDTH));
    const bar = '#'.repeat(filled);
    const empty = '.'.repeat(LOG_BAR_WIDTH - filled);
    const utilization = ((size / limit) * 100).toFixed(1);
    return `[${bar}${empty}] ${utilization}%`;
}

function compactLogValue(value: any): string {
    if (value === null || value === undefined) return "<missing>";
    let text: string;
    try {
        if (typeof value === "string") {
            text = value;
        } else if (typeof value?.toHexString === "function") {
            text = value.toHexString();
        } else if (typeof value?.toString === "function" && value.toString !== Object.prototype.toString) {
            text = value.toString();
        } else {
            text = JSON.stringify(value);
        }
    } catch {
        text = String(value);
    }
    text = text.replace(/\s+/g, " ").trim();
    if (text.length <= LOG_VALUE_MAX_LENGTH) return text;
    return `${text.slice(0, LOG_VALUE_MAX_LENGTH - 3)}...`;
}

function printTxSizeReport(events: NonNullable<ReturnType<typeof buildBatchEvent>>[], rootCount: number, batchPointer: string) {
    const totalSize = calculateBatchTxSize(events, rootCount, batchPointer);
    const totalEventsBytes = events.reduce((s, e) => s + calculateEventSize(e), 0);
    const pointerSize = STRING_LENGTH_PREFIX_SIZE + Buffer.byteLength(batchPointer, "utf-8");

    logInfo(`  Estimated Transaction Size:`);
    logInfo(`     Events: ${events.length} x avg ${events.length ? Math.round(totalEventsBytes / events.length) : 0}B = ${totalEventsBytes}B`);
    logInfo(`     Roots:  ${rootCount} x ${COLLECTION_ROOT_SIZE}B = ${rootCount * COLLECTION_ROOT_SIZE}B`);
    logInfo(`     Pointer: ${pointerSize}B`);
    logInfo(`     Base:   ${TX_BASE_OVERHEAD}B overhead`);
    logInfo(`     Total:  ${totalSize}B / ${TX_SIZE_LIMIT}B`);
    logInfo(`     ${sizeGauge(totalSize, TX_SIZE_LIMIT)}`);
}

function printSerializedTxSizeReport(size: number) {
    logInfo(`  Exact Serialized Transaction Size:`);
    logInfo(`     Total:  ${size}B / ${TX_SIZE_LIMIT}B`);
    logInfo(`     ${sizeGauge(size, TX_SIZE_LIMIT)}`);
}

async function processCollectionUpdates(db: Db, colName: string, updates: any[]): Promise<string | null> {
    for (const u of updates) {
        const doc = u.doc;
        if (!doc || !doc._id) continue;
        const filter = { _id: doc._id };
        const meta = COLLECTION_META[colName];
        const pkValue = doc[meta.primaryKey];
        const setFields: any = { _h: true };
        if (pkValue != null) {
            setFields._kh = createHash('sha256').update(String(pkValue)).digest().subarray(0, 8).toString('hex');
        }
        await db.collection(colName).updateOne(filter, { $set: setFields });
    }
    return '0'.repeat(64);
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

    let batchIdDoc = await db.collection('batch_tracker').findOne({ _id: 'current' as any });
    let currentBatchId = batchIdDoc?.batchId ?? 1;

    interface PendingUpdate {
        colName: string;
        action: number;
        doc: any;
        event: NonNullable<ReturnType<typeof buildBatchEvent>>;
    }
    type PublishBatchResult =
        | { ok: true }
        | { ok: false; reason: "txTooLarge"; error: string; actualSize: number; limit: number }
        | { ok: false; reason: "failed"; error: string };
    interface PublishBatchContext {
        flushTotal: number;
        remainingBefore: number;
        attempt: number;
    }

    let eventBuffer: PendingUpdate[] = [];
    let isFlushing = false;

    function groupUpdatesByCollection(updates: PendingUpdate[]) {
        const colUpdates = new Map<string, PendingUpdate[]>();
        for (const pu of updates) {
            if (!colUpdates.has(pu.colName)) colUpdates.set(pu.colName, []);
            colUpdates.get(pu.colName)!.push(pu);
        }
        return colUpdates;
    }

    function buildBatchPayload(updates: PendingUpdate[]) {
        const payload: Record<string, any[]> = {};
        for (const update of updates) {
            if (update.action === 2 || !update.doc) continue;
            payload[update.colName] = payload[update.colName] || [];
            payload[update.colName].push(update.doc);
        }
        return payload;
    }

    function estimateUpdatesSize(updates: PendingUpdate[], batchId: number) {
        const events = updates.map(p => p.event);
        const rootCount = groupUpdatesByCollection(updates).size;
        return calculateBatchTxSize(events, rootCount, getBatchPointer(batchId));
    }

    function actionLabel(action: number): string {
        switch (action) {
            case 0: return "insert";
            case 1: return "update";
            case 2: return "delete";
            default: return "unknown";
        }
    }

    function describeUpdate(update: PendingUpdate): string {
        const meta = COLLECTION_META[update.colName];
        const preferredField = update.colName === "users" ? "email" : meta?.primaryKey;
        const preferredValue = preferredField ? update.doc?.[preferredField] : undefined;
        const fallbackValue = update.doc?._id;
        const fieldName = preferredValue === undefined && fallbackValue !== undefined ? "_id" : preferredField || "key";
        const value = preferredValue === undefined ? fallbackValue : preferredValue;
        return `${actionLabel(update.action)} ${fieldName}=${compactLogValue(value)}`;
    }

    function actionSummary(updates: PendingUpdate[]): string {
        const counts = new Map<string, number>();
        for (const update of updates) {
            const label = actionLabel(update.action);
            counts.set(label, (counts.get(label) || 0) + 1);
        }
        return Array.from(counts.entries()).map(([label, count]) => `${label}=${count}`).join(", ");
    }

    function logBatchData(batchId: number, updates: PendingUpdate[]) {
        logInfo(`Batch #${batchId} data:`);
        const colUpdates = groupUpdatesByCollection(updates);
        for (const [colName, collectionUpdates] of colUpdates.entries()) {
            const colId = COLLECTION_ID_MAP.get(colName);
            const colLabel = colId === undefined ? colName : `[${colId}] ${colName}`;
            const eventBytes = collectionUpdates.reduce((sum, update) => sum + calculateEventSize(update.event), 0);
            const shown = collectionUpdates.slice(0, LOG_ITEMS_PER_COLLECTION).map(describeUpdate);
            const hidden = collectionUpdates.length - shown.length;
            const suffix = hidden > 0 ? `, +${hidden} more` : "";
            logInfo(`  ${colLabel}: ${collectionUpdates.length} events, ${eventBytes}B, ${actionSummary(collectionUpdates)}; ${shown.join(", ")}${suffix}`);
        }
    }

    function selectEstimatedChunkSize(updates: PendingUpdate[]) {
        let chunkSize = updates.length;
        while (chunkSize > 1 && estimateUpdatesSize(updates.slice(0, chunkSize), currentBatchId) > TX_SIZE_LIMIT) {
            chunkSize = Math.ceil(chunkSize / 2);
        }
        return chunkSize;
    }

    function shrinkChunkSizeAfterTooLarge(chunk: PendingUpdate[], actualSize: number, limit: number) {
        const eventBytes = chunk.reduce((sum, update) => sum + calculateEventSize(update.event), 0);
        const avgEventBytes = Math.max(1, Math.ceil(eventBytes / chunk.length));
        const overage = Math.max(1, actualSize - limit);
        const removeCount = Math.max(1, Math.ceil(overage / avgEventBytes) + 1);
        return Math.max(1, chunk.length - removeCount);
    }

    async function publishBatchChunk(updates: PendingUpdate[], context: PublishBatchContext): Promise<PublishBatchResult> {
        const batchId = currentBatchId;
        const batchPointer = getBatchPointer(batchId);
        const colUpdates = groupUpdatesByCollection(updates);
        const events = updates.map(p => p.event);

        logInfo(`Flushing batch #${batchId} attempt #${context.attempt}: ${events.length}/${context.remainingBefore} pending events across ${colUpdates.size} collections; flush total ${context.flushTotal}`);
        logBatchData(batchId, updates);
        printTxSizeReport(events, colUpdates.size, batchPointer);

        const collectionRoots: { collectionId: number; root: number[] }[] = [];
        for (const [colName, collectionUpdates] of colUpdates.entries()) {
            const newRoot = await processCollectionUpdates(db, colName, collectionUpdates);
            if (newRoot) {
                const colId = COLLECTION_ID_MAP.get(colName)!;
                collectionRoots.push({
                    collectionId: colId,
                    root: Array.from(Buffer.from(newRoot, 'hex'))
                });
                logInfo(`  Root [${colId}] ${colName}: ${newRoot.slice(0, 16)}... from ${collectionUpdates.length} events`);
            }
        }

        const provider = payer.publicKey.toBase58();
        const payload = buildBatchPayload(updates);
        await db.collection('network_batches').updateOne(
            { provider, batchId },
            {
                $set: {
                    provider,
                    batchId,
                    batchPointer,
                    payload,
                    status: "pending",
                    updatedAt: new Date()
                },
                $setOnInsert: { createdAt: new Date() }
            },
            { upsert: true }
        );

        try {
            const ix = await program.methods
                .syncCollectionBatch(1, batchId, collectionRoots, events, batchPointer)
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

            const serializedSize = calculateSignedTransactionSize(tx);
            await db.collection('network_batches').updateOne(
                { provider, batchId },
                { $set: { serializedSize, sizeCheckedAt: new Date(), updatedAt: new Date() } }
            );
            printSerializedTxSizeReport(serializedSize);
            if (serializedSize > TX_SIZE_LIMIT) {
                await db.collection('network_batches').updateOne(
                    { provider, batchId },
                    {
                        $set: {
                            status: "retrying",
                            error: `Transaction too large: ${serializedSize} > ${TX_SIZE_LIMIT}`,
                            attemptedEvents: updates.length,
                            serializedSize,
                            updatedAt: new Date()
                        }
                    }
                );
                logWarn(`Batch #${batchId} too large before send: ${serializedSize} > ${TX_SIZE_LIMIT}`);
                return makeTxTooLargeResult(serializedSize, TX_SIZE_LIMIT);
            }

            const serializedTx = tx.serialize();
            logInfo(`Batch #${batchId} sending raw transaction`);
            const txHash = await connection.sendRawTransaction(serializedTx, { preflightCommitment: "confirmed" });
            logInfo(`Batch #${batchId} submitted: ${txHash}`);
            const confirmation = await connection.confirmTransaction(
                {
                    signature: txHash,
                    blockhash: latestBlockhash.blockhash,
                    lastValidBlockHeight: latestBlockhash.lastValidBlockHeight,
                },
                "confirmed"
            );
            if (confirmation.value.err) {
                throw new Error(`Transaction confirmation failed: ${compactLogValue(confirmation.value.err)}`);
            }

            logInfo(`Batch #${batchId} confirmed and published: ${txHash}`);
            await db.collection('network_batches').updateOne(
                { provider, batchId },
                { $set: { txHash, publishedAt: new Date(), status: "published", updatedAt: new Date() } }
            );
            currentBatchId++;
            await db.collection('batch_tracker').updateOne(
                { _id: 'current' as any },
                { $set: { batchId: currentBatchId, lastPush: new Date() } },
                { upsert: true }
            );
            return { ok: true };
        } catch (err: any) {
            const message = getErrorMessage(err);
            const tooLarge = parseTxTooLargeError(err);
            await db.collection('network_batches').updateOne(
                { provider, batchId },
                {
                    $set: {
                        status: tooLarge ? "retrying" : "failed",
                        error: message,
                        attemptedEvents: updates.length,
                        updatedAt: new Date()
                    }
                }
            );
            if (tooLarge) {
                logWarn(`Batch #${batchId} transaction too large during send: ${tooLarge.actualSize} > ${tooLarge.limit}`);
                return makeTxTooLargeResult(tooLarge.actualSize, tooLarge.limit);
            }
            logError(`Batch #${batchId} failed`, message);
            return { ok: false, reason: "failed", error: message };
        }
    }

    async function flushBatch() {
        if (isFlushing) return;
        const pendingUpdates = eventBuffer.slice();
        if (pendingUpdates.length === 0) return;

        isFlushing = true;
        let remainingUpdates: PendingUpdate[] = [];
        try {
            eventBuffer = [];
            remainingUpdates = pendingUpdates.slice();
            logInfo(`Flush started: ${pendingUpdates.length} buffered events`);
            while (remainingUpdates.length > 0) {
                let chunkSize = selectEstimatedChunkSize(remainingUpdates);
                let attempt = 1;
                while (chunkSize > 0) {
                    const chunk = remainingUpdates.slice(0, chunkSize);
                    const result = await publishBatchChunk(chunk, {
                        flushTotal: pendingUpdates.length,
                        remainingBefore: remainingUpdates.length,
                        attempt,
                    });
                    if (result.ok) {
                        remainingUpdates = remainingUpdates.slice(chunkSize);
                        if (remainingUpdates.length > 0) {
                            logInfo(`Flush continues: ${remainingUpdates.length} events remaining; next on-chain batch #${currentBatchId}`);
                        }
                        break;
                    }
                    if (result.reason === "txTooLarge" && chunkSize > 1) {
                        const nextChunkSize = shrinkChunkSizeAfterTooLarge(chunk, result.actualSize, result.limit);
                        const deferredCount = chunkSize - nextChunkSize;
                        logWarn(`Batch #${currentBatchId} too large after build; retrying ${nextChunkSize}/${chunkSize} events now and deferring ${deferredCount} to later batches`);
                        chunkSize = nextChunkSize;
                        attempt++;
                        continue;
                    }
                    if (result.reason === "txTooLarge") {
                        logError(`Batch #${currentBatchId} has one event that exceeds the transaction limit; requeued for inspection`, result.error);
                    }
                    eventBuffer = [...remainingUpdates, ...eventBuffer];
                    return;
                }
            }
        } catch (err: any) {
            eventBuffer = [...remainingUpdates, ...eventBuffer];
            logError(`Fatal error in flushBatch`, getErrorMessage(err));
        } finally {
            isFlushing = false;
        }
    }

    const flushTimer = setInterval(() => {
        if (eventBuffer.length > 0) {
            flushBatch();
        }
    }, FLUSH_INTERVAL_MS);

    const watchCollections = COLLECTION_ORDER.filter(name =>
        COLLECTION_META[name] && !EXCLUDED_WATCH_COLLECTIONS.has(name)
    );
    const activeChangeStreams = new Set<any>();
    const restartTimers = new Set<NodeJS.Timeout>();
    let shuttingDown = false;

    logInfo(`Batch Push Service: Watching ${watchCollections.length} collections...`);

    async function handleChange(colName: string, change: ChangeStreamDocument) {
        const updateDesc = (change as any).updateDescription;
        const updatedFields = updateDesc?.updatedFields || {};
        const removedFields = updateDesc?.removedFields || [];

        const internalFields = ['_h', '_remote', '_kh'];
        const changedKeys = Object.keys(updatedFields);
        const isPurlyInternal = (changedKeys.length > 0 && changedKeys.every(k => internalFields.includes(k))) ||
            (removedFields.length === 1 && removedFields[0] === '_remote');

        if (isPurlyInternal) {
            return;
        }

        const doc = (change as any).fullDocument || (change as any).documentKey;
        if (!doc) {
            return;
        }

        if (doc && doc._remote === true) {
            return;
        }

        const validOps = ['insert', 'update', 'replace', 'delete'];
        if (!validOps.includes(change.operationType)) {
            return;
        }

        const action = actionFromOpType(change.operationType);

        const event = buildBatchEvent(colName, doc, action);
        if (!event) return;

        eventBuffer.push({ colName, action, doc, event });

        if (eventBuffer.length >= MAX_BATCH_SIZE) {
            flushBatch(); // Don't await here to keep the listener responsive
        }
    }

    function watchCollection(colName: string) {
        const collection = db.collection(colName);
        const changeStream = collection.watch([], { fullDocument: 'updateLookup' });
        activeChangeStreams.add(changeStream);
        let restartScheduled = false;

        changeStream.on('change', (change: ChangeStreamDocument) => {
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

        changeStream.on('error', (err: any) => {
            scheduleRestart('errored', err);
        });

        changeStream.on('close', () => {
            scheduleRestart('closed');
        });
    }

    for (const colName of watchCollections) {
        watchCollection(colName);
    }

    process.on('SIGINT', async () => {
        shuttingDown = true;
        for (const timer of restartTimers) clearTimeout(timer);
        restartTimers.clear();
        clearInterval(flushTimer);
        await Promise.allSettled([...activeChangeStreams].map((changeStream: any) => changeStream.close()));
        if (eventBuffer.length > 0) {
            await flushBatch();
        }
    });
}
