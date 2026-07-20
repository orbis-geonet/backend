import * as dotenv from "dotenv";
import * as path from "path";
import { fileURLToPath } from "url";
import { Connection, PublicKey } from "@solana/web3.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
dotenv.config({ path: path.resolve(__dirname, "./.env"), quiet: true });

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

export const COLLECTION_ID_MAP = new Map(COLLECTION_ORDER.map((name, idx) => [name, idx]));

export interface CollectionMeta {
    primaryKey: string;
    secondaryKey?: string;
    nameField?: string;
    parentKey?: string;
    parentKey2?: string;
    authorField?: string;
    geoField?: string;
}

export const COLLECTION_META: Record<string, CollectionMeta> = {
    'users': { primaryKey: 'userKey', secondaryKey: 'slug', nameField: 'displayName', authorField: 'email', geoField: 'coordinates' },
    'groups': { primaryKey: 'groupKey', secondaryKey: 'slug', nameField: 'name', geoField: 'location' },
    'userPurchase': { primaryKey: 'userPurchaseKey', parentKey: 'userKey' },
    'stripeAccount': { primaryKey: 'stripeAccountKey', parentKey: 'userKey' },
    'stripeTransfer': { primaryKey: 'transferStripeKey', parentKey: 'userKey' },
    'notifications': { primaryKey: 'notificationKey', parentKey: 'forUserKey' },
    'campaigns': { primaryKey: 'name', nameField: 'name' },
    'post_templates': { primaryKey: 'title', parentKey: 'userKey' },
    'userPicture': { primaryKey: 'pictureKey', parentKey: 'userKey' },
    'follows': { primaryKey: 'followerKey', parentKey: 'followingKey', authorField: 'followerKey' },
    'igLink': { primaryKey: 'userKey' },
    'posts': { primaryKey: 'postKey', parentKey: 'groupKey', parentKey2: 'placeKey', authorField: 'userKey', geoField: 'coordinates' },
    'eventAttendees': { primaryKey: 'postKey', parentKey: 'userKey' },
    'placeRates': { primaryKey: 'placeKey', parentKey: 'userKey' },
    'places': { primaryKey: 'placeKey', secondaryKey: 'slug', nameField: 'name', parentKey: 'dominantGroupKey', geoField: 'coordinates' },
    'payment': { primaryKey: 'paymentId', parentKey: 'userSubscriptionKey' },
    'comments': { primaryKey: 'commentKey', parentKey: 'postKey', authorField: 'userKey' },
    'subscription': { primaryKey: 'subscriptionKey', nameField: 'name', parentKey: 'groupKey' },
    'checkins': { primaryKey: 'userKey', parentKey: 'placeKey' },
    'storiesSeen': { primaryKey: 'postKey', parentKey: 'userKey' },
    'userSets': { primaryKey: 'name' },
    'partner': { primaryKey: 'partnerKey', parentKey: 'userKey' },
    'userSubscription': { primaryKey: 'userSubscriptionKey', parentKey: 'userKey' },
    'emails': { primaryKey: 'emailKey', secondaryKey: 'email' },
    'phones': { primaryKey: 'phoneKey', secondaryKey: 'phone' },
    'polygons': { primaryKey: 'polygonKey', geoField: 'polygonCenter' },
    'polygonSchedulerCoordinate': { primaryKey: 'polygonSchedulerCoordinateKey' },
    'reports': { primaryKey: 'name' },
    'stories': { primaryKey: 'groupKey' },
};

export const IGNORED_LANE_COLLECTIONS = new Set(['polygonSchedulerCoordinate', 'post_templates', 'notifications', 'igLink']);

export const PUBLIC_LANE_COLLECTIONS = new Set(['users', 'groups', 'posts', 'places', 'comments', 'follows']);

export type Lane = 'public' | 'derived';

export function laneForCollection(name: string): Lane | null {
    if (IGNORED_LANE_COLLECTIONS.has(name)) return null;
    if (!COLLECTION_META[name]) return null;
    return PUBLIC_LANE_COLLECTIONS.has(name) ? 'public' : 'derived';
}

function parsePosIntEnv(value: string | undefined, fallback: number): number {
    if (!value) return fallback;
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export const MANIFEST_TUNABLES = {
    publicFlushCount: parsePosIntEnv(process.env.PUBLIC_FLUSH_COUNT, 200),
    publicFlushAgeMs: parsePosIntEnv(process.env.PUBLIC_FLUSH_AGE_MS, 120_000),
    derivedFlushCount: parsePosIntEnv(process.env.DERIVED_FLUSH_COUNT, 500),
    derivedFlushAgeMs: parsePosIntEnv(process.env.DERIVED_FLUSH_AGE_MS, 600_000),
    flushTickMs: parsePosIntEnv(process.env.MANIFEST_FLUSH_TICK_MS, 15_000),
};

export const SELF_CONSUME = process.argv.includes("--self-consume") || process.env.SELF_CONSUME === "true";
