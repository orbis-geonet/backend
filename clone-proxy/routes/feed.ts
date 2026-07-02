import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { findProviders } from "../network/providers.js";
import { proxyToProvider } from "../proxy.js";

export function registerFeedRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/feed/all", async (req, res) => {
        const { providers } = await findProviders(db, "posts", "key", "all");
        if (providers.length > 0) {
            const proxyRes = await proxyToProvider(providers, "/feed/all", req.query as any, db, payer, connection);
            if (proxyRes?.data) return res.json(proxyRes.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/city", async (req, res) => {
        const { longitude, latitude } = req.query;
        if (!longitude || !latitude) return res.status(400).json({ error: "longitude and latitude required" });
        const { providers, hashValue } = await findProviders(db, "posts", "geo", "", parseFloat(latitude as string), parseFloat(longitude as string));
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/feed/city", req.query as any, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/near", async (req, res) => {
        const { longitude, latitude } = req.query;
        if (!longitude || !latitude) return res.status(400).json({ error: "longitude and latitude required" });
        const { providers, hashValue } = await findProviders(db, "posts", "geo", "", parseFloat(latitude as string), parseFloat(longitude as string));
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/feed/near", req.query as any, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/city/stories", async (req, res) => {
        const { longitude, latitude } = req.query;
        if (!longitude || !latitude) return res.status(400).json({ error: "longitude and latitude required" });
        const { providers, hashValue } = await findProviders(db, "stories", "geo", "", parseFloat(latitude as string), parseFloat(longitude as string));
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/feed/city/stories", req.query as any, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/feed/near/stories", async (req, res) => {
        const { longitude, latitude } = req.query;
        if (!longitude || !latitude) return res.status(400).json({ error: "longitude and latitude required" });
        const { providers, hashValue } = await findProviders(db, "stories", "geo", "", parseFloat(latitude as string), parseFloat(longitude as string));
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/feed/near/stories", req.query as any, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/feed/news", async (req, res) => {
        const { providers } = await findProviders(db, "posts", "key", "news");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/feed/news", req.query as any, db, payer, connection);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/news/stories", async (req, res) => {
        const { providers } = await findProviders(db, "stories", "key", "news");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/feed/news/stories", req.query as any, db, payer, connection);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/feed/user/slug/:slug", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "users", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/feed/user/slug/${req.params.slug}`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/user", async (req, res) => {
        const { providers } = await findProviders(db, "posts", "key", "user");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/feed/user`, req.query, db, payer, connection);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/user/:userKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "posts", "author", req.params.userKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/feed/user/${req.params.userKey}`, req.query, db, payer, connection, "author", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/place/slug/:slug", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "places", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/feed/place/slug/${req.params.slug}`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/place/:placeKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "posts", "parent2", req.params.placeKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/feed/place/${req.params.placeKey}`, req.query, db, payer, connection, "parent2", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/group/slug/:slug", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "groups", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/feed/group/slug/${req.params.slug}`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });

    app.get("/feed/group/:groupKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "posts", "parent", req.params.groupKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/feed/group/${req.params.groupKey}`, req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json({ content: [], nextPage: null });
    });
}
