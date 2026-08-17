import { createTribePaymentIntent, createSolPaymentIntent, getTribePaymentStatus } from "../network/tribe-payment.js";
import { log } from "../logger.js";
import { randomBytes } from "node:crypto";

function short(value: unknown): string {
    const text = String(value || "");
    if (!text) return "-";
    return text.length <= 12 ? text : `${text.slice(0, 6)}...${text.slice(-4)}`;
}

function errorDetails(e: any): string {
    if (!e) return "unknown error";
    return e.stack || e.message || String(e);
}

export function registerPaymentsRoutes(app: any, db: any, payer: any, _connection: any) {
    app.post("/payments/intent", async (req: any, res: any) => {
        const trace = randomBytes(4).toString("hex");
        try {
            const b = req.body || {};
            const coin = String(b.coin || "ORBIS").toUpperCase();
            const amountSol = b.amountSol ?? b.priceSol ?? b.solPrice;
            log.info(`[payments:intent:${trace}] received coin=${coin} kind=${b.kind || "subscription"} item=${short(b.itemKey)} quantity=${b.quantity ?? 1} payer=${short(b.payerWallet)} owner=${short(b.ownerWallet)} amountSol=${amountSol ?? "-"} priceUsd=${b.priceUsd ?? "-"}`);
            if (!b.payerWallet || !b.ownerWallet || (coin === "SOL" ? amountSol == null : b.priceUsd == null)) {
                log.warn(`[payments:intent:${trace}] rejected missing required fields coin=${coin}`);
                return res.status(400).json({ error: coin === "SOL" ? "payerWallet, ownerWallet and amountSol are required" : "payerWallet, ownerWallet and priceUsd are required", trace });
            }
            const params = {
                traceId: trace,
                userKey: b.userKey,
                kind: (b.kind === "purchase" ? "purchase" : "subscription") as "subscription" | "purchase",
                itemKey: b.itemKey,
                quantity: b.quantity,
                payerWallet: b.payerWallet,
                ownerWallet: b.ownerWallet,
                priceUsd: b.priceUsd == null ? undefined : Number(b.priceUsd),
                amountSol,
                clonePayoutWallet: b.clonePayoutWallet,
                operatorWallet: payer.publicKey.toBase58(),
            };
            const result = coin === "SOL"
                ? await createSolPaymentIntent(db, params)
                : await createTribePaymentIntent(db, params);
            if (result.error) {
                log.warn(`[payments:intent:${trace}] completed with app error=${result.error} message=${result.message || "-"}`);
            } else {
                log.info(`[payments:intent:${trace}] completed ref=${short(result.ref)} coin=${result.coin} amountOrbis=${result.amountOrbis} amountSol=${"amountSol" in result ? result.amountSol : "-"} txBytes=${result.unsignedTxBase64 ? Buffer.from(result.unsignedTxBase64, "base64").length : 0}`);
            }
            return res.json(result);
        } catch (e: any) {
            log.error(`[payments:intent:${trace}] failed ${errorDetails(e)}`);
            return res.status(500).json({ error: e.message, trace });
        }
    });

    app.get("/payments/status/:ref", async (req: any, res: any) => {
        log.info(`[payments:status] lookup ref=${short(req.params.ref)}`);
        const status = await getTribePaymentStatus(db, req.params.ref);
        if (!status) return res.status(404).json({ error: "unknown payment ref" });
        log.info(`[payments:status] ref=${short(req.params.ref)} status=${status.status} coin=${status.coin}`);
        return res.json(status);
    });
}
