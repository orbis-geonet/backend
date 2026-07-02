import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { findProviders } from "../network/providers.js";
import { proxyToProvider } from "../proxy.js";

export function registerPlacesRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/places/slug/:slug", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "places", "secondary", req.params.slug);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/places/slug/${req.params.slug}`, req.query, db, payer, connection, "secondary", hashValue);
            if (result) return res.json({ ...result.data, _source: result.source, _provider: result.provider });
        }
        res.status(404).json({ error: "Place not found" });
    });

    app.get("/places/:placeKey/events", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "posts", "parent2", req.params.placeKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/places/${req.params.placeKey}/events`, req.query, db, payer, connection, "parent2", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/places/:placeKey/followers", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "follows", "parent", req.params.placeKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/places/${req.params.placeKey}/followers`, req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/places/:placeKey/rate", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "placeRates", "key", req.params.placeKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/places/${req.params.placeKey}/rate`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/places/:placeKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "places", "key", req.params.placeKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/places/${req.params.placeKey}`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json({ ...result.data, _source: result.source, _provider: result.provider });
        }
        res.status(404).json({ error: "Place not found" });
    });

    app.get("/places", async (req, res) => {
        const { name, latitude, longitude, ownedByGroupKey } = req.query;
        if (ownedByGroupKey) {
            const { providers, hashValue } = await findProviders(db, "places", "parent", ownedByGroupKey as string);
            if (providers.length > 0) {
                const result = await proxyToProvider(providers, "/places", req.query, db, payer, connection, "parent", hashValue);
                if (result) return res.json(result.data);
            }
            return res.json([]);
        }
        if (name) {
            const { providers, hashValue } = await findProviders(db, "places", "name", name as string);
            if (providers.length > 0) {
                const result = await proxyToProvider(providers, "/places", req.query, db, payer, connection, "name", hashValue);
                if (result) return res.json(result.data);
            }
            return res.json([]);
        }
        if (latitude && longitude) {
            const lat = parseFloat(latitude as string);
            const lon = parseFloat(longitude as string);
            const { providers, hashValue } = await findProviders(db, "places", "geo", "", lat, lon);
            if (providers.length > 0) {
                const result = await proxyToProvider(providers, "/places", req.query, db, payer, connection, "geo", hashValue);
                if (result) return res.json(result.data);
            }
            return res.json([]);
        }
        res.json([]);
    });

    app.get("/places-info", async (req, res) => {
        const { latitude, longitude } = req.query;
        if (!latitude || !longitude) return res.status(400).json({ error: "latitude and longitude required" });
        const lat = parseFloat(latitude as string);
        const lon = parseFloat(longitude as string);
        const { providers, hashValue } = await findProviders(db, "places", "geo", "", lat, lon);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/places-info", req.query as any, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Place info not found" });
    });
}
