import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { findProviders } from "../network/providers.js";
import { proxyToProvider } from "../proxy.js";

export function registerPostsRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/posts/:postKey/comments/:commentKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "comments", "key", req.params.commentKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/posts/${req.params.postKey}/comments/${req.params.commentKey}`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.status(404).json({ error: "Comment not found" });
    });

    app.get("/posts/:postKey/comments", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "comments", "parent", req.params.postKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/posts/${req.params.postKey}/comments`, req.query, db, payer, connection, "parent", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });

    app.get("/posts/:postKey", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "posts", "key", req.params.postKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/posts/${req.params.postKey}`, {}, db, payer, connection, "key", hashValue);
            if (result) return res.json({ ...result.data, _source: result.source, _provider: result.provider });
        }
        res.status(404).json({ error: "Post not found" });
    });

    app.get("/events/:postKey/attendees", async (req, res) => {
        const { providers, hashValue } = await findProviders(db, "eventAttendees", "key", req.params.postKey);
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, `/events/${req.params.postKey}/attendees`, req.query, db, payer, connection, "key", hashValue);
            if (result) return res.json(result.data);
        }
        res.json([]);
    });
}
