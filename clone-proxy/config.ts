import * as dotenv from "dotenv";
import * as path from "path";
import { fileURLToPath } from "url";
import { Connection, PublicKey } from "@solana/web3.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "./.env") });

const REQUIRED_ENV_VARS = [
    "RPC_URL",
    "PROGRAM_ID",
    "MERKLE_TREE_MINT",
    "CLONE_PORT",
    "JAVA_BACKEND_URL",
    "CLONE_BASE_URL",
];

const missing = REQUIRED_ENV_VARS.filter((k) => !process.env[k]);
if (missing.length > 0) {
    console.error(`[Config] Missing required environment variables: ${missing.join(", ")}`);
    console.error(`[Config] Please set them in your .env file before starting.`);
    process.exit(1);
}

export const PORT = parseInt(process.env.CLONE_PORT!);
export const JAVA_BACKEND_URL = process.env.JAVA_BACKEND_URL!;
export const CLONE_BASE_URL = process.env.CLONE_BASE_URL!;
export const RPC_URL = process.env.RPC_URL!;
export const PROGRAM_ID = new PublicKey(process.env.PROGRAM_ID!);
export const MERKLE_TREE_MINT = new PublicKey(process.env.MERKLE_TREE_MINT!);
export const ORBIS_MINT = new PublicKey(process.env.ORBIS_MINT || "GkEwz1aZTTmJ4Xao3WfA3UEhBhPGrfZQ6VSMQ3xcQzfp");

export const COLLECTION_ORDER = [
    "users", "groups", "userPurchase", "stripeAccount", "stripeTransfer",
    "notifications", "campaigns", "post_templates", "userPicture", "follows",
    "igLink", "posts", "eventAttendees", "placeRates", "places", "payment",
    "comments", "subscription", "checkins", "storiesSeen", "userSets",
    "partner", "userSubscription", "emails", "phones", "polygons",
    "polygonSchedulerCoordinate", "reports", "stories",
];

export function createConnection(): Connection {
    return new Connection(RPC_URL, "confirmed");
}
