// HTTP surface for tribe crypto payments. Both endpoints are INTERNAL: they are
// called server-to-server by the Java backend with ?_internal=true and the
// X-Master-Key header (validated by the global middleware in index.ts). The real
// logic lives in network/tribe-payment.ts.
import { createTribePaymentIntent, getTribePaymentStatus } from "../network/tribe-payment.js";
import { log } from "../logger.js";

export function registerPaymentsRoutes(app: any, db: any, payer: any, _connection: any) {
    // POST /payments/intent?_internal=true
    // body: { userKey, kind, itemKey, quantity, payerWallet, ownerWallet, priceUsd, clonePayoutWallet? }
    app.post("/payments/intent", async (req: any, res: any) => {
        try {
            const b = req.body || {};
            if (!b.payerWallet || !b.ownerWallet || b.priceUsd == null) {
                return res.status(400).json({ error: "payerWallet, ownerWallet and priceUsd are required" });
            }
            const result = await createTribePaymentIntent(db, {
                userKey: b.userKey,
                kind: b.kind === "purchase" ? "purchase" : "subscription",
                itemKey: b.itemKey,
                quantity: b.quantity,
                payerWallet: b.payerWallet,
                ownerWallet: b.ownerWallet,
                priceUsd: Number(b.priceUsd),
                clonePayoutWallet: b.clonePayoutWallet,
                operatorWallet: payer.publicKey.toBase58(),
            });
            return res.json(result);
        } catch (e: any) {
            log.error(`[payments] intent failed: ${e.message}`);
            return res.status(500).json({ error: e.message });
        }
    });

    // GET /payments/status/:ref?_internal=true
    app.get("/payments/status/:ref", async (req: any, res: any) => {
        const status = await getTribePaymentStatus(db, req.params.ref);
        if (!status) return res.status(404).json({ error: "unknown payment ref" });
        return res.json(status);
    });
}
