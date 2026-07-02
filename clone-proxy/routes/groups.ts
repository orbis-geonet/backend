import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { findProviders } from "../network/providers.js";
import { proxyToProvider } from "../proxy.js";

export function registerGroupsRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/groups/rating", async (req, res) => {
        const { latitude, longitude } = req.query;
        if (!latitude || !longitude) return res.status(400).json({ error: "latitude and longitude required" });
        const lat = parseFloat(latitude as string);
        const lon = parseFloat(longitude as string);
        const { providers, hashValue } = await findProviders(db, "groups", "geo", "", lat, lon);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/groups/rating", req.query, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/groups/slug/:slug", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/groups/slug/${req.params.slug}`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json({ ...result.data, _source: result.source, _provider: result.provider, _verified: result.verified });
        }
        res.status(404).json({ error: "Group not found" });
    });

    app.get("/groups/subscription/slug/:slug", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "secondary", req.params.slug);
        const result = await proxyToProvider(providers, `/groups/subscription/slug/${req.params.slug}`, req.query, db, payer, connection, "secondary", hashValue);
        if (result) return res.json(result.data);
        res.json([]);
    });

    app.get("/groups/subscription/one/:subscriptionKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "subscription", "key", req.params.subscriptionKey);
        const result = await proxyToProvider(providers, `/groups/subscription/one/${req.params.subscriptionKey}`, req.query, db, payer, connection, "key", hashValue);
        if (result) return res.json(result.data);
        res.status(404).json({ error: "Subscription not found" });
    });

    app.get("/groups/subscription/:groupKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "subscription", "parent", req.params.groupKey);
        const result = await proxyToProvider(providers, `/groups/subscription/${req.params.groupKey}`, req.query, db, payer, connection, "parent", hashValue);
        if (result) return res.json(result.data);
        res.json([]);
    });

    app.get("/groups/:groupKey/members", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "key", req.params.groupKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/groups/${req.params.groupKey}/members`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/groups/:groupKey/followers", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "follows", "parent", req.params.groupKey);
        const result = await proxyToProvider(providers, `/groups/${req.params.groupKey}/followers`, req.query, db, payer, connection, "parent", hashValue);
        if (result) return res.json(result.data);
        res.json([]);
    });

    app.get("/groups/:groupKey/admins", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "key", req.params.groupKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/groups/${req.params.groupKey}/admins`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/groups/:groupKey/events", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "posts", "parent", req.params.groupKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/groups/${req.params.groupKey}/events`, req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/groups/:groupKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "key", req.params.groupKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/groups/${req.params.groupKey}`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json({ ...result.data, _source: result.source, _provider: result.provider, _verified: result.verified });
        }
        res.status(404).json({ error: "Group not found" });
    });

    app.get("/groups", async (req, res) => {
        const { name, latitude, longitude } = req.query;
        if (name) {
            const { providers, hashValue } = await findProviders(db, "groups", "name", name as string);
            if (providers.length > 0) {
                const result = await proxyToProvider(providers, "/groups", req.query, db, payer, connection, "name", hashValue);
                if (result) return res.json(result.data);
            }
            return res.json([]);
        }
        if (latitude && longitude) {
            const lat = parseFloat(latitude as string);
            const lon = parseFloat(longitude as string);
            const { providers, hashValue } = await findProviders(db, "groups", "geo", "", lat, lon);
            if (providers.length > 0) {
                const result = await proxyToProvider(providers, "/groups", req.query, db, payer, connection, "geo", hashValue);
                if (result) return res.json(result.data);
            }
            return res.json([]);
        }
        res.json([]);
    });
}
