import "reflect-metadata";
import express from "express";
import type { NextFunction, Request, Response } from "express";
import { MongoClient, ObjectId } from "mongodb";
import { PORT, PROGRAM_ID, MERKLE_TREE_MINT, ORBIS_MINT, JAVA_BACKEND_URL, RPC_URL, CLONE_BASE_URL, MANIFEST_TUNABLES, SELF_CONSUME, createConnection } from "./config.js";
import { getAssociatedTokenAddressSync } from "@solana/spl-token";
import { getPayer } from "./blockchain/wallet.js";
import { scanHistory, cacheToMongo, startRealtimeListener, discoverAllProviders, ensureNetworkIndexes } from "./blockchain/indexer.js";
import { registerProfileRoutes } from "./routes/profile.js";
import { registerAuthRoutes } from "./routes/auth.js";
import { registerGroupsRoutes } from "./routes/groups.js";
import { registerPostsRoutes } from "./routes/posts.js";
import { registerPlacesRoutes } from "./routes/places.js";
import { registerFeedRoutes } from "./routes/feed.js";
import { registerMiscRoutes } from "./routes/misc.js";
import { registerPolygonRoutes } from "./routes/polygon.js";
import { registerV3Routes } from "./routes/v3.js";
import { registerPaymentsRoutes } from "./routes/payments.js";
import { startTribePaymentsListener, paymentsEnabled } from "./network/tribe-payment.js";
import axios from "axios";
import { calculateFee, startVoucherTimer } from "./network/payment.js";
import { PublicKey } from "@solana/web3.js";
import * as anchor from "@coral-xyz/anchor";
import { startBatchPushService } from "./blockchain/batch-push-service.js";
import { log } from "./logger.js";
import { runStartupChecks } from "./startup.js";
import { createHmac } from "crypto";
import { readFileSync } from "node:fs";

const LOCAL_IDL = JSON.parse(readFileSync(new URL("./programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));

const API_SECRET: string = process.env.API_SECRET ?? (() => { throw new Error("API_SECRET environment variable is missing."); })();
const ORBIS_API_SECRET: string = process.env.ORBIS_API_SECRET ?? (() => { throw new Error("ORBIS_API_SECRET environment variable is missing."); })();
const DEFAULT_IO_TIMEOUT_MS = 30_000;
const MONGO_TIMEOUT_MS = parsePositiveInt(process.env.MONGO_TIMEOUT_MS, DEFAULT_IO_TIMEOUT_MS);
const JAVA_BACKEND_TIMEOUT_MS = parsePositiveInt(process.env.JAVA_BACKEND_TIMEOUT_MS, DEFAULT_IO_TIMEOUT_MS);
const EVENT_SYNC_STALE_MS = parsePositiveInt(process.env.EVENT_SYNC_STALE_MS, 120_000);

function parsePositiveInt(value: string | undefined, fallback: number): number {
    if (!value) return fallback;
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function describeError(err: unknown): string {
    if (err instanceof Error) return err.message;
    return String(err);
}

function delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function waitForNetworkEventToSettle(db: any, eventId: ObjectId, timeoutMs = 10_000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        const event = await db.collection("network_events").findOne({ _id: eventId });
        if (!event || event.status !== "syncing") return event;
        await delay(250);
    }
    return db.collection("network_events").findOne({ _id: eventId });
}

async function forwardToLocalJava(req: Request, res: Response, reason: string) {
    const javaUrl = `${JAVA_BACKEND_URL}${req.path}`;
    const javaQuery: any = { ...req.query, _java_proxied: "true" };
    delete javaQuery._internal;
    delete javaQuery.network_event_id;

    log.info(`${reason}: ${javaUrl} [${req.method}]`);
    const javaHeaders: Record<string, string> = { "X-API-Key": buildLocalApiKey() };
    const authorization = req.headers["authorization"];
    if (typeof authorization === "string") javaHeaders.Authorization = authorization;
    const javaRequestOptions = {
        params: javaQuery,
        headers: javaHeaders,
        responseType: "arraybuffer" as const,
        timeout: JAVA_BACKEND_TIMEOUT_MS,
        timeoutErrorMessage: `Java backend timed out after ${JAVA_BACKEND_TIMEOUT_MS}ms`,
    };
    const javaRes = req.method === "POST" || req.method === "PUT"
        ? await axios({ method: req.method.toLowerCase(), url: javaUrl, data: req.body, ...javaRequestOptions, headers: { ...javaHeaders, "Content-Type": "application/json" } })
        : await axios.get(javaUrl, javaRequestOptions);

    const contentType = javaRes.headers["content-type"];
    if (contentType) res.setHeader("Content-Type", contentType);
    return res.status(javaRes.status).send(javaRes.data);
}

function wrapAsyncHandler(fn: any) {
    if (typeof fn !== "function" || fn.length === 4) return fn;
    return function wrappedAsyncHandler(req: Request, res: Response, next: NextFunction) {
        try {
            const result = fn(req, res, next);
            if (result && typeof result.then === "function") {
                result.catch(next);
            }
            return result;
        } catch (err) {
            return next(err);
        }
    };
}

function wrapExpressAsyncHandlers(app: any) {
    for (const method of ["use", "get", "post", "put", "patch", "delete"] as const) {
        const original = app[method].bind(app);
        app[method] = (...args: any[]) => original(...args.map(wrapAsyncHandler));
    }
}

process.on("unhandledRejection", (reason) => {
    log.error(`Unhandled promise rejection captured: ${describeError(reason)}`);
});

process.on("uncaughtException", (err) => {
    log.error(`Uncaught exception captured: ${describeError(err)}`);
});

function buildLocalApiKey() {
    const payload = Buffer.from(JSON.stringify({ role: "internal", timestamp: Date.now() })).toString("base64url");
    const sig = createHmac("sha256", ORBIS_API_SECRET).update(payload).digest("base64url");
    return `${payload}.${sig}`;
}

function isValidOrbisKey(token: string): boolean {
    const parts = token.split(".");
    if (parts.length !== 2) return false;
    const [payload, sig] = parts;
    const expectedSig = createHmac("sha256", API_SECRET).update(payload).digest("base64url");
    return expectedSig === sig;
}

function onOff(on: boolean): string {
    return on ? "ON " : "OFF";
}

function printStartupBanner(opts: {
    payer: string;
    port: number;
    baseUrl: string;
    db: string;
    programId: string;
    rpc: string;
    disableBatch: boolean;
    skipHistory: boolean;
    register: boolean;
    dryRun: boolean;
    noCache: boolean;
    autoFetch: boolean;
    selfConsume: boolean;
    payments: boolean;
}) {
    const t = MANIFEST_TUNABLES;
    const maskUrl = (s: string) => s.replace(/([?&]api-key=)[^&]+/i, "$1***");
    const bar = "══════════════════════════════════════════════════════════════";
    const lines = [
        "",
        bar,
        "  ORBIS CLONE PROXY — startup configuration",
        bar,
        "  Identity",
        `    payer        ${opts.payer}`,
        `    port         ${opts.port}`,
        `    base url     ${opts.baseUrl}`,
        `    database     ${opts.db}`,
        `    program      ${opts.programId}`,
        `    rpc          ${maskUrl(opts.rpc)}`,
        "  Producer (manifest lanes)",
        `    public       flush at ${t.publicFlushCount} entries or ${Math.round(t.publicFlushAgeMs / 1000)}s`,
        `    derived      flush at ${t.derivedFlushCount} entries or ${Math.round(t.derivedFlushAgeMs / 1000)}s`,
        `    check tick   ${Math.round(t.flushTickMs / 1000)}s`,
        "  Flags",
        `    [${onOff(!opts.disableBatch)}]  batch producer        (DISABLE_BATCH ${onOff(opts.disableBatch)})`,
        `    [${onOff(opts.autoFetch)}]  AUTO_FETCH_EVENTS     eager committed payload fetch`,
        `    [${onOff(opts.selfConsume)}]  SELF_CONSUME          index own manifests`,
        `    [${onOff(!opts.noCache)}]  provider cache        (NO_CACHE ${onOff(opts.noCache)})`,
        `    [${onOff(opts.skipHistory)}]  SKIP_HISTORY          forward-only listener`,
        `    [${onOff(opts.register)}]  REGISTER              register clone on start`,
        `    [${onOff(opts.dryRun)}]  DRY_RUN               validate then exit`,
        `    [${onOff(opts.payments)}]  tribe payments        (DEVNET_* configured)`,
        bar,
        "",
    ];
    for (const l of lines) console.log(l);
}

async function main() {
    const mongoUri = process.argv[2] || process.env.MONGO_URI;
    if (!mongoUri) {
        log.error("MONGO_URI environment variable or positional argument is required.");
        process.exit(1);
    }

    const dbName = process.argv[3] && !process.argv[3].startsWith("--") ? process.argv[3] : process.env.LOCAL_DB_NAME!;

    const walletIdx = process.argv.indexOf("--wallet");
    let walletPath: string | undefined;
    if (walletIdx !== -1) {
        walletPath = process.argv[walletIdx + 1];
        if (!walletPath || walletPath.startsWith("--")) {
            log.error("Error: --wallet flag requires a path argument.");
            process.exit(1);
        }
    } else {
        walletPath = process.env.WALLET_PATH;
    }

    const shouldRegister = process.argv.includes("--register") || process.env.REGISTER === "true";
    const dryRun = process.argv.includes("--dry-run") || process.env.DRY_RUN === "true";
    const skipHistory = process.argv.includes("--skip-history") || process.env.SKIP_HISTORY === "true";

    const mongoClient = new MongoClient(mongoUri, {
        serverSelectionTimeoutMS: MONGO_TIMEOUT_MS,
        connectTimeoutMS: MONGO_TIMEOUT_MS,
        socketTimeoutMS: MONGO_TIMEOUT_MS,
    });
    await mongoClient.connect();
    const db = mongoClient.db(dbName);

    const connection = createConnection();
    const payer = await getPayer(walletPath);

    log.info("Starting Orbis Clone Proxy...");

    const disableBatch = process.argv.includes("--disable-batch") || process.env.DISABLE_BATCH === "true";
    const noCache = process.argv.includes("--no-cache") || process.env.NO_CACHE === "true";
    const autoFetch = process.env.AUTO_FETCH_EVENTS === "true";
    printStartupBanner({
        payer: payer.publicKey.toBase58(),
        port: PORT,
        baseUrl: CLONE_BASE_URL,
        db: dbName,
        programId: PROGRAM_ID.toBase58(),
        rpc: RPC_URL,
        disableBatch,
        skipHistory,
        register: shouldRegister,
        dryRun,
        noCache,
        autoFetch,
        selfConsume: SELF_CONSUME,
        payments: paymentsEnabled(),
    });

    if (dryRun) {
        log.info("--- DRY RUN MODE ---");
        try {
            await mongoClient.db("admin").command({ ping: 1 });
            log.info("MongoDB connected.");
            const replStatus = await mongoClient.db("admin").command({ replSetGetStatus: 1 }).catch(() => null);
            if (replStatus) log.info("MongoDB Replica Set active.");
            else log.warn("MongoDB is NOT a replica set.");

            const version = await connection.getVersion();
            log.info(`Solana RPC connected (version ${version["solana-core"]}).`);

            const bal = await connection.getBalance(payer.publicKey);
            log.info(bal > 0 ? `SOL balance exists: ${bal / 1e9} SOL.` : `Insufficient SOL balance: ${bal}.`);

            const ata = getAssociatedTokenAddressSync(ORBIS_MINT, payer.publicKey);
            try {
                const orbisBal = await connection.getTokenAccountBalance(ata);
                log.info(orbisBal.value.uiAmount! > 0 ? `ORBIS balance exists: ${orbisBal.value.uiAmount} ORBIS.` : `Insufficient ORBIS balance: ${orbisBal.value.uiAmount}.`);
            } catch (e) {
                log.warn("ORBIS token account not found for this wallet. Balance is 0.");
            }

            log.info("System checks completed.");
        } catch (e: any) {
            log.error(`Dry run failed: ${e.message}`);
        }
        process.exit(0);
    }

    await runStartupChecks(connection, payer, shouldRegister);

    log.info("Discovering providers from on-chain CloneInfo PDAs...");
    await discoverAllProviders(db, connection, PROGRAM_ID);

    if (skipHistory) {
        log.info("--skip-history enabled: bootstrapping watermark and starting forward-only listener");
        const latestSigs = await connection.getSignaturesForAddress(MERKLE_TREE_MINT, { limit: 1 });
        const latestSignature = latestSigs[0]?.signature;
        if (latestSignature) {
            await db.collection("network_sync_state").updateOne(
                { _id: "last_scan" as any },
                { $set: { lastScannedAt: new Date(), totalBatches: 0, lastSignature: latestSignature } },
                { upsert: true }
            );
            log.info(`Watermark set to current head: ${latestSignature}`);
        } else {
            log.info("No on-chain signatures found yet; starting listener from current point");
        }
        startRealtimeListener(connection, PROGRAM_ID, db, [], payer.publicKey.toBase58(), payer);
        log.info("Realtime event listener active (forward-only mode)");
    } else {
        const syncState = await db.collection("network_sync_state").findOne({ _id: "last_scan" as any });
        const untilSignature = syncState?.lastSignature;
        log.info(`Last indexed signature: ${untilSignature || "none"}`);

        scanHistory(connection, MERKLE_TREE_MINT, untilSignature).then(async (batches) => {
            if (batches.length > 0) {
                await cacheToMongo(db, batches, connection, PROGRAM_ID, payer.publicKey.toBase58(), payer);
                log.info(`Indexed ${batches.length} new historical batches`);
            } else {
                log.info("Already up to date with blockchain history");
            }
            startRealtimeListener(connection, PROGRAM_ID, db, batches, payer.publicKey.toBase58(), payer);
            log.info("Realtime event listener active");
        }).catch((err) => log.error(`Indexer error: ${err.message}`));
    }
    if (!disableBatch) {
        startBatchPushService(db, connection, payer).catch(err => {
            if (err.message.includes("REPLICA_SET_REQUIRED")) {
                log.warn("--- WARNING: BATCH PUSH SERVICE DISABLED ---");
                log.warn(err.message);
                log.warn("Your MongoDB instance is NOT a replica set. Node will NOT index real-time database changes.");
                return;
            }
            log.error(`Batch push service error: ${err.message}`);
        });
    } else {
        log.info("Batch push service disabled by --disable-batch flag");
    }

    ensureNetworkIndexes(db).catch(err => {
        log.error(`Ensure network indexes error: ${describeError(err)}`);
    });

    if (paymentsEnabled()) {
        startTribePaymentsListener(db);
    } else {
        log.info("Tribe payments disabled (set DEVNET_RPC_URL + DEVNET_PAYMENTS_PROGRAM_ID to enable)");
    }

    const app = express();
    wrapExpressAsyncHandlers(app);
    app.use(express.json());
    app.use((req, _res, next) => {
        log.info(`${req.method} ${req.url}`);
        next();
    });

    app.use(async (req, res, next) => {
        if (
            req.path === "/network/status" ||
            req.path === "/v3/api-key" ||
            req.path === "/v3/vouchers" ||
            req.path === "/debug/network-lookup" ||
            req.path.startsWith("/v3/index-batches/")
        ) return next();

        if (req.query._internal === "true") {
            /*
            const remoteAddress = req.socket.remoteAddress;
            if (remoteAddress !== "127.0.0.1" && remoteAddress !== "::1" && remoteAddress !== "::ffff:127.0.0.1") {
                return res.status(403).json({ error: "Internal endpoint not accessible externally" });
            }
            */
            const masterKey = req.headers["x-master-key"] as string;
            if (!masterKey || masterKey !== ORBIS_API_SECRET) {
                return res.status(401).json({ error: "Invalid master key" });
            }

            const eventId = req.query.network_event_id as string;
            if (!eventId) return next();

            const eventObjectId = new ObjectId(eventId);
            const claimResult: any = await db.collection("network_events").findOneAndUpdate(
                {
                    _id: eventObjectId,
                    $or: [
                        { status: "pending" },
                        { status: "syncing", syncStartedAt: { $lt: new Date(Date.now() - EVENT_SYNC_STALE_MS) } },
                    ],
                },
                {
                    $set: {
                        status: "syncing",
                        syncStartedAt: new Date(),
                        updatedAt: new Date(),
                    },
                    $unset: {
                        syncError: "",
                    },
                },
                { returnDocument: "after" }
            );
            const event = claimResult?.value !== undefined ? claimResult.value : claimResult;
            if (!event) {
                const existingEvent = await db.collection("network_events").findOne({ _id: eventObjectId });
                if (!existingEvent) {
                    log.info(`Targeted event ${eventId} NOT FOUND in network_events. Reverting...`);
                    return next();
                }

                if (existingEvent.status === "synced") {
                    return forwardToLocalJava(req, res, `Targeted event ${eventId} is already synced. Requesting local Java data`);
                }

                if (existingEvent.status === "syncing") {
                    log.info(`Targeted event ${eventId} is already syncing. Waiting for completion.`);
                    const settledEvent = await waitForNetworkEventToSettle(db, eventObjectId);
                    if (settledEvent?.status === "synced") {
                        return forwardToLocalJava(req, res, `Targeted event ${eventId} finished syncing. Requesting local Java data`);
                    }
                    if (settledEvent?.status === "pending") {
                        return res.status(409).json({
                            error: "Network event sync failed and was released for retry",
                            eventId,
                            status: settledEvent.status,
                            cacheStatus: settledEvent.cacheStatus,
                            cacheError: settledEvent.cacheError,
                            syncError: settledEvent.syncError,
                        });
                    }
                    return res.status(202).json({
                        error: "Network event sync already in progress",
                        eventId,
                        status: settledEvent?.status || existingEvent.status,
                        cacheStatus: settledEvent?.cacheStatus || existingEvent.cacheStatus,
                    });
                }

                return res.status(409).json({
                    error: "Network event is not pending",
                    eventId,
                    status: existingEvent.status,
                    cacheStatus: existingEvent.cacheStatus,
                });
            }
            log.info(`Claimed targeted event in network_events: ${eventId}`);

            const localProvider = payer.publicKey.toBase58();
            if (event.provider === localProvider) {
                log.warn(`Targeted event ${eventId} was published by this clone (${localProvider.slice(0, 8)}...). Marking synced and serving local Java data without provider payment.`);
                await db.collection("network_events").updateOne(
                    { _id: event._id },
                    {
                        $set: {
                            status: "synced",
                            syncSkippedReason: "self-provider",
                            syncedAt: new Date(),
                            updatedAt: new Date(),
                        }
                    }
                );

                return forwardToLocalJava(req, res, "Requesting local Java data for self-owned event");
            }

            const providerDoc = await db.collection("network_providers").findOne({ provider: event.provider });
            const baseUrl = providerDoc?.baseUrl;
            if (!baseUrl) {
                await db.collection("network_events").updateOne(
                    { _id: event._id, status: "syncing" },
                    {
                        $set: {
                            status: "pending",
                            syncError: "Provider baseUrl not found",
                            updatedAt: new Date(),
                        },
                    }
                );
                return next();
            }

            const { proxyToProvider } = await import("./proxy.js");
            const query = { ...req.query };
            delete query.network_event_id;
            delete query._java_proxied;
            const providerHeaders: Record<string, string> = {};
            const authorization = req.headers["authorization"];
            if (typeof authorization === "string") providerHeaders.Authorization = authorization;

            const proxyResult = await proxyToProvider(
                [{ provider: event.provider, baseUrl, event }],
                req.path,
                query,
                db,
                payer,
                connection,
                undefined,
                undefined,
                req.method,
                req.body,
                providerHeaders
            );

            if (proxyResult) return res.json(proxyResult.data);
            await db.collection("network_events").updateOne(
                { _id: event._id, status: "syncing" },
                {
                    $set: {
                        status: "pending",
                        syncError: "Upstream provider failed during internal lookup",
                        updatedAt: new Date(),
                    },
                }
            );
            return res.status(504).json({ error: "Upstream provider failed during internal lookup" });
        }

        const orbisKey = req.headers["x-orbis-key"] as string;
        if (!orbisKey || !isValidOrbisKey(orbisKey)) {
            return res.status(401).json({ error: "Valid X-Orbis-Key required" });
        }

        const batchMatch = req.path.match(/^\/v3\/batches\/([^/]+)$/);
        if (batchMatch) {
            const batchId = Number(batchMatch[1]);
            if (!Number.isInteger(batchId)) return res.status(400).json({ error: "Invalid batch id" });
            const provider = (req.query.provider as string) || payer.publicKey.toBase58();
            if (provider !== payer.publicKey.toBase58()) {
                log.warn(`Batch provider mismatch for ${batchId}: requested ${provider}, local ${payer.publicKey.toBase58()}`);
                return res.status(409).json({ error: "Batch provider mismatch", requestedProvider: provider, localProvider: payer.publicKey.toBase58() });
            }
            const batch = await db.collection("network_batches").findOne({ provider, batchId });
            if (!batch?.payload) return res.status(404).json({ error: "batch not found" });
            const jsonBuf = Buffer.from(JSON.stringify(batch.payload));
            const fee = await calculateFee(connection, jsonBuf.length);
            const requester = req.query.requester as string;
            const escrow = req.query.escrow as string;
            if (requester && escrow) {
                (async () => {
                    try {
                        const wallet = new anchor.Wallet(payer);
                        const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
                        const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);
                        const escrowData: any = await program.account.streamingEscrow.fetch(new PublicKey(escrow));
                        const escrowRequester = escrowData.requester.toBase58();
                        startVoucherTimer(escrowRequester, escrow, fee.toString(), connection, payer);
                    } catch (err: any) {
                        log.warn(`Failed to start voucher timer: ${err.message}`);
                    }
                })();
            }
            return res.json({ data: jsonBuf.toString("base64"), requiredFee: fee.toString() });
        }

        if (req.path === "/fetch-by-hash") {
            const { collection, kh } = req.query as { collection: string; kh: string };
            if (!collection || !kh) return res.status(400).json({ error: "missing params" });
            const doc = await db.collection(collection).findOne({ _kh: kh });
            if (!doc) return res.status(404).json({ error: "not found" });
            const jsonBuf = Buffer.from(JSON.stringify(doc));
            const fee = await calculateFee(connection, jsonBuf.length);
            const requester = req.query.requester as string;
            const escrow = req.query.escrow as string;
            if (requester && escrow) {
                (async () => {
                    try {
                        const wallet = new anchor.Wallet(payer);
                        const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
                        const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);
                        const escrowData: any = await program.account.streamingEscrow.fetch(new PublicKey(escrow));
                        const escrowRequester = escrowData.requester.toBase58();
                        startVoucherTimer(escrowRequester, escrow, fee.toString(), connection, payer);
                    } catch (err: any) {
                        log.warn(`Failed to start voucher timer: ${err.message}`);
                    }
                })();
            }
            return res.json({ data: jsonBuf.toString("base64"), requiredFee: fee.toString() });
        }

        log.info(`[Provider Mode] Intercepting request for local data: ${req.path}`);
        try {
            const javaUrl = `${JAVA_BACKEND_URL}${req.path}?_java_proxied=true`;
            const javaQuery: any = { ...req.query };
            delete javaQuery._internal;

            log.info(`Requesting data from Java: ${javaUrl} [${req.method}]`);
            const javaHeaders: Record<string, string> = { "X-API-Key": buildLocalApiKey() };
            const authorization = req.headers["authorization"];
            if (typeof authorization === "string") javaHeaders.Authorization = authorization;
            const javaRequestOptions = {
                params: javaQuery,
                headers: javaHeaders,
                responseType: "arraybuffer" as const,
                timeout: JAVA_BACKEND_TIMEOUT_MS,
                timeoutErrorMessage: `Java backend timed out after ${JAVA_BACKEND_TIMEOUT_MS}ms`,
            };
            const javaRes = req.method === "POST" || req.method === "PUT"
                ? await axios({ method: req.method.toLowerCase(), url: javaUrl, data: req.body, ...javaRequestOptions, headers: { ...javaHeaders, "Content-Type": "application/json" } })
                : await axios.get(javaUrl, javaRequestOptions);
            const binaryData = javaRes.data;

            const requester = req.query.requester as string;
            if (!requester) {
                log.warn(`Missing requester in query for path: ${req.path}`);
                return res.status(403).json({ error: "Requester must identify themselves via ?requester=" });
            }

            const fee = await calculateFee(connection, binaryData.length);
            log.info(`Data fetched from Java. Size: ${binaryData.length} bytes. Required Fee: ${fee.toString()}`);

            const escrow = req.query.escrow as string;

            if (escrow) {
                (async () => {
                    try {
                        const wallet = new anchor.Wallet(payer);
                        const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
                        const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);

                        const escrowData: any = await program.account.streamingEscrow.fetch(new PublicKey(escrow));
                        const escrowRequester = escrowData.requester.toBase58();

                        startVoucherTimer(escrowRequester, escrow, fee.toString(), connection, payer);
                    } catch (err: any) {
                        log.warn(`Failed to start voucher timer: ${err.message}`);
                    }
                })();
            }

            return res.json({
                data: Buffer.from(binaryData).toString("base64"),
                requiredFee: fee.toString()
            });
        } catch (e: any) {
            const message = describeError(e);
            log.error(`Provider error forwarding to Java backend: ${message}`);
            if (e.response) {
                return res.status(e.response.status).send(e.response.data);
            }
            if (e.code === "ECONNABORTED" || e.code === "ETIMEDOUT") {
                return res.status(504).json({ error: "Java backend timeout", details: message });
            }
            return res.status(500).json({ error: message });
        }
    });

    app.get("/network/status", async (req, res) => {
        try {
            const syncState = await db.collection("network_sync_state").findOne({ _id: "last_scan" as any });
            const lastIndexedSignature = syncState?.lastSignature || null;

            const latestSigs = await connection.getSignaturesForAddress(MERKLE_TREE_MINT, { limit: 1 });
            const latestOnChainSignature = latestSigs.length > 0 ? latestSigs[0].signature : null;

            const isCaughtUp = lastIndexedSignature === latestOnChainSignature;

            const events = await db.collection("network_events").find({}).toArray();

            const stats = {
                total: events.length,
                pending: events.filter(e => e.status === "pending").length,
                synced: events.filter(e => e.status === "synced").length,
                byProvider: {} as Record<string, any>
            };

            for (const ev of events) {
                const prov = ev.provider;
                if (!stats.byProvider[prov]) {
                    stats.byProvider[prov] = { total: 0, pending: 0, synced: 0 };
                }
                stats.byProvider[prov].total++;
                if (ev.status === "pending") stats.byProvider[prov].pending++;
                if (ev.status === "synced") stats.byProvider[prov].synced++;
            }

            res.json({
                blockchain: {
                    lastIndexedSignature,
                    latestOnChainSignature,
                    isCaughtUp
                },
                events: stats
            });
        } catch (e: any) {
            res.status(500).json({ error: e.message });
        }
    });

    registerV3Routes(app, db, payer, connection);
    registerAuthRoutes(app, db, payer, connection);
    registerProfileRoutes(app, db, payer, connection);
    registerGroupsRoutes(app, db, payer, connection);
    registerPostsRoutes(app, db, payer, connection);
    registerPlacesRoutes(app, db, payer, connection);
    registerFeedRoutes(app, db, payer, connection);
    registerMiscRoutes(app, db, payer, connection);
    registerPolygonRoutes(app, db, payer, connection);
    registerPaymentsRoutes(app, db, payer, connection);

    app.use((err: any, req: Request, res: Response, next: NextFunction) => {
        const message = describeError(err);
        log.error(`Unhandled request error ${req.method} ${req.url}: ${message}`);
        if (res.headersSent) return next(err);
        res.status(500).json({ error: message });
    });

    const server = app.listen(PORT, () => {
        log.info(`Clone Proxy running on port ${PORT}`);
    });
    server.on("error", (err: any) => {
        if (err?.code === "EADDRINUSE") {
            log.error(`Port ${PORT} is already in use — is another clone already running?`);
        } else {
            log.error(`Server error: ${describeError(err)}`);
        }
        process.exit(1);
    });
}

main().catch((err) => { log.error(`Fatal error: ${err.message}`); process.exit(1); });
