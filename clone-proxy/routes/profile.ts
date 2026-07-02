import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { findProviders } from "../network/providers.js";
import { proxyToProvider } from "../proxy.js";

export function registerProfileRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/profile/me", async (req, res) => {
        res.status(401).json({ error: "Auth required for /me" });
    });

    app.get("/profile/blockedUsers", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "users", "key", "blockedUsers");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/profile/blockedUsers", req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/blockedByUsers", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "users", "key", "blockedByUsers");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/profile/blockedByUsers", req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/me/followers/pending", async (req, res) => {
        res.status(401).json({ error: "Auth required for /me/followers/pending" });
    });

    app.get("/profile/purchases", async (req, res) => {
        const userKey = req.headers["x-user-key"] as string;
        if (!userKey) return res.status(401).json({ error: "x-user-key header required" });
        const { providers, hashValue } = await findProviders(db, "userPurchase", "parent", userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/profile/purchases", req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/subscriptions", async (req, res) => {
        const userKey = req.headers["x-user-key"] as string;
        if (!userKey) return res.status(401).json({ error: "x-user-key header required" });
        const { providers, hashValue } = await findProviders(db, "userSubscription", "parent", userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/profile/subscriptions", req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/slug/:slug", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "users", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/slug/${req.params.slug}`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json({ ...result.data, _source: result.source, _provider: result.provider });
        }
        res.status(404).json({ error: "User not found" });
    });

    app.get("/profile/slug/:slug/pictures", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "userPicture", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/slug/${req.params.slug}/pictures`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/slug/:slug/igpictures", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "userPicture", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/slug/${req.params.slug}/igpictures`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/slug/:slug/following", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "follows", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/slug/${req.params.slug}/following`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/slug/:slug/followers", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "follows", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/slug/${req.params.slug}/followers`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/slug/:slug/member", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/slug/${req.params.slug}/member`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/slug/:slug/groupFollower", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/slug/${req.params.slug}/groupFollower`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey/followers", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "follows", "parent", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}/followers`, req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey/following", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "follows", "key", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}/following`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey/pictures", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "userPicture", "parent", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}/pictures`, req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey/igpictures", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "userPicture", "parent", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}/igpictures`, req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey/admin", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "key", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}/admin`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey/member", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "key", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}/member`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey/groupFollower", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "key", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}/groupFollower`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/profile/:userKey", async (req, res) => {
        if (req.params.userKey === "me") return res.status(401).json({ error: "Auth required for /me" });
        const { providers, hashValue } = await findProviders(db, "users", "key", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/profile/${req.params.userKey}`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json({ ...result.data, _source: result.source, _provider: result.provider });
        }
        res.status(404).json({ error: "User not found" });
    });

    app.get("/profile", async (req, res) => {
        const name = req.query.name as string;
        if (!name) return res.status(400).json({ error: "name parameter required" });
        const { providers, hashValue } = await findProviders(db, "users", "name", name);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/profile", req.query, db, payer, connection, "name", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.post("/profile/chatusers", async (req, res) => {
        const userKeys: string[] = req.body;
        if (!Array.isArray(userKeys) || userKeys.length === 0) return res.json([]);
        const { providers, hashValue } = await findProviders(db, "users", "key", userKeys[0]);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/profile/chatusers", req.query, db, payer, connection, "key", hashValue, "POST", req.body);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });
}
