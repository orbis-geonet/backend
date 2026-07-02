# Orbis Backend Infrastructure

Orbis is a geo-social, federated backend stack where users check in to real-world places, form groups, publish local content, and synchronize clone data through the Orbis Protocol on Solana.

This repository contains the backend services required to run an Orbis deployment or clone node.

## Services

| Service | Path | Description |
|---|---|---|
| `orbis-api` | `orbis-v2_back-end.-mongodb-java/` | Java Spring Boot REST API for auth, profiles, places, groups, feeds, subscriptions, Stripe, and Firebase events. |
| `orbis-clone-proxy` | `clone-proxy/` | Node.js worker that indexes Solana protocol events, syncs MongoDB data, serves clone-to-clone APIs, and registers clone URLs on-chain. |
| `orbis-mongo` | Docker service | MongoDB database used by both services. Runs as a replica set in the provided Compose setup. |

## Architecture

```text
Client apps
   |
   v
orbis-api (Spring Boot)
   |
   | internal calls
   v
orbis-clone-proxy (Node.js)
   |
   | indexes and writes
   v
Orbis Protocol on Solana

Both services use MongoDB for local application and synchronized protocol data.
```

External services used by a full deployment:

- Firebase Authentication and Firebase Realtime Database
- Solana RPC provider
- MongoDB
- Stripe, if subscriptions and purchases are enabled
- AWS SES, if email delivery is enabled
- Branch and Instagram integrations, if those product features are enabled


## Requirements

- Docker Desktop or Docker Engine
- Docker Compose
- Firebase service account JSON
- Solana RPC endpoint
- Solana wallet JSON for the clone proxy
- ORBIS token balance for the clone wallet

For local non-Docker development, also install:

- Node.js 20+
- Java 17
- npm

## Quickstart With Docker Compose

Create shared Docker resources once:

```bash
docker network create orbis-net
docker volume create orbis-mongo-data
```

Place Firebase credentials at:

```text
orbis-v2_back-end.-mongodb-java/firebase/secrets.json
```

Place the clone wallet file at the path expected by your `clone-proxy/.env` and Compose volume mapping, for example:

```text
clone-proxy/wallet/wallet1.json
```

Copy and edit environment files:

```bash
cp clone-proxy/.env.example clone-proxy/.env
```

Then start the stack:

```bash
docker compose up -d --build
```

The Java API is available at:

```text
http://localhost:8080
```

The clone proxy is available at:

```text
http://localhost:3000
```

OpenAPI docs:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

Stop the stack:

```bash
docker compose down
```

Rebuild after code changes:

```bash
docker compose up -d --build
```

## Configuration Overview

The two services must share the same internal API secret.

| Concern | Java backend | Clone proxy |
|---|---|---|
| Shared internal secret | `ORBIS_API_SECRET` | `ORBIS_API_SECRET` |
| Java calls Node | `NODEJS_WORKER_URL` | `CLONE_PORT` / service URL |
| Node calls Java | Java service URL | `JAVA_BACKEND_URL` |
| MongoDB | `SPRING_DATA_MONGODB_URI` | `MONGO_URI`, `LOCAL_DB_NAME` |
| Firebase | `GOOGLE_APPLICATION_CREDENTIALS`, Firebase config | Not used for Firebase admin work |
| Solana | Not direct protocol writer | `RPC_URL`, `PROGRAM_ID`, `MERKLE_TREE_MINT`, `ORBIS_MINT`, wallet JSON |

See the service READMEs for full variable lists.

## Common Operations

Run everything:

```bash
docker compose up -d
```

View logs:

```bash
docker compose logs -f orbis-api
docker compose logs -f orbis-clone-proxy
```

Register a new clone:

```env
REGISTER=true
```

Set that in `clone-proxy/.env`, start the clone proxy, then remove it after registration succeeds.

Update a clone base URL from `clone-proxy/`:

```powershell
npm run update-clone -- -- --url https://genesis.orbis.social --wallet .\wallet\orbis-production-clone-wallet-keypair.json
```

## Documentation

- Java backend: [`orbis-v2_back-end.-mongodb-java/README.md`](./orbis-v2_back-end.-mongodb-java/README.md)
- Clone proxy: [`clone-proxy/README.md`](./clone-proxy/README.md)
- Clone extra data flow: [`clone-proxy/extra-data-flow.md`](./clone-proxy/extra-data-flow.md)

## Development Workflow

Java backend:

```bash
cd orbis-v2_back-end.-mongodb-java
./gradlew bootRun
```

Clone proxy:

```bash
cd clone-proxy
npm install
npm run dev
```

Run formatting for the clone proxy:

```bash
cd clone-proxy
npm run lint
npm run lint:fix
```

Build Java:

```bash
cd orbis-v2_back-end.-mongodb-java
./gradlew bootJar
```

## Contributing

Recommended contribution flow:

1. Open an issue describing the bug, feature, or documentation gap.
2. Keep changes focused to one service or concern.
3. Update the relevant README when behavior or configuration changes.
4. Run the relevant build, test, or lint command before opening a pull request.
5. Never include secrets, private keys, production database dumps, or wallet files in pull requests.

## Security

Report security issues privately to the project maintainers. Do not open public issues for vulnerabilities involving auth, secrets, wallets, payments, or data access.

## License

Add the project license before publishing this repository as open source.
