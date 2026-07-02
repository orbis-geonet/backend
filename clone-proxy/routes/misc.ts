import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { findProviders } from "../network/providers.js";
import { proxyToProvider } from "../proxy.js";

export function registerMiscRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/notifications", async (req, res) => {
        const userKey = req.headers["x-user-key"] as string;
        if (!userKey) return res.status(401).json({ error: "x-user-key header required" });
        const { providers, hashValue } = await findProviders(db, "notifications", "parent", userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/notifications", req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/partner", async (req, res) => {
        const userKey = req.headers["x-user-key"] as string;
        if (!userKey) return res.status(401).json({ error: "x-user-key header required" });
        const { providers, hashValue } = await findProviders(db, "partner", "parent", userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/partner", req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Partner not found" });
    });

    app.get("/checkins", async (req, res) => {
        const userKey = req.headers["x-user-key"] as string;
        if (!userKey) return res.status(401).json({ error: "x-user-key header required" });
        const { providers, hashValue } = await findProviders(db, "checkins", "key", userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/checkins", req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });
}
