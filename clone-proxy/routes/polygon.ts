import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { findProviders } from "../network/providers.js";
import { proxyToProvider } from "../proxy.js";

export function registerPolygonRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/polygon-calculations/polygons-page", async (req, res) => {
        const { latitude, longitude } = req.query;
        if (!latitude || !longitude) return res.status(400).json({ error: "latitude and longitude required" });
        const { providers, hashValue } = await findProviders(db, "polygons", "geo", "", parseFloat(latitude as string), parseFloat(longitude as string));
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/polygon-calculations/polygons-page", req.query as any, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Polygon data not found" });
    });

    app.get("/polygon-calculations/polygons", async (req, res) => {
        const { latitude, longitude } = req.query;
        if (!latitude || !longitude) return res.status(400).json({ error: "latitude and longitude required" });
        const { providers, hashValue } = await findProviders(db, "polygons", "geo", "", parseFloat(latitude as string), parseFloat(longitude as string));
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/polygon-calculations/polygons", req.query as any, db, payer, connection, "geo", hashValue);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Polygon data not found" });
    });

    app.get("/polygon-calculations/polygons/:placeKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "polygons", "key", req.params.placeKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/polygon-calculations/polygons/${req.params.placeKey}`, {}, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Polygon data not found" });
    });

    app.get("/polygon-calculations/trigger", async (req, res) => {
        const { providers } = await findProviders(db, "polygons", "misc", "");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/polygon-calculations/trigger", {}, db, payer, connection);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Trigger failed" });
    });

    app.get("/polygon-calculations/trigger-one-point", async (req, res) => {
        const { providers } = await findProviders(db, "polygons", "misc", "");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/polygon-calculations/trigger-one-point", req.query as any, db, payer, connection);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Trigger failed" });
    });

    app.get("/polygon-calculations/:key", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "polygonSchedulerCoordinate", "key", req.params.key);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/polygon-calculations/${req.params.key}`, {}, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Scheduler status not found" });
    });
}
