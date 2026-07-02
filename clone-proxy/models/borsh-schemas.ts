import { createHash } from "crypto";

export async function getCanonicalUserState(db: any, userKey: string) {
    const user = await db.collection("users").findOne({ userKey });
    if (!user) throw new Error(`User ${userKey} not found.`);

    const [
        userPurchases, stripeAccounts, stripeTransfers, groups,
        notifications, campaigns, postTemplates, userPictures,
        follows, igLinks, posts, eventAttendees,
        placeRates, places, userSubscriptions, comments,
        subscriptions, checkins, storiesSeen, userSets, partners,
    ] = await Promise.all([
        db.collection("userPurchase").find({ userKey }).toArray(),
        db.collection("stripeAccount").find({ userKey }).toArray(),
        db.collection("stripeTransfer").find({ userKey }).toArray(),
        db.collection("groups").find({ followers: userKey }).toArray(),
        db.collection("notifications").find({ forUserKey: userKey }).toArray(),
        db.collection("campaigns").find({}).toArray(),
        db.collection("post_templates").find({ userKey }).toArray(),
        db.collection("userPicture").find({ userKey }).toArray(),
        db.collection("follows").find({ followerKey: userKey }).toArray(),
        db.collection("igLink").find({ userKey }).toArray(),
        db.collection("posts").find({ userKey, deleted: { $ne: true } }).toArray(),
        db.collection("eventAttendees").find({ userKey }).toArray(),
        db.collection("placeRates").find({ userKey }).toArray(),
        db.collection("places").find({ userCreatedKey: userKey }).toArray(),
        db.collection("userSubscription").find({ userKey }).toArray(),
        db.collection("comments").find({ userKey, deleted: { $ne: true } }).toArray(),
        db.collection("subscription").find({ createdUserKey: userKey }).toArray(),
        db.collection("checkins").find({ userKey }).toArray(),
        db.collection("storiesSeen").find({ userKey }).toArray(),
        db.collection("userSets").find({ usersKey: userKey }).toArray(),
        db.collection("partner").find({ userKey }).toArray(),
    ]);

    const userSubKeys = userSubscriptions.map((s: any) => s.userSubscriptionKey);
    const payments = await db.collection("payment").find({ userSubscriptionKey: { $in: userSubKeys } }).toArray();

    const userSetIds = userSets.map((s: any) => s._id.toString());
    const filteredCampaigns = campaigns.filter((c: any) =>
        c.usersSetIds && c.usersSetIds.some((id: any) => userSetIds.includes(id.toString()))
    );
    const followedPlaceKeys = [...new Set(follows.map((f: any) => f.placeKey).filter(Boolean))] as string[];
    if (followedPlaceKeys.length > 0) {
        const followedPlaces = await db.collection("places").find({ placeKey: { $in: followedPlaceKeys } }).toArray();
        const existingPlaceKeys = new Set(places.map((p: any) => p.placeKey).filter(Boolean));
        places.push(...followedPlaces.filter((p: any) => !existingPlaceKeys.has(p.placeKey)));
    }

    const stateObj = {
        users: [user],
        userPurchase: userPurchases,
        stripeAccount: stripeAccounts,
        stripeTransfer: stripeTransfers,
        groups: groups,
        notifications: notifications,
        campaigns: filteredCampaigns,
        post_templates: postTemplates,
        userPicture: userPictures,
        follows: follows,
        igLink: igLinks,
        posts: posts,
        eventAttendees: eventAttendees,
        placeRates: placeRates,
        places: places,
        payment: payments,
        comments: comments,
        subscription: subscriptions,
        checkins: checkins,
        storiesSeen: storiesSeen,
        userSets: userSets,
        partner: partners,
        userSubscription: userSubscriptions
    };

    const jsonBuffer = Buffer.from(JSON.stringify(stateObj));
    const hash = createHash("sha256").update(jsonBuffer).digest();
    return { hash: Array.from(hash), state: stateObj, buffer: jsonBuffer };
}

const COLLECTION_KEY_MAP: Record<string, string> = {
    users: "userKey", userPurchase: "userPurchaseKey", stripeAccount: "stripeAccountKey",
    stripeTransfer: "transferStripeKey", groups: "groupKey", notifications: "notificationKey",
    post_templates: "title", userPicture: "pictureKey", follows: "followerKey", igLink: "userKey",
    posts: "postKey", eventAttendees: "postKey", placeRates: "placeKey", places: "placeKey",
    payment: "paymentId", comments: "commentKey", subscription: "subscriptionKey",
    checkins: "userKey", storiesSeen: "postKey", userSets: "name", partner: "partnerKey",
    userSubscription: "userSubscriptionKey", polygons: "polygonKey",
    polygonSchedulerCoordinate: "polygonSchedulerCoordinateKey",
};

export interface SyncStateResult {
    totalDocs: number;
    collections: Record<string, number>;
    isCanonicalState: boolean;
}

export async function syncStateToDb(db: any, binaryData: Buffer, resourceId?: string): Promise<SyncStateResult> {
    const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/;
    const DATE_FIELDS = new Set([
        "timestamp",
        "createTimestamp",
        "reportedTime",
        "validTimestamp",
        "invalidTimestamp",
        "updatedAt",
        "lastCheckInTimestamp",
        "lastSizeChangeTimestamp",
        "creationServerTimestamp",
        "activeServerTimestamp",
        "createdAt",
        "finishedAt",
        "lastSeen",
        "expirationTime",
        "plannedTime",
        "plannedEndTime",
        "startDate",
        "endDate",
        "lastPaymentTime",
        "loadTimestamp",
        "chekinsTimestamp",
    ]);

    function restoreDates(obj: any): any {
        if (obj === null || obj === undefined) return obj;
        if (typeof obj === "string" && ISO_DATE_RE.test(obj)) return new Date(obj);
        if (obj instanceof Date) return obj;
        if (Array.isArray(obj)) return obj.map(restoreDates);
        if (typeof obj === "object") {
            const out: any = {};
            for (const [k, v] of Object.entries(obj)) {
                if (DATE_FIELDS.has(k) && typeof v === "string" && ISO_DATE_RE.test(v)) {
                    out[k] = new Date(v);
                } else if (v && typeof v === "object" && "$date" in (v as any)) {
                    out[k] = new Date((v as any).$date);
                } else if (typeof v === "object") {
                    out[k] = restoreDates(v);
                } else {
                    out[k] = v;
                }
            }
            return out;
        }
        return obj;
    }

    const syncUpdate = async (coll: string, filter: any, doc: any) => {
        const cleaned = restoreDates(doc);
        const { _id, ...rest } = cleaned;
        const keyField = COLLECTION_KEY_MAP[coll];
        const pkValue = keyField ? (rest[keyField] ?? filter[keyField]) : null;
        const kh = pkValue != null
            ? createHash('sha256').update(String(pkValue)).digest().subarray(0, 8).toString('hex')
            : undefined;
        const setFields = kh != null
            ? { ...rest, updatedAt: new Date(), _remote: true, _kh: kh }
            : { ...rest, updatedAt: new Date(), _remote: true };
        await db.collection(coll).updateOne(filter, { $set: setFields }, { upsert: true });
        await db.collection(coll).updateOne(filter, { $unset: { _remote: "" } });
    };

    const buildSyncFilter = (collName: string, doc: any, keyField: string) => {
        if (collName === "follows" && doc.followerKey) {
            if (doc.userKey) return { followerKey: doc.followerKey, userKey: doc.userKey };
            if (doc.groupKey) return { followerKey: doc.followerKey, groupKey: doc.groupKey };
            if (doc.placeKey) return { followerKey: doc.followerKey, placeKey: doc.placeKey };
        }
        return { [keyField]: doc[keyField] };
    };

    const reconcileIncomingPolygons = async (docs: any[]) => {
        const incoming = docs.filter(doc => doc?.polygonKey && Array.isArray(doc.placeKeys));
        const incomingPolygonKeys = [...new Set(incoming.map(doc => doc.polygonKey).filter(Boolean))];
        const incomingPlaceKeys = [...new Set(incoming.flatMap(doc => doc.placeKeys).filter(Boolean))];

        if (incomingPolygonKeys.length === 0 || incomingPlaceKeys.length === 0) return;

        await db.collection("polygons").deleteMany({
            placeKeys: { $in: incomingPlaceKeys },
            polygonKey: { $nin: incomingPolygonKeys },
        });
    };

    const emptyResult = (): SyncStateResult => ({
        totalDocs: 0,
        collections: {},
        isCanonicalState: false,
    });

    try {
        let json: any;
        try {
            json = JSON.parse(binaryData.toString());
        } catch (e: any) {
            throw new Error(`Invalid cache payload JSON: ${e.message}`);
        }

        if (json && typeof json === 'object') {
            let isCanonicalState = false;
            for (const key of Object.keys(COLLECTION_KEY_MAP)) {
                if (json[key] && Array.isArray(json[key])) {
                    isCanonicalState = true;
                    break;
                }
            }

            if (isCanonicalState) {
                let totalDocs = 0;
                const collections: Record<string, number> = {};
                if (Array.isArray(json.polygons)) {
                    await reconcileIncomingPolygons(json.polygons);
                }
                for (const collName in COLLECTION_KEY_MAP) {
                    if (json[collName] && Array.isArray(json[collName])) {
                        const docs = json[collName];
                        for (const doc of docs) {
                            const keyField = COLLECTION_KEY_MAP[collName];
                            if (doc[keyField]) {
                                await syncUpdate(collName, buildSyncFilter(collName, doc, keyField), doc);
                                collections[collName] = (collections[collName] || 0) + 1;
                                totalDocs++;
                            }
                        }
                    }
                }
                console.log(`  -> Synced complete schemaless bundle: ${totalDocs} documents across ${Object.keys(collections).length} collections.`);
                return { totalDocs, collections, isCanonicalState: true };
            }

            const list = Array.isArray(json) ? json : [json];
            const collections: Record<string, number> = {};
            let totalDocs = 0;
            await reconcileIncomingPolygons(list);
            for (const item of list) {
                for (const collName in COLLECTION_KEY_MAP) {
                    const keyField = COLLECTION_KEY_MAP[collName];
                    if (item[keyField]) {
                        await syncUpdate(collName, buildSyncFilter(collName, item, keyField), item);
                        collections[collName] = (collections[collName] || 0) + 1;
                        totalDocs++;
                        break;
                    }
                }
            }
            return { totalDocs, collections, isCanonicalState: false };
        }
        return emptyResult();
    } catch (e: any) {
        console.log(`  -> Skipped schemaless DB sync Error: ${e.message}`);
        throw e;
    }
}
