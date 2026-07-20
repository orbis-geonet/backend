# Orbis Clone Proxy

The Orbis Clone Proxy is the Node.js worker for an Orbis clone node. It indexes Orbis Protocol events from Solana, stores synchronized data in MongoDB, exposes clone-to-clone data APIs, and forwards app requests to the Java backend when needed.

It is designed to run next to:

- a MongoDB database, preferably as a replica set for live change streams;
- the Java Orbis API (`orbis-api`);
- a Solana wallet registered as an Orbis clone node.

## What It Does

- Reads Orbis Protocol events from Solana and caches them locally.
- Watches local MongoDB changes and commits constant-size manifests to the protocol.
- Serves clone data to other registered clones.
- Authenticates cross-clone requests using short-lived HMAC tokens.
- Proxies selected requests to the Java backend using an internal API key.
- Registers or updates the on-chain base URL for a clone wallet.

## Requirements

- Node.js 20+
- npm
- Docker, if running with containers
- MongoDB 7+, ideally configured as a replica set
- Solana RPC endpoint
- Solana wallet JSON file for the clone owner
- ORBIS token balance for the clone wallet
- Matching `ORBIS_API_SECRET` shared with the Java backend

## Repository Layout

| Path | Purpose |
|---|---|
| `index.ts` | Main Express server and runtime wiring. |
| `config.ts` | Required environment validation and shared config. |
| `blockchain/` | Solana indexer, batch push, wallet, and protocol utilities. |
| `network/` | Cross-clone payment and data retrieval logic. |
| `routes/` | HTTP routes exposed by the clone proxy. |
| `scripts/` | Admin scripts for registration, initialization, minting, and URL updates. |
| `programs/idl/` | Anchor IDL used to call the Orbis Protocol. |
| `wallet/` | Local wallet files. Do not commit real wallets. |
| `extra-data-flow.md` | Detailed notes on how extra linked data is fetched and cached. |

## Environment

Copy `.env.example` to `.env` and update the values for your deployment.

Required variables:

| Variable | Example | Purpose |
|---|---|---|
| `MONGO_URI` | `mongodb://orbis-mongo:27017/?directConnection=true` | MongoDB connection used by the proxy. In Docker Compose, use the Mongo container name. |
| `LOCAL_DB_NAME` | `orbis_local` | MongoDB database name used by this clone. |
| `WALLET_PATH` | `wallet.json` | Wallet file name under `./wallet/` for the running clone. |
| `RPC_URL` | `https://api.mainnet-beta.solana.com` | Solana RPC endpoint. Use a reliable paid RPC for production. |
| `PROGRAM_ID` | `x2HARL2...` | Orbis Protocol program id. |
| `MERKLE_TREE_MINT` | `H8gv...` | Merkle tree account used by the protocol indexer. |
| `ORBIS_MINT` | `GBGP...` | ORBIS token mint. |
| `CLONE_PORT` | `3000` | HTTP port for this proxy. |
| `JAVA_BACKEND_URL` | `http://orbis-api:8080` | URL of the co-located Java backend. |
| `CLONE_BASE_URL` | `https://your-clone.example.com` | Public base URL written on-chain for other clones to reach you. |
| `ORBIS_API_SECRET` | `change-me` | Shared secret between this proxy and the Java backend. Must match Java config. |
| `API_SECRET` | `change-me-too` | Local secret used to sign and validate cross-clone `X-Orbis-Key` tokens. |

Optional variables:

| Variable | Default | Purpose |
|---|---|---|
| `REGISTER=true` | `false` | Register this wallet as a clone node on startup if not already registered. |
| `DRY_RUN=true` | `false` | Check MongoDB, Solana RPC, wallet SOL, and ORBIS balance, then exit. |
| `DISABLE_BATCH=true` | `false` | Disable local MongoDB change watching and batch pushing. |
| `SKIP_HISTORY=true` | `false` | Start from the current chain head instead of scanning historical events. |
| `NO_CACHE=true` | `false` | Skip fetching and caching linked data during cross-clone retrieval. |
| `OVERRIDE=true` | `false` | Re-create escrow accounts if a provider mismatch is detected. |
| `AUTO_FETCH_EVENTS=true` | `false` | Eagerly fetch and pay for each committed manifest's full payload (mirror) instead of lazy per-request retrieval. |
| `SELF_CONSUME=true` | `false` | Also index this clone's own manifests. For single-machine testing only. |
| `PUBLIC_FLUSH_COUNT` | `200` | Public lane commits a manifest when this many changes are queued. |
| `PUBLIC_FLUSH_AGE_MS` | `120000` | Public lane commits when the oldest queued change reaches this age. |
| `DERIVED_FLUSH_COUNT` | `500` | Derived (noisy) lane queue-size threshold. |
| `DERIVED_FLUSH_AGE_MS` | `600000` | Derived lane age threshold (defaults to 10 minutes). |
| `MANIFEST_FLUSH_TICK_MS` | `15000` | How often the flush ticker re-checks the lane thresholds. |
| `MONGO_TIMEOUT_MS` | `30000` | Mongo connection and socket timeout. |
| `JAVA_BACKEND_TIMEOUT_MS` | `30000` | Timeout for calls forwarded to the Java backend. |
| `EVENT_SYNC_STALE_MS` | `120000` | Time after which a syncing network event is treated as stale. |
| `MONGO_WATCH_RESTART_MS` | `30000` | Delay before restarting Mongo watch streams. |

Note: if your `.env.example` still contains `LOCAL_MONGO_URI`, rename it to `MONGO_URI`; the current runtime reads `MONGO_URI`.

## Wallets

Wallet files are standard Solana JSON arrays. Keep them out of git.

For Docker, mount the wallet directory and set `WALLET_PATH` to the file name:

```env
WALLET_PATH=wallet.json
```

For local scripts, pass wallet paths carefully:

- runtime startup expects wallet files under `./wallet/`;
- `scripts/update-base-url.ts` accepts either absolute paths or paths relative to `clone-proxy`.

## Install

```bash
npm install
```

## Run Locally

Create `.env`, place your wallet file under `wallet/`, then run:

```bash
npm run dev
```

For a connectivity-only check:

```bash
DRY_RUN=true npm run dev
```

On PowerShell:

```powershell
$env:DRY_RUN="true"
npm run dev
Remove-Item Env:\DRY_RUN
```

## Run With Docker Compose

The easiest full-stack path is from the repository root:

```bash
docker network create orbis-net
docker volume create orbis-mongo-data
docker compose up -d --build
```

The root `docker-compose.yml` starts:

- `orbis-mongo`
- `orbis-clone-proxy`
- `orbis-api`

## Run As A Standalone Container

Build:

```bash
docker build -t orbis-clone-proxy .
```

Run on Linux/macOS:

```bash
docker run \
  --network orbis-net \
  --env-file .env \
  -v "$(pwd)/wallet:/app/wallet:ro" \
  -p 3000:3000 \
  orbis-clone-proxy
```

Run on Windows PowerShell:

```powershell
docker run `
  --network orbis-net `
  --env-file .env `
  -v "${PWD}/wallet:/app/wallet:ro" `
  -p 3000:3000 `
  orbis-clone-proxy
```

## Register A Clone

A clone wallet must be registered on-chain before it can operate.

Set this once in `.env` when starting a new clone:

```env
REGISTER=true
```

Then start the proxy. The startup checks will verify SOL balance, ORBIS token balance, and the clone PDA. If the clone is not registered, the proxy will call the protocol `register_clone` instruction using `CLONE_BASE_URL`.

After registration succeeds, remove `REGISTER=true` from production environments to avoid accidental repeated registration attempts.

## Update The On-Chain Clone Base URL

Use this when the public URL for a registered clone changes.

Recommended command from the `clone-proxy` directory:

```powershell
npm run update-clone -- -- --url https://genesis.orbis.social --wallet .\wallet\orbis-production-clone-wallet-keypair.json
```

The extra `--` is intentional for newer npm versions on Windows, where flags may otherwise be interpreted as npm config. On npm versions that forward flags normally, this also works:

```bash
npm run update-clone -- --url https://genesis.orbis.social --wallet wallet/orbis-production-clone-wallet-keypair.json
```

The script prints the wallet file, owner public key, clone PDA, current URL, and transaction signature before exiting.

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Run the proxy with `tsx watch`. |
| `npm run start` | Run the proxy with `tsx index.ts`; includes the `prestart` hook. |
| `npm run init:contract` | Initialize protocol configuration from `scripts/initialize-program.ts`. |
| `npm run update-clone` | Alias for updating a clone base URL on-chain. |
| `npm run update:base-url` | Original base URL update script. |
| `npm run lint` | Check formatting with Prettier. |
| `npm run lint:fix` | Format files with Prettier. |
| `npm run test:geo` | Run geospatial tests. |
| `npm run test:hash` | Run hash tests. |
| `npm run test:api` | Run API key tests. |
| `npm run test:idl` | Fetch or inspect IDL data. |

## Sync Flow (Manifest v2)

Each clone is both a producer and a consumer.

**Producer.** Mongo change streams feed a durable `network_outbox`, coalescing repeated edits per record. Records are split into two lanes — `public` (users, groups, posts, places, comments, follows) flushed fast, and `derived` (counters, checkins, stories, etc.) flushed lazily. On flush the clone writes the full documents to `network_batches`, a hashes-only index to `network_index_batches`, then commits a `sync_index_manifest` transaction carrying only `manifestHash` + `payloadHash`. That transaction is a **constant size** no matter how many records the batch covers.

**Consumer.** The clone listens for other clones' `IndexManifestCommitted` events, fetches `/v3/index-batches/:id`, verifies `sha256 == manifestHash`, and materializes each entry into `network_events` with status `pending`. No documents are fetched yet — only the searchable hash index.

**Retrieval.** When Java looks up a record and finds a matching `network_events` row, it forwards to the node, which fetches that record from the owning clone, pays through the streaming escrow, caches it locally, and marks the event `synced`. With `AUTO_FETCH_EVENTS=true` the clone instead eagerly fetches and pays for each committed batch's full payload.

MongoDB change streams require a replica set. Without one the proxy still serves and indexes chain data, but cannot watch local changes to publish them. See [Extra Data Flow](./extra-data-flow.md) for linked-object fetch-and-cache details.

## Authentication Architecture

There are three authentication paths.

### Orbis App To Java API

The app calls Java with a static deployment secret:

```http
X-Master-Key: <ORBIS_API_SECRET>
```

Firebase JWT auth still applies to protected user routes through:

```http
Authorization: Bearer <firebase-id-token>
```

### Node Worker To Java API

The Node worker signs a short-lived HMAC token with `ORBIS_API_SECRET` and sends it as:

```http
X-API-Key: <signed-internal-token>
```

Java validates this token before processing internal worker requests.

### Other Clones To This Node

A remote clone obtains an API key from this node:

```http
POST /v3/api-key
```

Body:

```json
{
  "publicKey": "<solana-pubkey>",
  "signature": "<base64-signature-of-ORBIS_API_KEY_REQ>"
}
```

Requirements:

- requester must be a registered Clone Node PDA on-chain;
- requester must meet the ORBIS token balance requirement;
- requester must sign the expected message.

The returned token is sent on later clone requests:

```http
X-Orbis-Key: <token>
```

`API_SECRET` signs and validates these cross-clone tokens. It is local to one clone and should not be shared.

## Java Backend Coupling

The Java backend and clone proxy must agree on:

| Setting | Node variable | Java setting |
|---|---|---|
| Shared internal secret | `ORBIS_API_SECRET` | `orbis.api.secret` / `ORBIS_API_SECRET` |
| Node URL from Java | `CLONE_BASE_URL` for public identity | `NODEJS_WORKER_URL` for local calls |
| Java URL from Node | `JAVA_BACKEND_URL` | Java server base URL |

For co-located deployments, Java should call the worker at `http://127.0.0.1:3000` or the Docker service name `http://orbis-clone-proxy:3000`.

## Troubleshooting

### `MONGO_URI environment variable or positional argument is required`

Set `MONGO_URI` in `.env`. If your example file uses `LOCAL_MONGO_URI`, rename it.

### `REPLICA_SET_REQUIRED`

MongoDB is not running as a replica set. The proxy can still serve and index data, but live MongoDB watching is disabled. Use the root Docker Compose setup or run Mongo with `--replSet rs0`.

### `Wallet file not found`

Check whether the command expects a file name under `./wallet/` or a full path. Runtime startup uses `WALLET_PATH` under `./wallet/`; the base URL update script can resolve absolute paths.

### `This wallet is not registered as a clone node on-chain`

Set `REGISTER=true` and restart, or register the clone manually. The wallet also needs SOL for fees and ORBIS tokens.

### Solana RPC timeouts

Use a reliable RPC endpoint. Public endpoints are often rate-limited and can make historical indexing fail.

## Security Notes

- Never commit wallet JSON files.
- Never commit real `.env` files.
- Use different values for `API_SECRET` and `ORBIS_API_SECRET`.
- Rotate any secret that has ever been committed or shared publicly.
- Treat Solana keypairs as production private keys.

## Related Docs

- [Extra Data Flow](./extra-data-flow.md)
- [Mainnet Deployment Notes](./MAINNET_DEPLOYMENT.md)
- Java backend README: `../orbis-v2_back-end.-mongodb-java/README.md`
