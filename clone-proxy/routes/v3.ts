import { Express } from "express";
import { Connection, Keypair, PublicKey, Ed25519Program } from "@solana/web3.js";
import { TOKEN_PROGRAM_ID } from "@solana/spl-token";
import * as anchor from "@coral-xyz/anchor";
import { BN } from "bn.js";
import { serialize } from "@dao-xyz/borsh";
import { getCanonicalUserState } from "../models/borsh-schemas.js";
import { PROGRAM_ID } from "../config.js";
import { hashKeyFull, hashShort, geoHashFromLatLon } from "../network/manifest.js";
import { calculateFee, clearVoucherTimer } from "../network/payment.js";
import nacl from "tweetnacl";
import { createHmac } from "crypto";
import { getAssociatedTokenAddress } from "@solana/spl-token";
import { readFileSync } from "node:fs";

const LOCAL_IDL = JSON.parse(readFileSync(new URL("../programs/idl/orbis_protocol.json", import.meta.url), "utf-8"));

type ProviderFetcher = (db: any, keys: string[]) => Promise<string | undefined>;

const stripMongoId = ({ _id, ...rest }: any) => rest;

const feedFetcher: ProviderFetcher = async (db, keys) => {
    const postsRaw = await db.collection("posts").find({ postKey: { $in: keys } }).toArray();
    const groupKeys = [...new Set(postsRaw.map((p: any) => p.groupKey).filter(Boolean))] as string[];
    const userKeys = [...new Set(postsRaw.map((p: any) => p.userKey).filter(Boolean))] as string[];
    const placeKeys = [...new Set(postsRaw.map((p: any) => p.placeKey).filter(Boolean))] as string[];

    const [groupsRaw, placesRaw, usersRaw] = await Promise.all([
        groupKeys.length > 0 ? db.collection("groups").find({ groupKey: { $in: groupKeys } }).toArray() : [],
        placeKeys.length > 0 ? db.collection("places").find({ placeKey: { $in: placeKeys } }).toArray() : [],
        userKeys.length > 0 ? db.collection("users").find({ userKey: { $in: userKeys } }).toArray() : []
    ]);

    console.log(`   -> [feed] fetched ${postsRaw.length} posts, ${groupsRaw.length} groups, ${usersRaw.length} users, ${placesRaw.length} places`);

    return Buffer.from(JSON.stringify({
        posts: postsRaw.map(({ _id, ...r }: any) => r),
        groups: groupsRaw.map(({ _id, ...r }: any) => r),
        users: usersRaw.map(({ _id, ...r }: any) => r),
        places: placesRaw.map(({ _id, ...r }: any) => r)
    })).toString("base64");
};

const PROVIDER_FETCHERS: Record<string, ProviderFetcher> = {
    groups_rating: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        const postsRaw = await db.collection("posts").find({ groupKey: { $in: keys }, deleted: { $ne: true } }).toArray();
        console.log(`   -> [groups_rating] ${groupsRaw.length} groups, ${postsRaw.length} posts`);
        const groups = groupsRaw.map(({ _id, ...r }: any) => r);
        const posts = postsRaw.map(({ _id, ...r }: any) => r);
        return Buffer.from(JSON.stringify({ groups, posts })).toString("base64");
    },
    groups_geo: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        console.log(`   -> [groups_geo] ${groupsRaw.length} groups`);
        const groups = groupsRaw.map(({ _id, ...r }: any) => r);
        return Buffer.from(JSON.stringify(groups)).toString("base64");
    },
    group_single: async (db, keys) => {
        const groupRaw = await db.collection("groups").findOne({ groupKey: keys[0] });
        if (!groupRaw) {
            console.log(`   -> [group_single] group not found for key=${keys[0]}`);
            return undefined;
        }
        console.log(`   -> [group_single] fetched group ${keys[0]}`);
        const { _id, ...rest } = groupRaw;
        return Buffer.from(JSON.stringify({ groups: [rest] })).toString("base64");
    },
    group_slug: async (db, keys) => {
        const groupRaw = await db.collection("groups").findOne({ slug: keys[0] });
        if (!groupRaw) {
            console.log(`   -> [group_slug] group not found for slug=${keys[0]}`);
            return undefined;
        }
        console.log(`   -> [group_slug] fetched group slug=${keys[0]}`);
        const { _id, ...rest } = groupRaw;
        return Buffer.from(JSON.stringify({ groups: [rest] })).toString("base64");
    },
    group_members: async (db, keys) => {
        const groupRaw = await db.collection("groups").findOne({ groupKey: keys[0] });
        if (!groupRaw) {
            console.log(`   -> [group_members] group not found for key=${keys[0]}`);
            return undefined;
        }
        console.log(`   -> [group_members] fetched group ${keys[0]}`);
        const { _id, ...rest } = groupRaw;
        return Buffer.from(JSON.stringify({ groups: [rest] })).toString("base64");
    },
    group_followers: async (db, keys) => {
        const groupRaw = await db.collection("groups").findOne({ groupKey: keys[0] });
        if (!groupRaw) {
            console.log(`   -> [group_followers] group not found for key=${keys[0]}`);
            return undefined;
        }
        console.log(`   -> [group_followers] fetched group ${keys[0]}`);
        const { _id, ...rest } = groupRaw;
        return Buffer.from(JSON.stringify({ groups: [rest] })).toString("base64");
    },
    group_admins: async (db, keys) => {
        const groupRaw = await db.collection("groups").findOne({ groupKey: keys[0] });
        if (!groupRaw) {
            console.log(`   -> [group_admins] group not found for key=${keys[0]}`);
            return undefined;
        }
        console.log(`   -> [group_admins] fetched group ${keys[0]}`);
        const { _id, ...rest } = groupRaw;
        return Buffer.from(JSON.stringify({ groups: [rest] })).toString("base64");
    },
    group_events: async (db, keys) => {
        const postsRaw = await db.collection("posts").find({ groupKey: keys[0], deleted: { $ne: true } }).toArray();
        console.log(`   -> [group_events] ${postsRaw.length} posts for group ${keys[0]}`);
        const posts = postsRaw.map(({ _id, ...r }: any) => r);
        return Buffer.from(JSON.stringify(posts)).toString("base64");
    },
    auth_login: async (db, keys) => {
        try {
            const result = await getCanonicalUserState(db, keys[0]);
            console.log(`   -> [auth_login] fetched user state for ${keys[0]}`);
            return result.buffer.toString("base64");
        } catch (e: any) {
            console.log(`   -> [auth_login] failed to fetch user state for ${keys[0]}: ${e.message}`);
            return undefined;
        }
    },
    auth_signup: async (db, keys) => {
        try {
            const result = await getCanonicalUserState(db, keys[0]);
            console.log(`   -> [auth_signup] fetched user state for ${keys[0]}`);
            return result.buffer.toString("base64");
        } catch (e: any) {
            console.log(`   -> [auth_signup] failed to fetch user state for ${keys[0]}: ${e.message}`);
            return undefined;
        }
    },
    user_me: async (db, keys) => {
        try {
            const result = await getCanonicalUserState(db, keys[0]);
            console.log(`   -> [user_me] fetched user state for ${keys[0]}`);
            return result.buffer.toString("base64");
        } catch (e: any) {
            console.log(`   -> [user_me] failed to fetch user state for ${keys[0]}: ${e.message}`);
            return undefined;
        }
    },
    user_search: async (db, keys) => {
        const usersRaw = await db.collection("users").find({ userKey: { $in: keys } }).toArray();
        console.log(`   -> [user_search] ${usersRaw.length} users`);
        return Buffer.from(JSON.stringify({ users: usersRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_slug: async (db, keys) => {
        const userRaw = await db.collection("users").findOne({ slug: keys[0] });
        if (!userRaw) {
            console.log(`   -> [user_slug] user not found for slug=${keys[0]}`);
            return undefined;
        }
        console.log(`   -> [user_slug] fetched user slug=${keys[0]}`);
        try {
            const result = await getCanonicalUserState(db, userRaw.userKey);
            return result.buffer.toString("base64");
        } catch (e: any) {
            console.log(`   -> [user_slug] failed to fetch user state for ${userRaw.userKey}: ${e.message}`);
            return undefined;
        }
    },
    user_slug_pictures: async (db, keys) => {
        const userRaw = await db.collection("users").findOne({ slug: keys[0] });
        if (!userRaw) {
            console.log(`   -> [user_slug_pictures] user not found for slug=${keys[0]}`);
            return undefined;
        }
        const pics = await db.collection("userPicture").find({ userKey: userRaw.userKey, type: "ORBIS" }).toArray();
        console.log(`   -> [user_slug_pictures] ${pics.length} pictures for slug=${keys[0]}`);
        return Buffer.from(JSON.stringify({ users: [{ ...userRaw, _id: undefined }], userPictures: pics.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_pictures: async (db, keys) => {
        const pics = await db.collection("userPicture").find({ userKey: { $in: keys }, type: "ORBIS" }).toArray();
        console.log(`   -> [user_pictures] ${pics.length} pictures`);
        return Buffer.from(JSON.stringify({ userPictures: pics.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_slug_igpictures: async (db, keys) => {
        const userRaw = await db.collection("users").findOne({ slug: keys[0] });
        if (!userRaw) {
            console.log(`   -> [user_slug_igpictures] user not found for slug=${keys[0]}`);
            return undefined;
        }
        const pics = await db.collection("userPicture").find({ userKey: userRaw.userKey, type: "INSTAGRAM" }).toArray();
        console.log(`   -> [user_slug_igpictures] ${pics.length} ig pictures for slug=${keys[0]}`);
        return Buffer.from(JSON.stringify({ users: [{ ...userRaw, _id: undefined }], userPictures: pics.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_igpictures: async (db, keys) => {
        const pics = await db.collection("userPicture").find({ userKey: { $in: keys }, type: "INSTAGRAM" }).toArray();
        console.log(`   -> [user_igpictures] ${pics.length} ig pictures`);
        return Buffer.from(JSON.stringify({ userPictures: pics.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_slug_following: async (db, keys) => {
        const userRaw = await db.collection("users").findOne({ slug: keys[0] });
        if (!userRaw) {
            console.log(`   -> [user_slug_following] user not found for slug=${keys[0]}`);
            return undefined;
        }
        const followsRaw = await db.collection("follows").find({ followerKey: userRaw.userKey }).toArray();
        const followedUserKeys = [...new Set(followsRaw.map((f: any) => f.userKey).filter(Boolean))] as string[];
        const followedGroupKeys = [...new Set(followsRaw.map((f: any) => f.groupKey).filter(Boolean))] as string[];
        const followedPlaceKeys = [...new Set(followsRaw.map((f: any) => f.placeKey).filter(Boolean))] as string[];
        const [usersRaw, groupsRaw, placesRaw] = await Promise.all([
            followedUserKeys.length > 0 ? db.collection("users").find({ userKey: { $in: followedUserKeys } }).toArray() : [],
            followedGroupKeys.length > 0 ? db.collection("groups").find({ groupKey: { $in: followedGroupKeys } }).toArray() : [],
            followedPlaceKeys.length > 0 ? db.collection("places").find({ placeKey: { $in: followedPlaceKeys } }).toArray() : []
        ]);
        console.log(`   -> [user_slug_following] ${followsRaw.length} follows, ${usersRaw.length} users, ${groupsRaw.length} groups, ${placesRaw.length} places`);
        return Buffer.from(JSON.stringify({
            follows: followsRaw.map(stripMongoId),
            users: usersRaw.map(stripMongoId),
            groups: groupsRaw.map(stripMongoId),
            places: placesRaw.map(stripMongoId)
        })).toString("base64");
    },
    user_following: async (db, keys) => {
        const followsRaw = await db.collection("follows").find({ followerKey: { $in: keys } }).toArray();
        const followedUserKeys = [...new Set(followsRaw.map((f: any) => f.userKey).filter(Boolean))] as string[];
        const followedGroupKeys = [...new Set(followsRaw.map((f: any) => f.groupKey).filter(Boolean))] as string[];
        const followedPlaceKeys = [...new Set(followsRaw.map((f: any) => f.placeKey).filter(Boolean))] as string[];
        const [usersRaw, groupsRaw, placesRaw] = await Promise.all([
            followedUserKeys.length > 0 ? db.collection("users").find({ userKey: { $in: followedUserKeys } }).toArray() : [],
            followedGroupKeys.length > 0 ? db.collection("groups").find({ groupKey: { $in: followedGroupKeys } }).toArray() : [],
            followedPlaceKeys.length > 0 ? db.collection("places").find({ placeKey: { $in: followedPlaceKeys } }).toArray() : []
        ]);
        console.log(`   -> [user_following] ${followsRaw.length} follows, ${usersRaw.length} users, ${groupsRaw.length} groups, ${placesRaw.length} places`);
        return Buffer.from(JSON.stringify({
            follows: followsRaw.map(stripMongoId),
            users: usersRaw.map(stripMongoId),
            groups: groupsRaw.map(stripMongoId),
            places: placesRaw.map(stripMongoId)
        })).toString("base64");
    },
    user_slug_followers: async (db, keys) => {
        const userRaw = await db.collection("users").findOne({ slug: keys[0] });
        if (!userRaw) {
            console.log(`   -> [user_slug_followers] user not found for slug=${keys[0]}`);
            return undefined;
        }
        const followsRaw = await db.collection("follows").find({ userKey: userRaw.userKey, accepted: true }).toArray();
        const followerKeys = [...new Set(followsRaw.map((f: any) => f.followerKey).filter(Boolean))] as string[];
        const usersRaw = followerKeys.length > 0 ? await db.collection("users").find({ userKey: { $in: followerKeys } }).toArray() : [];
        console.log(`   -> [user_slug_followers] ${usersRaw.length} followers for slug=${keys[0]}`);
        return Buffer.from(JSON.stringify({ users: usersRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_followers_pending: async (db, keys) => {
        const followsRaw = await db.collection("follows").find({ userKey: { $in: keys }, accepted: false }).toArray();
        const followerKeys = [...new Set(followsRaw.map((f: any) => f.followerKey).filter(Boolean))] as string[];
        const usersRaw = followerKeys.length > 0 ? await db.collection("users").find({ userKey: { $in: followerKeys } }).toArray() : [];
        console.log(`   -> [user_followers_pending] ${usersRaw.length} pending followers`);
        return Buffer.from(JSON.stringify({ users: usersRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_followers: async (db, keys) => {
        const followsRaw = await db.collection("follows").find({ userKey: { $in: keys }, accepted: true }).toArray();
        const followerKeys = [...new Set(followsRaw.map((f: any) => f.followerKey).filter(Boolean))] as string[];
        const usersRaw = followerKeys.length > 0 ? await db.collection("users").find({ userKey: { $in: followerKeys } }).toArray() : [];
        console.log(`   -> [user_followers] ${usersRaw.length} followers`);
        return Buffer.from(JSON.stringify({ users: usersRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_blocked: async (db, keys) => {
        const usersRaw = await db.collection("users").find({ userKey: { $in: keys } }).toArray();
        console.log(`   -> [user_blocked] ${usersRaw.length} blocked users`);
        return Buffer.from(JSON.stringify({ users: usersRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_blocked_by: async (db, keys) => {
        const usersRaw = await db.collection("users").find({ userKey: { $in: keys } }).toArray();
        console.log(`   -> [user_blocked_by] ${usersRaw.length} blocked-by users`);
        return Buffer.from(JSON.stringify({ users: usersRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_slug_admin: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        console.log(`   -> [user_slug_admin] ${groupsRaw.length} admin groups`);
        return Buffer.from(JSON.stringify({ groups: groupsRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_admin: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        console.log(`   -> [user_admin] ${groupsRaw.length} admin groups`);
        return Buffer.from(JSON.stringify({ groups: groupsRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_slug_member: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        console.log(`   -> [user_slug_member] ${groupsRaw.length} member groups`);
        return Buffer.from(JSON.stringify({ groups: groupsRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_member: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        console.log(`   -> [user_member] ${groupsRaw.length} member groups`);
        return Buffer.from(JSON.stringify({ groups: groupsRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_slug_group_follower: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        console.log(`   -> [user_slug_group_follower] ${groupsRaw.length} followed groups`);
        return Buffer.from(JSON.stringify({ groups: groupsRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_group_follower: async (db, keys) => {
        const groupsRaw = await db.collection("groups").find({ groupKey: { $in: keys } }).toArray();
        console.log(`   -> [user_group_follower] ${groupsRaw.length} followed groups`);
        return Buffer.from(JSON.stringify({ groups: groupsRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_chat_users: async (db, keys) => {
        const usersRaw = await db.collection("users").find({ userKey: { $in: keys } }).toArray();
        console.log(`   -> [user_chat_users] ${usersRaw.length} chat users`);
        return Buffer.from(JSON.stringify({ users: usersRaw.map(({ _id, ...r }: any) => r) })).toString("base64");
    },
    user_profile: async (db, keys) => {
        try {
            const result = await getCanonicalUserState(db, keys[0]);
            console.log(`   -> [user_profile] fetched user state for ${keys[0]}`);
            return result.buffer.toString("base64");
        } catch (e: any) {
            console.log(`   -> [user_profile] failed to fetch user state for ${keys[0]}: ${e.message}`);
            return undefined;
        }
    },
    posts_comments: async (db, keys) => {
        const commentsRaw = await db.collection("comments").find({ postKey: keys[0], deleted: { $ne: true } }).toArray();
        console.log(`   -> [posts_comments] ${commentsRaw.length} comments for post ${keys[0]}`);
        const comments = commentsRaw.map(({ _id, ...r }: any) => r);
        return Buffer.from(JSON.stringify({ comments })).toString("base64");
    },
    post_single: async (db, keys) => {
        const postRaw = await db.collection("posts").findOne({ postKey: keys[0] });
        if (!postRaw) return undefined;
        console.log(`   -> [post_single] fetched post ${keys[0]}`);
        const { _id, ...rest } = postRaw;
        return Buffer.from(JSON.stringify({ posts: [rest] })).toString("base64");
    },
    feed_all: feedFetcher,
    feed_city: feedFetcher,
    feed_near: feedFetcher,
    feed_city_stories: feedFetcher,
    feed_near_stories: feedFetcher,
    feed_news: feedFetcher,
    feed_news_stories: feedFetcher,
    feed_user_slug: feedFetcher,
    feed_user: feedFetcher,
    feed_place_slug: feedFetcher,
    feed_place: feedFetcher,
    feed_group_slug: feedFetcher,
    feed_group: feedFetcher,
    polygons: async (db, keys) => {
        const keysList = keys || [];
        const objectIds = [];
        for (const k of keysList) {
            try { 
                const { ObjectId } = await import("mongodb"); 
                objectIds.push(new ObjectId(k)); 
            } catch { }
        }
        
        const polygonsRaw = await db.collection("polygons").find({ 
            $or: [ 
                { polygonKey: { $in: keysList } }, 
                { placeKeys: { $in: keysList } }, 
                { placeKeys: { $in: objectIds } }, 
                { _id: { $in: objectIds } } 
            ] 
        }).toArray();
        
        const placeKeys: string[] = [];
        polygonsRaw.forEach((p: any) => p.placeKeys && placeKeys.push(...p.placeKeys));
        const allPlaceKeys = [...new Set([...keysList, ...placeKeys])];
        
        const placesRaw = allPlaceKeys.length > 0 ? await db.collection("places").find({ placeKey: { $in: allPlaceKeys } }).toArray() : [];
        const groupKeys = [...new Set(placesRaw.map((p: any) => p.dominantGroupKey).filter(Boolean))];
        const groupsRaw = groupKeys.length > 0 ? await db.collection("groups").find({ groupKey: { $in: groupKeys } }).toArray() : [];
        console.log(`   -> [polygons] fetched ${polygonsRaw.length} polygons, ${placesRaw.length} places, ${groupsRaw.length} groups`);
        
        return Buffer.from(JSON.stringify({
            polygons: polygonsRaw.map(({ _id, ...r }: any) => ({ _id: { $oid: _id.toString() }, ...r })),
            places: placesRaw.map(({ _id, ...r }: any) => ({ _id: { $oid: _id.toString() }, ...r })),
            groups: groupsRaw.map(({ _id, ...r }: any) => ({ _id: { $oid: _id.toString() }, ...r }))
        })).toString("base64");
    },
    polygon_single: async (db, keys) => {
        const placeKey = keys[0];
        let placeKeyOid;
        try { 
            const { ObjectId } = await import("mongodb"); 
            placeKeyOid = new ObjectId(placeKey);
        } catch {}
        
        const polygonsRaw = await db.collection("polygons").find({ 
            $or: [ 
                { placeKeys: placeKey }, 
                { placeKeys: placeKeyOid }, 
                { _id: placeKeyOid }, 
                { polygonKey: placeKey } 
            ].filter(v => Object.values(v)[0] !== undefined)
        }).toArray();
        const placesRaw = await db.collection("places").find({ placeKey }).toArray();
        const groupKeys = [...new Set(placesRaw.map((p: any) => p.dominantGroupKey).filter(Boolean))];
        const groupsRaw = groupKeys.length > 0 ? await db.collection("groups").find({ groupKey: { $in: groupKeys } }).toArray() : [];
        console.log(`   -> [polygon_single] fetched ${polygonsRaw.length} polygons, ${placesRaw.length} places, ${groupsRaw.length} groups`);
        
        return Buffer.from(JSON.stringify({
            polygons: polygonsRaw.map(({ _id, ...r }: any) => ({ _id: { $oid: _id.toString() }, ...r })),
            places: placesRaw.map(({ _id, ...r }: any) => ({ _id: { $oid: _id.toString() }, ...r })),
            groups: groupsRaw.map(({ _id, ...r }: any) => ({ _id: { $oid: _id.toString() }, ...r }))
        })).toString("base64");
    },
    polygon_status: async (db, keys) => {
        const statuses = await db.collection("polygonSchedulerCoordinate").find({ polygonSchedulerCoordinateKey: keys[0] }).toArray();
        console.log(`   -> [polygon_status] fetched ${statuses.length} status for key=${keys[0]}`);
        return Buffer.from(JSON.stringify({
            polygonSchedulerCoordinate: statuses.map(({ _id, ...r }: any) => ({ _id: { $oid: _id.toString() }, ...r }))
        })).toString("base64");
    },
};


const SYSVAR_INSTRUCTIONS_PUBKEY = new PublicKey("Sysvar1nstructions1111111111111111111111111");

export function registerV3Routes(app: Express, db: any, payer: Keypair, connection: Connection) {
    app.get("/v3/users/:userKey/state/:timestamp", async (req, res) => {
        try {
            const result = await getCanonicalUserState(db, req.params.userKey);
            const fee = await calculateFee(connection, result.buffer.length);
            return res.json({
                data: Buffer.from(result.buffer).toString("base64"),
                requiredFee: fee.toString()
            });
        } catch (e: any) { res.status(404).json({ error: e.message }); }
    });
    /*
    app.get("/v3/groups", async (req, res) => {
        try {
            const groupsRaw = await db.collection("groups").find({}).limit(50).toArray();
            const groups = groupsRaw.map((g: any) => new GroupDoc(g));
            const buffer = Buffer.from(serialize(new GroupsBorsh(groups)));
            const fee = await calculateFee(connection, buffer.length);
            return res.json({
                data: buffer.toString("base64"),
                requiredFee: fee.toString()
            });
        } catch (e: any) { res.status(500).json({ error: e.message }); }
    });

    app.get("/v3/groups/:groupKey", async (req, res) => {
        try {
            const groupRaw = await db.collection("groups").findOne({ groupKey: req.params.groupKey });
            if (!groupRaw) return res.status(404).json({ error: "Group not found" });
            const buffer = Buffer.from(serialize(new SingleGroupBorsh(new GroupDoc(groupRaw))));
            const fee = await calculateFee(connection, buffer.length);
            return res.json({
                data: buffer.toString("base64"),
                requiredFee: fee.toString()
            });
        } catch (e: any) { res.status(500).json({ error: e.message }); }
    });
    */
    app.post("/v3/vouchers", async (req, res) => {
        const { signature, size, hash, escrow, extraDataRequest } = req.body;
        console.log(`\n[Voucher] Received claim request:`);
        console.log('   The extra data request is: ', extraDataRequest);
        console.log(`   Escrow:    ${escrow}`);
        console.log(`   Data Size: ${size} bytes`);
        console.log(`   Data Hash: ${hash}`);

        try {
            const escrowPubkey = new PublicKey(escrow);
            const wallet = new anchor.Wallet(payer);
            const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
            const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);

            const escrowData: any = await program.account.streamingEscrow.fetch(escrowPubkey);
            const requesterPubkey = escrowData.requester as PublicKey;
            const providerPubkey = payer.publicKey;

            console.log(`   Requester:     ${requesterPubkey.toBase58()}`);
            console.log(`   Provider (us): ${providerPubkey.toBase58()}`);

            if (escrowData.provider.toBase58() !== providerPubkey.toBase58()) {
                const errMsg = `Your escrow provider ${escrowData.provider.toBase58()} does not match me (${providerPubkey.toBase58()}). Please recreate the escrow with my address.`;
                console.log(`   -> Provider mismatch detected! ${errMsg}`);
                return res.status(403).json({ error: "Escrow provider mismatch", details: errMsg });
            }

            const [providerCloneInfoPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("clone_info"), providerPubkey.toBuffer()], PROGRAM_ID
            );
            const providerCloneInfo: any = await program.account.cloneInfo.fetch(providerCloneInfoPda);
            console.log(`   Provider Trust Score: ${providerCloneInfo.trustScore}`);

            if (providerCloneInfo.trustScore < 500) {
                console.log(`   -> Trust score too low: ${providerCloneInfo.trustScore} (min 500)`);
                return res.status(403).json({ error: "Provider trust score too low" });
            }

            const [requesterCloneInfoPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("clone_info"), requesterPubkey.toBuffer()], PROGRAM_ID
            );
            await program.account.cloneInfo.fetch(requesterCloneInfoPda);
            console.log(`   Both clones registered on-chain`);

            const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], PROGRAM_ID);
            const globalConfig: any = await program.account.globalConfig.fetch(configPda);
            const feePerMb = new BN(globalConfig.feePerMb.toString());
            const minFee = new BN(globalConfig.minFee.toString());
            const mbCount = Math.floor(size / 1_048_576);
            const byteFee = feePerMb.muln(mbCount);
            const amountReleased = size > 0 ? (byteFee.gt(minFee) ? byteFee : minFee) : new BN(0);

            const amountLocked = new BN(escrowData.amountLocked.toString());
            const amountClaimed = new BN(escrowData.amountClaimed.toString());
            const currentBalance = amountLocked.sub(amountClaimed);

            console.log(`   Fee per MB:      ${feePerMb.toString()}`);
            console.log(`   Amount to claim: ${amountReleased.toString()}`);
            console.log(`   Escrow balance:  ${currentBalance.toString()}`);

            if (currentBalance.lt(amountReleased)) {
                console.log(`   -> Insufficient escrow balance!`);
                return res.status(402).json({ error: "Insufficient escrow balance" });
            }

            const sigBuffer = Buffer.from(signature, "base64");
            const dataSize = BigInt(size);
            const sizeBuf = Buffer.alloc(8);
            sizeBuf.writeBigUInt64LE(dataSize);
            const nonceBuf = Buffer.alloc(8);
            nonceBuf.writeBigUInt64LE(BigInt(escrowData.amountClaimed.toString()));
            const hashBuf = Buffer.from(hash, "hex");
            const message = Buffer.concat([escrowPubkey.toBuffer(), nonceBuf, sizeBuf, hashBuf]);

            console.log(`   Signature length: ${sigBuffer.length} bytes`);
            console.log(`   Amount claimed:   ${escrowData.amountClaimed.toString()}`);
            console.log(`   Message length:   ${message.length} bytes`);

            const ed25519Ix = Ed25519Program.createInstructionWithPublicKey({
                publicKey: requesterPubkey.toBytes(),
                message: Uint8Array.from(message),
                signature: Uint8Array.from(sigBuffer),
            });

            const [escrowVaultPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("escrow_vault"), escrowPubkey.toBuffer()], PROGRAM_ID
            );
            const orbisMint = globalConfig.orbisMint;
            const providerTokenAccount = await anchor.utils.token.associatedAddress({
                mint: orbisMint, owner: providerPubkey,
            });
            const treasuryTokenAccount = await anchor.utils.token.associatedAddress({
                mint: orbisMint, owner: globalConfig.treasury,
            });

            console.log(`   -> Submitting claim_streaming_payment transaction...`);

            const dataHashArray = Array.from(hashBuf);

            const tx = await program.methods
                .claimStreamingPayment(
                    new BN(size),
                    dataHashArray,
                )
                .accounts({
                    requester: requesterPubkey,
                    streamingEscrow: escrowPubkey,
                    escrowVault: escrowVaultPda,
                    requesterCloneInfo: requesterCloneInfoPda,
                    providerCloneInfo: providerCloneInfoPda,
                    provider: providerPubkey,
                    providerTokenAccount,
                    treasuryTokenAccount,
                    config: configPda,
                    tokenProgram: TOKEN_PROGRAM_ID,
                    instructionsSysvar: SYSVAR_INSTRUCTIONS_PUBKEY,
                })
                .preInstructions([ed25519Ix])
                .rpc();

            const treasuryFee = amountReleased.divn(100);
            const providerPayout = amountReleased.sub(treasuryFee);

            console.log(`   -> Payment claimed on-chain!`);
            clearVoucherTimer(escrow);
            console.log(`   TX Signature:    ${tx}`);
            console.log(`   Total Claimed: ${amountReleased.toString()}`);
            console.log(`   Provider:     ${providerPayout.toString()}`);
            console.log(`   Treasury:     ${treasuryFee.toString()}`);

            let extraDataStr: string | undefined;
            if (extraDataRequest && extraDataRequest.queryType && Array.isArray(extraDataRequest.keys)) {
                try {
                    console.log(`   -> Extra data request type: ${extraDataRequest.queryType}`);
                    const fetcher = PROVIDER_FETCHERS[extraDataRequest.queryType];
                    if (fetcher) {
                        extraDataStr = await fetcher(db, extraDataRequest.keys);
                    } else {
                        console.log(`   -> No fetcher registered for queryType: ${extraDataRequest.queryType}`);
                    }
                } catch (e: any) {
                    console.error(`   -> Failed to gather extra data: ${e.message}`);
                }
            }

            res.json({
                status: "claimed",
                tx,
                amountClaimed: amountReleased.toString(),
                providerPayout: providerPayout.toString(),
                treasuryFee: treasuryFee.toString(),
                extraData: extraDataStr
            });
            console.log(`   -> Claim successful. Full data sent to requester.`);
        } catch (e: any) {
            console.error(`   -> Claim failed: ${e.message}`);
            res.status(500).json({ error: "Claim failed", details: e.message });
        }
    });
    app.get("/network/status", async (_req, res) => {
        const providers = await db.collection("network_providers").find({}).toArray();
        const syncState = await db.collection("network_sync_state").findOne({ _id: "last_scan" as any });
        res.json({
            providers: providers.map((p: any) => ({
                provider: p.provider,
                baseUrl: p.baseUrl,
                isGenesis: p.isGenesis,
                latestBatchId: p.latestBatchId,
                collections: Object.keys(p.collectionRoots || {}).length,
            })),
            syncState,
        });
    });

    app.post("/v3/api-key", async (req, res) => {
        try {
            const { publicKey, signature } = req.body;
            if (!publicKey || !signature) return res.status(400).json({ error: "publicKey and signature required" });

            const pubkey = new PublicKey(publicKey);
            const signatureBuffer = Buffer.from(signature, "base64");
            const message = Buffer.from("ORBIS_API_KEY_REQ");

            if (!nacl.sign.detached.verify(message, signatureBuffer, pubkey.toBytes())) {
                return res.status(401).json({ error: "Invalid signature" });
            }

            const wallet = new anchor.Wallet(payer);
            const anchorProvider = new anchor.AnchorProvider(connection, wallet, { preflightCommitment: "confirmed" });
            const program = new anchor.Program(LOCAL_IDL as any, anchorProvider);

            const [cloneInfoPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("clone_info"), pubkey.toBuffer()],
                PROGRAM_ID
            );

            try {
                await program.account.cloneInfo.fetch(cloneInfoPda);
            } catch {
                return res.status(401).json({ error: "You are not a registered clone node." });
            }

            const [configPda] = PublicKey.findProgramAddressSync([Buffer.from("global_config")], PROGRAM_ID);
            const configData: any = await program.account.globalConfig.fetch(configPda);

            const ata = await getAssociatedTokenAddress(configData.orbisMint, pubkey);
            const balanceInfo = await connection.getTokenAccountBalance(ata);
            
            const orbisAmount = new BN(balanceInfo.value.amount);
            const tenOrbis = new BN(10).mul(new BN(10).pow(new BN(balanceInfo.value.decimals)));

            if (orbisAmount.lt(tenOrbis)) {
                return res.status(401).json({ error: "Insufficient ORBIS balance. Requires at least 10 tokens." });
            }

            const API_SECRET = process.env.API_SECRET;
            if (!API_SECRET) return res.status(500).json({ error: "Server missing API_SECRET configuration." });
            const payload = Buffer.from(JSON.stringify({ 
                role: "client", 
                pubkey: pubkey.toBase58(), 
                timestamp: Date.now() 
            })).toString("base64url");
            
            const sig = createHmac("sha256", API_SECRET).update(payload).digest("base64url");
            const token = `${payload}.${sig}`;

            res.json({ token });
        } catch (e: any) {
            res.status(500).json({ error: e.message });
        }
    });

    app.get("/v3/index-batches/:batchId", async (req, res) => {
        const provider = (req.query.provider as string) || payer.publicKey.toBase58();
        const batchId = Number(req.params.batchId);
        if (!Number.isInteger(batchId)) return res.status(400).json({ error: "Invalid batch id" });
        const doc = await db.collection("network_index_batches").findOne({ provider, batchId });
        if (!doc?.manifestJson) return res.status(404).json({ error: "manifest not found" });
        res.setHeader("Content-Type", "application/json");
        return res.send(doc.manifestJson);
    });

    app.get("/debug/network-lookup", async (req, res) => {
        if ((req.headers["x-master-key"] as string) !== process.env.ORBIS_API_SECRET) {
            return res.status(401).json({ error: "master key required" });
        }
        const collection = req.query.collection as string;
        const type = (req.query.type as string) || "key";
        const value = req.query.value as string;
        const lat = req.query.lat != null ? parseFloat(req.query.lat as string) : undefined;
        const lon = req.query.lon != null ? parseFloat(req.query.lon as string) : undefined;
        if (!collection) return res.status(400).json({ error: "collection required" });

        let field: string;
        let hashValue: string;
        switch (type) {
            case "key": field = "keyHash"; hashValue = hashKeyFull(value); break;
            case "secondary": field = "secondaryHash"; hashValue = hashShort(value); break;
            case "name": field = "nameHash"; hashValue = hashShort(value); break;
            case "parent": field = "parentHash"; hashValue = hashShort(value); break;
            case "parent2": field = "parentHash2"; hashValue = hashShort(value); break;
            case "author":
            case "email": field = "authorHash"; hashValue = hashShort(value); break;
            case "geo": field = "geoHash"; hashValue = (lat != null && lon != null) ? geoHashFromLatLon(lat, lon) : ""; break;
            default: return res.status(400).json({ error: `unknown type ${type}` });
        }

        const ev = await db.collection("network_events")
            .find({ collectionName: collection, status: "pending", [field]: hashValue })
            .sort({ timestamp: -1 })
            .limit(1)
            .next();
        if (!ev) return res.json({ found: false, field, hashValue });
        return res.json({ found: true, network_event_id: ev._id.toString(), field, hashValue, event: ev });
    });

}
