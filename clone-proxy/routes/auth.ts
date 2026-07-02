import { Express } from "express";
import { Connection, Keypair } from "@solana/web3.js";
import { proxyToProvider } from "../proxy.js";
import { findProviders } from "../network/providers.js";
import { log } from "../logger.js";

export function registerAuthRoutes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.post("/login", async (req, res) => {
        const { email, password } = req.body ?? {};
        if (!email) return res.status(400).json({ code: 400, message: "Email required" });
        if (!password) return res.status(400).json({ code: 400, message: "Password required" });

        log.info(`[Auth] Login attempt for ${email}`);

        const eventId = req.query.network_event_id as string | undefined;
        const { providers, hashValue } = await findProviders(db, "users", "email", email, undefined, undefined, eventId);

        if (providers.length > 0) {
            const loginResult = await proxyToProvider(
                providers,
                "/login",
                req.query,
                db,
                payer,
                connection,
                "email",
                hashValue,
                "POST",
                { email, password }
            );

            if (loginResult) {
                const loginData = loginResult.data;
                log.info(`[Auth] Login success from provider. UserKey: ${loginData?.userKey}`);
                return res.json(loginData);
            }
        }

        res.status(401).json({ code: 401, message: "Invalid credentials" });
    });
    /*
    app.post("/signup", async (req, res) => {
        const { email, password } = req.body ?? {};
        if (!email) return res.status(400).json({ code: 400, message: "Email required" });
        if (!password) return res.status(400).json({ code: 400, message: "Password required" });

        log.info(`[Auth] Signup attempt for ${email}`);

        const eventId = req.query.network_event_id as string | undefined;
        const { providers, hashValue } = await findProviders(db, "users", "email", email, undefined, undefined, eventId);

        if (providers.length > 0) {
            const signupResult = await proxyToProvider(
                providers,
                "/signup",
                req.query,
                db,
                payer,
                connection,
                "email",
                hashValue,
                "POST",
                req.body
            );

            if (signupResult) {
                log.info(`[Auth] Signup success from provider`);
                return res.json(signupResult.data);
            }
        }

        res.status(400).json({ code: 400, message: "Registration failed" });
    });
    */
    app.post("/refresh", async (req, res) => {
        const { providers } = await findProviders(db, "users", "key", "refresh");
        if (providers.length > 0) {
            const result = await proxyToProvider(providers, "/refresh", req.query, db, payer, connection, undefined, undefined, "POST", req.body);
            if (result) return res.json(result.data);
        }
        res.status(401).json({ code: 401, message: "Refresh failed" });
    });
}
