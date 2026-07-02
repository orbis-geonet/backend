export interface ExtraDataRequest {
    type: string;
    keys: string[];
    queryType: string;
}

export interface ExtraDataStrategy {
    queryType: string;
    pathPattern: RegExp;
    buildRequest: (docs: any[], pathWithQuery: string) => ExtraDataRequest | null;
}

function extractPathKey(pathWithQuery: string, pattern: RegExp): string | null {
    const path = pathWithQuery.split("?")[0];
    const match = path.match(pattern);
    return match ? match[1] : null;
}

function extractFeedPostKeys(docs: any[]): string[] {
    const feed = docs[0];
    if (feed && feed.content && Array.isArray(feed.content)) {
        return [...new Set(feed.content.map((c: any) => c.post?.postKey).filter(Boolean))] as string[];
    }
    return [...new Set(docs.map((d: any) => d.postKey).filter(Boolean))] as string[];
}

export const EXTRA_DATA_STRATEGIES: ExtraDataStrategy[] = [
    {
        queryType: "groups_rating",
        pathPattern: /^\/groups\/rating/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "groups_rating" } : null;
        }
    },
    {
        queryType: "group_events",
        pathPattern: /^\/groups\/([^/]+)\/events/,
        buildRequest: (docs, pathWithQuery) => {
            const groupKey = extractPathKey(pathWithQuery, /^\/groups\/([^/]+)\/events/);
            if (!groupKey) return null;
            return { type: "groups", keys: [groupKey], queryType: "group_events" };
        }
    },
    {
        queryType: "group_members",
        pathPattern: /^\/groups\/([^/]+)\/members/,
        buildRequest: (_docs, pathWithQuery) => {
            const groupKey = extractPathKey(pathWithQuery, /^\/groups\/([^/]+)\/members/);
            if (!groupKey) return null;
            return { type: "groups", keys: [groupKey], queryType: "group_members" };
        }
    },
    {
        queryType: "group_followers",
        pathPattern: /^\/groups\/([^/]+)\/followers/,
        buildRequest: (_docs, pathWithQuery) => {
            const groupKey = extractPathKey(pathWithQuery, /^\/groups\/([^/]+)\/followers/);
            if (!groupKey) return null;
            return { type: "groups", keys: [groupKey], queryType: "group_followers" };
        }
    },
    {
        queryType: "group_admins",
        pathPattern: /^\/groups\/([^/]+)\/admins/,
        buildRequest: (_docs, pathWithQuery) => {
            const groupKey = extractPathKey(pathWithQuery, /^\/groups\/([^/]+)\/admins/);
            if (!groupKey) return null;
            return { type: "groups", keys: [groupKey], queryType: "group_admins" };
        }
    },
    {
        queryType: "group_single",
        pathPattern: /^\/groups\/([^/]+)$/,
        buildRequest: (_docs, pathWithQuery) => {
            const groupKey = extractPathKey(pathWithQuery, /^\/groups\/([^/]+)$/);
            if (!groupKey || groupKey === "rating" || groupKey === "recommended" || groupKey === "slug" || groupKey === "subscription") return null;
            return { type: "groups", keys: [groupKey], queryType: "group_single" };
        }
    },
    {
        queryType: "group_slug",
        pathPattern: /^\/groups\/slug\/([^/]+)$/,
        buildRequest: (_docs, pathWithQuery) => {
            const slug = extractPathKey(pathWithQuery, /^\/groups\/slug\/([^/]+)$/);
            if (!slug) return null;
            return { type: "groups", keys: [slug], queryType: "group_slug" };
        }
    },
    {
        queryType: "groups_geo",
        pathPattern: /^\/groups$/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "groups_geo" } : null;
        }
    },
    {
        queryType: "auth_login",
        pathPattern: /^\/login$/,
        buildRequest: (docs) => {
            const userKey = docs[0]?.userKey;
            if (!userKey) return null;
            return { type: "users", keys: [userKey], queryType: "auth_login" };
        }
    },
    {
        queryType: "auth_signup",
        pathPattern: /^\/signup$/,
        buildRequest: (docs) => {
            const userKey = docs[0]?.userKey;
            if (!userKey) return null;
            return { type: "users", keys: [userKey], queryType: "auth_signup" };
        }
    },
    {
        queryType: "user_me",
        pathPattern: /^\/profile\/me$/,
        buildRequest: (docs) => {
            const userKey = docs[0]?.userKey;
            if (!userKey) return null;
            return { type: "users", keys: [userKey], queryType: "user_me" };
        }
    },
    {
        queryType: "user_search",
        pathPattern: /^\/profile$/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.userKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "users", keys, queryType: "user_search" } : null;
        }
    },
    {
        queryType: "user_slug",
        pathPattern: /^\/profile\/slug\/([^/]+)$/,
        buildRequest: (_docs, pathWithQuery) => {
            const slug = extractPathKey(pathWithQuery, /^\/profile\/slug\/([^/]+)$/);
            if (!slug) return null;
            return { type: "users", keys: [slug], queryType: "user_slug" };
        }
    },
    {
        queryType: "user_slug_pictures",
        pathPattern: /^\/profile\/slug\/([^/]+)\/pictures/,
        buildRequest: (_docs, pathWithQuery) => {
            const slug = extractPathKey(pathWithQuery, /^\/profile\/slug\/([^/]+)\/pictures/);
            if (!slug) return null;
            return { type: "users", keys: [slug], queryType: "user_slug_pictures" };
        }
    },
    {
        queryType: "user_pictures",
        pathPattern: /^\/profile\/(?:me|([^/]+))\/pictures/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.userKey || d.pictureKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "users", keys, queryType: "user_pictures" } : null;
        }
    },
    {
        queryType: "user_slug_igpictures",
        pathPattern: /^\/profile\/slug\/([^/]+)\/igpictures/,
        buildRequest: (_docs, pathWithQuery) => {
            const slug = extractPathKey(pathWithQuery, /^\/profile\/slug\/([^/]+)\/igpictures/);
            if (!slug) return null;
            return { type: "users", keys: [slug], queryType: "user_slug_igpictures" };
        }
    },
    {
        queryType: "user_igpictures",
        pathPattern: /^\/profile\/(?:me|([^/]+))\/igpictures/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.userKey || d.pictureKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "users", keys, queryType: "user_igpictures" } : null;
        }
    },
    {
        queryType: "user_slug_following",
        pathPattern: /^\/profile\/slug\/([^/]+)\/following/,
        buildRequest: (_docs, pathWithQuery) => {
            const slug = extractPathKey(pathWithQuery, /^\/profile\/slug\/([^/]+)\/following/);
            if (!slug) return null;
            return { type: "follows", keys: [slug], queryType: "user_slug_following" };
        }
    },
    {
        queryType: "user_following",
        pathPattern: /^\/profile\/(?:me|([^/]+))\/following/,
        buildRequest: (docs, pathWithQuery) => {
            const userKey = extractPathKey(pathWithQuery, /^\/profile\/([^/]+)\/following/);
            if (userKey && userKey !== "me") {
                return { type: "follows", keys: [userKey], queryType: "user_following" };
            }
            const keys = [...new Set(docs.map((d: any) => d.followerKey || d.userKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "follows", keys, queryType: "user_following" } : null;
        }
    },
    {
        queryType: "user_slug_followers",
        pathPattern: /^\/profile\/slug\/([^/]+)\/followers/,
        buildRequest: (_docs, pathWithQuery) => {
            const slug = extractPathKey(pathWithQuery, /^\/profile\/slug\/([^/]+)\/followers/);
            if (!slug) return null;
            return { type: "follows", keys: [slug], queryType: "user_slug_followers" };
        }
    },
    {
        queryType: "user_followers_pending",
        pathPattern: /^\/profile\/me\/followers\/pending/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.userKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "follows", keys, queryType: "user_followers_pending" } : null;
        }
    },
    {
        queryType: "user_followers",
        pathPattern: /^\/profile\/(?:me|([^/]+))\/followers/,
        buildRequest: (_docs, pathWithQuery) => {
            const userKey = extractPathKey(pathWithQuery, /^\/profile\/([^/]+)\/followers/);
            if (!userKey || userKey === "me") return null;
            return { type: "follows", keys: [userKey], queryType: "user_followers" };
        }
    },
    {
        queryType: "user_blocked",
        pathPattern: /^\/profile\/blockedUsers/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.userKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "users", keys, queryType: "user_blocked" } : null;
        }
    },
    {
        queryType: "user_blocked_by",
        pathPattern: /^\/profile\/blockedByUsers/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.userKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "users", keys, queryType: "user_blocked_by" } : null;
        }
    },
    {
        queryType: "user_slug_admin",
        pathPattern: /^\/profile\/slug\/([^/]+)\/admin/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "user_slug_admin" } : null;
        }
    },
    {
        queryType: "user_admin",
        pathPattern: /^\/profile\/(?:me|([^/]+))\/admin/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "user_admin" } : null;
        }
    },
    {
        queryType: "user_slug_member",
        pathPattern: /^\/profile\/slug\/([^/]+)\/member/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "user_slug_member" } : null;
        }
    },
    {
        queryType: "user_member",
        pathPattern: /^\/profile\/(?:me|([^/]+))\/member/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "user_member" } : null;
        }
    },
    {
        queryType: "user_slug_group_follower",
        pathPattern: /^\/profile\/slug\/([^/]+)\/groupFollower/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "user_slug_group_follower" } : null;
        }
    },
    {
        queryType: "user_group_follower",
        pathPattern: /^\/profile\/(?:me|([^/]+))\/groupFollower/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.groupKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "groups", keys, queryType: "user_group_follower" } : null;
        }
    },
    {
        queryType: "user_chat_users",
        pathPattern: /^\/profile\/chatusers/,
        buildRequest: (docs) => {
            const keys = [...new Set(docs.map((d: any) => d.userKey).filter(Boolean))] as string[];
            return keys.length > 0 ? { type: "users", keys, queryType: "user_chat_users" } : null;
        }
    },
    {
        queryType: "user_profile",
        pathPattern: /^\/profile\/([^/]+)$/,
        buildRequest: (_docs, pathWithQuery) => {
            const userKey = extractPathKey(pathWithQuery, /^\/profile\/([^/]+)$/);
            if (!userKey || userKey === "me" || userKey === "blockedUsers" || userKey === "blockedByUsers" || userKey === "chatusers" || userKey === "slug") return null;
            return { type: "users", keys: [userKey], queryType: "user_profile" };
        }
    },
    {
        queryType: "posts_comments",
        pathPattern: /^\/posts\/([^/]+)\/comments/,
        buildRequest: (_docs, pathWithQuery) => {
            const postKey = extractPathKey(pathWithQuery, /^\/posts\/([^/]+)\/comments/);
            if (!postKey) return null;
            return { type: "posts", keys: [postKey], queryType: "posts_comments" };
        }
    },
    {
        queryType: "post_single",
        pathPattern: /^\/posts\/([^/]+)$/,
        buildRequest: (_docs, pathWithQuery) => {
            const postKey = extractPathKey(pathWithQuery, /^\/posts\/([^/]+)$/);
            if (!postKey) return null;
            return { type: "posts", keys: [postKey], queryType: "post_single" };
        }
    },
    {
        queryType: "feed_all",
        pathPattern: /^\/feed\/all/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_all" } : null;
        }
    },
    {
        queryType: "feed_city",
        pathPattern: /^\/feed\/city(?!\/stories)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_city" } : null;
        }
    },
    {
        queryType: "feed_near",
        pathPattern: /^\/feed\/near(?!\/stories)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_near" } : null;
        }
    },
    {
        queryType: "feed_city_stories",
        pathPattern: /^\/feed\/city\/stories/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_city_stories" } : null;
        }
    },
    {
        queryType: "feed_near_stories",
        pathPattern: /^\/feed\/near\/stories/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_near_stories" } : null;
        }
    },
    {
        queryType: "feed_news",
        pathPattern: /^\/feed\/news(?!\/stories)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_news" } : null;
        }
    },
    {
        queryType: "feed_news_stories",
        pathPattern: /^\/feed\/news\/stories/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_news_stories" } : null;
        }
    },
    {
        queryType: "feed_user_slug",
        pathPattern: /^\/feed\/user\/slug\/([^/]+)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_user_slug" } : null;
        }
    },
    {
        queryType: "feed_user",
        pathPattern: /^\/feed\/user(?:\/([^/]+)|$)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_user" } : null;
        }
    },
    {
        queryType: "feed_place_slug",
        pathPattern: /^\/feed\/place\/slug\/([^/]+)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_place_slug" } : null;
        }
    },
    {
        queryType: "feed_place",
        pathPattern: /^\/feed\/place\/([^/]+)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_place" } : null;
        }
    },
    {
        queryType: "feed_group_slug",
        pathPattern: /^\/feed\/group\/slug\/([^/]+)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_group_slug" } : null;
        }
    },
    {
        queryType: "feed_group",
        pathPattern: /^\/feed\/group\/([^/]+)/,
        buildRequest: (docs) => {
            const keys = extractFeedPostKeys(docs);
            return keys.length > 0 ? { type: "posts", keys, queryType: "feed_group" } : null;
        }
    },
    {
        queryType: "polygons",
        pathPattern: /^\/polygon-calculations\/polygons(?:\-page)?$/,
        buildRequest: (docs) => {
            const keys: string[] = [];
            docs.forEach((d: any) => {
                if (d.placeKey) keys.push(d.placeKey);
                if (d.placeKeys && Array.isArray(d.placeKeys)) keys.push(...d.placeKeys);
                if (d.palindromeKey) keys.push(d.palindromeKey);
                if (d.polygonKey) keys.push(d.polygonKey);
            });
            return keys.length > 0 ? { type: "polygons", keys: [...new Set(keys)], queryType: "polygons" } : null;
        }
    },
    {
        queryType: "polygon_single",
        pathPattern: /^\/polygon-calculations\/polygons\/([^/]+)$/,
        buildRequest: (docs, pathWithQuery) => {
            const placeKey = extractPathKey(pathWithQuery, /^\/polygon-calculations\/polygons\/([^/]+)$/);
            if (!placeKey) return null;
            return { type: "polygons", keys: [placeKey], queryType: "polygon_single" };
        }
    },
    {
        queryType: "polygon_status",
        pathPattern: /^\/polygon-calculations\/([^/]+)$/,
        buildRequest: (docs, pathWithQuery) => {
            const key = extractPathKey(pathWithQuery, /^\/polygon-calculations\/([^/]+)$/);
            if (!key || key === "trigger" || key === "trigger-one-point" || key === "polygons" || key === "polygons-page") return null;
            return { type: "polygonSchedulerCoordinate", keys: [key], queryType: "polygon_status" };
        }
    },
];

export function resolveExtraDataRequest(docs: any[], batchPointer: string): ExtraDataRequest | null {
    const path = batchPointer.split("?")[0];
    for (const strategy of EXTRA_DATA_STRATEGIES) {
        if (strategy.pathPattern.test(path)) {
            return strategy.buildRequest(docs, path);
        }
    }
    return null;
}
