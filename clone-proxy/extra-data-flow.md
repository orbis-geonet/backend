# Extra Data Synchronization Flow

Added new routes caching for(Auth, Users, Feeds) using the Extra Data Strategy system.

## Overview

When a requester (a node or client) requests data from a provider (the node serving the data), the provider returns the requested data (often a "batch"). However, many Orbis routes (like social feeds or user profiles) contain references to related documents (users, groups, pictures) that might not be included in the primary batch.

To solve this, the proxy uses **Extra Data Strategies** to analyze the returned data and automatically fetch missing related documents during the payment verification (voucher) process.

## How it Works

1.  **Request Identification**: The proxy interceptor (in `network/extra-data-strategies.ts`) watches incoming data from specific API patterns (e.g., `/profile/...`, `/feed/...`, `/login`).
2.  **Strategy Matching**: A strategy matching the URL pattern is selected.
3.  **Key Extraction**: The strategy extracts necessary "Extra Data" keys (like User Keys, Group Keys, or Post Keys) from the primary response payload.
4.  **Voucher Submission**: When the requester submits a payment voucher to the provider, it includes an `extraDataRequest` object containing the `type`, `queryType`, and `keys`.
5.  **Provider Fulfillment**: The provider receives the voucher, validates the payment call on-chain, and executes the specific `PROVIDER_FETCHER` (defined in `routes/v3.ts`) associated with the `queryType`.
6.  **Local Cache Sync**: The provider returns the extra data (encoded as Base64 JSON) in the voucher response. The requester then automatically synchronizes this data into its local MongoDB instance via `syncStateToDb`.

## Supported Extra Data Routes

### Auth & User Routes
- **`/login` / `/signup`**: Fetches the full user state for the authenticated user.
- **`/profile/me`**: Syncs the current user's profile and related data.
- **`/profile/{userKey}`**: Syncs the user's profile based on their key.
- **`/profile/slug/{slug}`**: Resolves a slug to a user key and syncs the profile.
- **`/profile/.../pictures`**: Syncs Orbis-native user pictures.
- **`/profile/.../igpictures`**: Syncs imported Instagram media.
- **`/profile/.../following`**: Syncs the list of users and groups the user follows.
- **`/profile/.../followers`**: Syncs the user's accepted followers.
- **`/profile/me/followers/pending`**: Syncs pending follow requests.
- **`/profile/blockedUsers`**: Syncs the list of blocked users.
- **`/profile/chatusers`**: Syncs user details for a list of chat participants.

### Feed Routes
- **`/feed/all` / `/feed/city` / `/feed/near` / `/feed/news`**: Syncs posts, groups, users, and places appearing in the feed.
- **`/feed/user/...` / `/feed/group/...` / `/feed/place/...`**: Syncs contextual posts and related entities for specific profiles or groups.

### Group Routes
- **`/groups/rating`**: Syncs groups and their related posts.
- **`/groups/{groupKey}`**: Syncs detailed group information.
- **`/groups/{groupKey}/events` / `/groups/{groupKey}/members`**: Syncs related group entities.
