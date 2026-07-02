# Orbis Java Backend

The Orbis Java Backend is the Spring Boot API for Orbis, a geo-social application where users check in to real-world places, create groups, publish posts, and compete for territory on a live map.

This service owns the public REST API for app clients. It works with MongoDB, Firebase, Stripe, and the Orbis Clone Proxy, which indexes and syncs Orbis Protocol data from Solana.

## Responsibilities

- Firebase-backed authentication and user profile management.
- Places, check-ins, map data, geospatial search, and territory sizing.
- Groups, memberships, group ownership, followers, bans, and admins.
- Posts, feeds, stories, comments, likes, events, and previews.
- Subscriptions and purchases through Stripe.
- Notifications through Firebase Realtime Database and Firebase services.
- Internal communication with the Node.js clone proxy.
- OpenAPI documentation through Springdoc.

## Architecture

| Service | Role |
|---|---|
| `orbis-api` | This Spring Boot API. Handles client-facing REST endpoints. |
| `orbis-clone-proxy` | Node.js worker. Indexes Solana protocol events and serves clone-to-clone data. |
| `orbis-mongo` | MongoDB database for app and synced protocol data. |
| Firebase | Authentication, Realtime Database, and service account operations. |
| Stripe | Optional subscriptions, purchases, Connect accounts, and webhooks. |

## Tech Stack

- Java 17
- Spring Boot 2.5.x
- Spring WebFlux
- Spring Security
- Reactive MongoDB
- Firebase Admin SDK
- Springdoc OpenAPI
- Stripe Java SDK
- Gradle wrapper
- Docker

## Repository Layout

| Path | Purpose |
|---|---|
| `src/main/java/to/orbis/v2/backend/controllers` | REST controllers. |
| `src/main/java/to/orbis/v2/backend/services` | Business logic. |
| `src/main/java/to/orbis/v2/backend/repositories` | Mongo repositories and aggregation queries. |
| `src/main/java/to/orbis/v2/backend/models` | DTOs and Mongo entities. |
| `src/main/resources` | Spring configuration and templates. |
| `resizerfunc` | Cloud Function code for media resize/preview workflows. |
| `docs` | Historical and supporting documentation. Code and OpenAPI are the source of truth. |
| `Dockerfile` | Multi-stage Docker build for the API service. |

## Open Source Safety Checklist

Before publishing this repository:

- Replace all committed production credentials with placeholders.
- Rotate any Firebase, Stripe, AWS, MongoDB, Branch, Instagram, Solana, or API secret that has ever been committed.
- Do not commit `firebase/secrets.json` or any Firebase service account file.
- Do not commit `.env` files, wallet files, private keys, or production YAML files containing secrets.
- Prefer `.env.example` and local profile examples with fake values.

This README uses placeholders in commands. Use your own credentials and infrastructure.

## Requirements

For Docker-based setup:

- Docker Desktop or Docker Engine
- Docker Compose
- Firebase service account JSON
- MongoDB, provided by the root Docker Compose stack
- Running clone proxy, provided by the root Docker Compose stack

For local development without Docker:

- Java 17
- Git
- MongoDB 7+
- Firebase service account JSON
- Node.js clone proxy running locally or in Docker

## Configuration

The backend reads configuration from Spring YAML files and environment variables. For open-source deployments, use environment variables or a local Spring profile rather than committing real secrets.

Core variables:

| Variable | Example | Purpose |
|---|---|---|
| `GOOGLE_APPLICATION_CREDENTIALS` | `/app/secrets.json` | Path to Firebase service account JSON. |
| `SPRING_PROFILES_ACTIVE` | `prod-local` | Spring profile to run. For Docker Compose, use `prod-local`. |
| `SPRING_DATA_MONGODB_URI` | `mongodb://orbis-mongo:27017/?retryWrites=true` | Mongo connection string override. |
| `NODEJS_WORKER_URL` | `http://orbis-clone-proxy:3000` | URL Java uses to call the co-located clone proxy. |
| `ORBIS_API_SECRET` | `change-me` | Shared secret used for app and internal Node/Java auth. Must match clone proxy. |
| `port` | `8080` | Server port. Defaults to `8080`. |

External provider settings:

| Setting | Purpose |
|---|---|
| `firebase.apiKey` | Firebase web API key used by auth helper flows. |
| `firebase.databaseUrl` | Firebase Realtime Database URL for map and group notifications. |
| `stripe.*` | Stripe secret/public keys and webhook secrets for subscriptions and purchases. |
| `cloud.aws.*` | AWS SES credentials and region for email delivery. |
| `branch.key` | Branch deep-link key. |
| `instagram.*` | Instagram OAuth settings. |
| `mailUser` / `mailPassword` | SMTP credentials if mail is enabled. |

Recommendation for open source: provide a sanitized `application-local.example.yaml` or `.env.example` and document which features can be disabled when provider credentials are not configured.

## Firebase Credentials

Create a Firebase service account key from Firebase Console:

`Project Settings -> Service Accounts -> Generate new private key`

The repo does not include this file. Place it in a private local path, then set `GOOGLE_APPLICATION_CREDENTIALS` to that path.

The examples below use this conventional local path:

```text
orbis-v2_back-end.-mongodb-java/firebase/secrets.json
```

Create the `firebase/` directory yourself if you use that path. You can also keep the service account outside the repo entirely, such as `/etc/orbis/secrets/firebase-service-account.json`, as long as `GOOGLE_APPLICATION_CREDENTIALS` points to it.

This file must stay uncommitted and should be mounted into containers at runtime.

## Clone Operator Quickstart

A clone operator runs two application services next to MongoDB:

| Service | Default name | Purpose |
|---|---|---|
| MongoDB | `orbis-mongo` | Stores app data and locally synced protocol data. Run it as a replica set for clone proxy change streams. |
| Clone proxy | `orbis-clone-proxy` | Node.js worker that indexes Solana, syncs clone data, and serves clone-to-clone APIs. |
| Java API | `orbis-api` | Spring Boot API used by app clients. |

Local runtime files are intentionally not committed. Create these files, or equivalent private paths, on each operator machine:

| File | Why it exists | Ignore behavior |
|---|---|---|
| `orbis-v2_back-end.-mongodb-java/src/main/resources/application.yaml` | Java backend runtime config. | Ignored by this module's `.gitignore` through `src/main/resources/*.yaml` and `*.yml`. |
| `orbis-v2_back-end.-mongodb-java/firebase/secrets.json` | Example local path for the Firebase Admin service account. Create this path yourself, or use another private path and update `GOOGLE_APPLICATION_CREDENTIALS`. | Ignored by the root ignore rules because the filename is `secrets.json`. |
| `clone-proxy/.env` | Node worker runtime config. | Ignored by `clone-proxy/.gitignore`. |
| `clone-proxy/wallet/wallet.json` | Solana clone wallet keypair. | Ignored by `clone-proxy/.gitignore` through `wallet/`. |

Use placeholders in docs and commits. Put real values only in the ignored local files or deployment secret manager.

### Java `application.yaml`

Create:

```text
orbis-v2_back-end.-mongodb-java/src/main/resources/application.yaml
```

Use this as a safe starting point:

```yaml
spring:
  data:
    mongodb:
      database: ${ORBIS_MONGO_DATABASE:orbis_clone}
      uri: ${SPRING_DATA_MONGODB_URI:mongodb://localhost:27017/?directConnection=true}
  jackson:
    default-property-inclusion: non_null
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USER:nouser}
    password: ${MAIL_PASSWORD:nopassword}
    properties.mail.smtp:
      auth: true
      starttls.enable: true

firebase:
  apiKey: ${FIREBASE_API_KEY:replace-me}
  databaseUrl: ${FIREBASE_DATABASE_URL:https://your-project-default-rtdb.firebaseio.com/}

googlePlaces:
  onePage: true

openstreetmap:
  search-url: ${OPENSTREETMAP_SEARCH_URL:https://photon.komoot.io/reverse}

instagram:
  clientId: ${INSTAGRAM_CLIENT_ID:replace-me}
  redirectUri: ${INSTAGRAM_REDIRECT_URI:http://localhost:8080/igoauth/callback}
  secret: ${INSTAGRAM_SECRET:replace-me}

server:
  port: ${port:8080}

management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus"

nodejs:
  worker:
    url: ${NODEJS_WORKER_URL:http://127.0.0.1:3000}

orbis:
  adminMail: ${ORBIS_ADMIN_MAIL:admin@example.com}
  fromMail: ${ORBIS_FROM_MAIL:noreply@example.com}
  baseUrl: ${ORBIS_BASE_URL:http://localhost:8080/}
  shortLinkPrefix: ${ORBIS_SHORT_LINK_PREFIX:https://your-clone.example.com/link}
  cdnBaseUrl: ${ORBIS_CDN_BASE_URL:https://cdn.example.com/}
  android:
    packageName: ${ORBIS_ANDROID_PACKAGE_NAME:to.orbis.app}
  ios:
    bundleId: ${ORBIS_IOS_BUNDLE_ID:to.orbis.app}
    appStoreId: ${ORBIS_IOS_APP_STORE_ID:0000000000}
  api:
    secret: ${ORBIS_API_SECRET}

stripe:
  enable: false
  test-mode-enable: false
  webhook-enable: false
  partner-commission: "0.0"
  orbis-commission: "0.0"
  stripe-commission: "0.0"
  stripe-addition-fee: "0.0"
  redirect-url: ${STRIPE_REDIRECT_URL:http://localhost:8080}
  stripe-secret-token: ${STRIPE_SECRET_TOKEN:replace-me}
  stripe-public-token: ${STRIPE_PUBLIC_TOKEN:replace-me}
  stripe-payment-webhook-secret: ${STRIPE_PAYMENT_WEBHOOK_SECRET:replace-me}
  stripe-subscription-webhook-secret: ${STRIPE_SUBSCRIPTION_WEBHOOK_SECRET:replace-me}
  stripe-connect-webhook-secret: ${STRIPE_CONNECT_WEBHOOK_SECRET:replace-me}

cloud:
  aws:
    region:
      static: ${AWS_REGION:us-east-1}
      auto: false
    stack:
      auto: false
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID:replace-me}
      secret-key: ${AWS_SECRET_ACCESS_KEY:replace-me}

app:
  email:
    from: ${APP_EMAIL_FROM:noreply@example.com}
    fromName: ${APP_EMAIL_FROM_NAME:Orbis}
    testMode: true
    testModeReceiver: ${APP_EMAIL_TEST_RECEIVER:admin@example.com}
    amazonConfigurationSetName: ${APP_EMAIL_CONFIGURATION_SET:local}

branch:
  key: ${BRANCH_KEY:replace-me}
  create-link-url: ${BRANCH_CREATE_LINK_URL:https://api2.branch.io/v1/url}
```

Keep `stripe.enable`, `stripe.webhook-enable`, and `stripe.test-mode-enable` disabled for clone operator setup unless you are explicitly running the legacy Stripe flow with real keys.

### Clone Proxy `.env`

Create:

```text
clone-proxy/.env
```

Use this as a safe starting point:

```env
MONGO_URI=mongodb://orbis-mongo:27017/?retryWrites=true
LOCAL_DB_NAME=orbis_clone
WALLET_PATH=wallet.json
RPC_URL=https://your-solana-rpc.example
PROGRAM_ID=<orbis-program-id>
MERKLE_TREE_MINT=<merkle-tree-account>
ORBIS_MINT=<orbis-token-mint>
CLONE_PORT=3000
JAVA_BACKEND_URL=http://orbis-api:8080
CLONE_BASE_URL=https://your-clone.example.com
ORBIS_API_SECRET=<same-value-as-java>
API_SECRET=<local-cross-clone-secret>
```

The Java and Node services must agree on the internal connection settings:

| Direction | Setting |
|---|---|
| Java to clone proxy | Java `nodejs.worker.url`, or `NODEJS_WORKER_URL` in deployment. |
| Clone proxy to Java | Clone `JAVA_BACKEND_URL`. |
| Shared internal auth | Same `ORBIS_API_SECRET` value in both services. |

### Docker Startup

From the repository root:

```bash
docker network create orbis-net
docker volume create orbis-mongo-data
docker compose up -d --build
```

The Compose stack uses the existing service names `orbis-mongo`, `orbis-clone-proxy`, and `orbis-api`. In Docker, use container URLs:

```text
NODEJS_WORKER_URL=http://orbis-clone-proxy:3000
JAVA_BACKEND_URL=http://orbis-api:8080
```

### Local Manual Startup

Start MongoDB as a replica set first. The clone proxy can run without change streams, but live sync requires a replica set.

Start the clone proxy:

```bash
cd clone-proxy
npm install
npm run dev
```

Start the Java backend from another terminal:

```bash
cd orbis-v2_back-end.-mongodb-java
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/firebase/secrets.json"
export ORBIS_API_SECRET="<same-value-as-clone-proxy>"
export NODEJS_WORKER_URL="http://127.0.0.1:3000"
./gradlew bootRun
```

On Windows PowerShell:

```powershell
cd orbis-v2_back-end.-mongodb-java
$env:GOOGLE_APPLICATION_CREDENTIALS="$PWD\firebase\secrets.json"
$env:ORBIS_API_SECRET="<same-value-as-clone-proxy>"
$env:NODEJS_WORKER_URL="http://127.0.0.1:3000"
.\gradlew.bat bootRun
```

Before publishing or creating a clean one-commit remote, check ignored files:

```bash
git status --ignored --short
```

Confirm local YAML files, `.env`, wallet files, and Firebase secrets are not staged.

## Run With Docker Compose

From the repository root:

```bash
docker network create orbis-net
docker volume create orbis-mongo-data
docker compose up -d --build
```

The API will be available at:

```text
http://localhost:8080
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

## Run As A Standalone Container

Build from this directory:

```bash
docker build -t orbis-backend .
```

Run on Linux/macOS:

```bash
docker run -d \
  --name orbis-api \
  --network orbis-net \
  -p 8080:8080 \
  -e GOOGLE_APPLICATION_CREDENTIALS=/app/secrets.json \
  -e ORBIS_API_SECRET=change-me \
  -e SPRING_PROFILES_ACTIVE=prod-local \
  -e SPRING_DATA_MONGODB_URI="mongodb://orbis-mongo:27017/?retryWrites=true" \
  -e NODEJS_WORKER_URL="http://orbis-clone-proxy:3000" \
  -v "$(pwd)/firebase/secrets.json:/app/secrets.json:ro" \
  orbis-backend
```

Run on Windows PowerShell:

```powershell
docker run -d `
  --name orbis-api `
  --network orbis-net `
  -p 8080:8080 `
  -e GOOGLE_APPLICATION_CREDENTIALS=/app/secrets.json `
  -e ORBIS_API_SECRET=change-me `
  -e SPRING_PROFILES_ACTIVE=prod-local `
  -e SPRING_DATA_MONGODB_URI="mongodb://orbis-mongo:27017/?retryWrites=true" `
  -e NODEJS_WORKER_URL="http://orbis-clone-proxy:3000" `
  -v "${PWD}/firebase/secrets.json:/app/secrets.json:ro" `
  orbis-backend
```

## Run Locally With Gradle

Linux/macOS:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/firebase/secrets.json"
export SPRING_PROFILES_ACTIVE=prod-local
export SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/?retryWrites=true"
export NODEJS_WORKER_URL="http://127.0.0.1:3000"
export ORBIS_API_SECRET="change-me"
./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="$PWD\firebase\secrets.json"
$env:SPRING_PROFILES_ACTIVE="prod-local"
$env:SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/?retryWrites=true"
$env:NODEJS_WORKER_URL="http://127.0.0.1:3000"
$env:ORBIS_API_SECRET="change-me"
.\gradlew.bat bootRun
```

## Build And Test

Build a jar:

```bash
./gradlew bootJar
```

Run tests:

```bash
./gradlew test
```

On Windows:

```powershell
.\gradlew.bat bootJar
.\gradlew.bat test
```

## API Documentation

When running locally, use:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

Some generated Swagger URLs may show `http` when your deployment requires `https`. Use the scheme that matches your deployment.

## Authentication

Orbis uses Firebase Authentication for user identity.

Protected routes accept:

```http
Authorization: Bearer <firebase-id-token>
```

Email/password convenience routes:

| Endpoint | Purpose |
|---|---|
| `POST /signup` | Create a Firebase user and backend profile. |
| `POST /login` | Login and return Firebase tokens. |
| `POST /refresh` | Exchange a refresh token for a new id token. |
| `POST /password` | Change password for an authenticated user. |
| `POST /forgotPassword` | Start a password reset flow. |
| `POST /profile/me` | Create or update the backend profile after Firebase auth. |

If clients authenticate directly with Firebase SDKs, they should still call `POST /profile/me` on first connect so backend profile data, memberships, and related features are initialized.

## Node Worker Authentication

The Java backend and clone proxy use a shared secret, `ORBIS_API_SECRET`.

Expected flows:

| Flow | Header | Description |
|---|---|---|
| App or trusted deployment to Java | `X-Master-Key` | Static deployment secret for trusted service calls. |
| Node worker to Java | `X-API-Key` | Short-lived HMAC token generated by the clone proxy. |
| Java to local Node worker | `X-Master-Key` | Used when Java forwards work to the co-located worker. |

`NODEJS_WORKER_URL` tells Java where to reach the worker. In Docker Compose this is usually:

```text
http://orbis-clone-proxy:3000
```

For local development this is usually:

```text
http://127.0.0.1:3000
```

## Main API Areas

| Area | Examples |
|---|---|
| Auth | `/login`, `/signup`, `/refresh`, `/password`, `/forgotPassword` |
| Profile/users | `/profile`, `/profile/me`, followers, blocking, pictures, FCM tokens |
| Places | `/places`, `/places/map`, place details, check-ins, place rates |
| Groups | `/groups`, `/groups/map`, memberships, followers, admins, bans |
| Feed | `/feed/all`, `/feed/city`, `/feed/near`, stories, user/place/group feeds |
| Posts | `/posts`, comments, likes, reports, shares |
| Events | `/events`, attendees, attending state |
| Subscriptions | `/groups/subscription/*`, `/profile/subscription/*` |
| Stripe | `/stripe`, `/stripe/webhook/*` |
| Previews | `/previews/*` |
| Notifications | `/notifications` |

The OpenAPI document is the source of truth for request and response shapes.

## Places And Map Behavior

Places have a dynamic radius. The server returns two sizes:

| Field | Meaning |
|---|---|
| `size` | Current size, ready for immediate display. |
| `lastSize` | Size at `lastCheckInTimestamp`, useful for client-side decay. |

Clients can compute decay locally to avoid unnecessary realtime traffic.

Pseudo-code:

```java
var placeSize = lastSize;
var elapsedTime = (double) Duration.between(lastCheckInTimestamp, Instant.now()).toMillis();

if (elapsedTime < DAY && placeSize >= 500) {
  placeSize = (placeSize - 500) * ((DAY - elapsedTime) / DAY) + 500;
} else if (placeSize >= 500) {
  placeSize = 500 * (YEAR - elapsedTime) / YEAR;
} else {
  placeSize = placeSize * (YEAR - elapsedTime) / YEAR;
}

if (placeSize < 0) placeSize = 0.0;
```

Realtime notifications are only needed when a non-time-based size change happens, such as a check-in or nearby place interaction. Updates are published to Firebase Realtime Database under:

```text
/placeSizes/{geohash4}/{placeKey}
```

Payload shape:

```json
{
  "placeKey": "<placeKey>",
  "lastCheckInTimestamp": 1710000000000,
  "lastSize": 750.0,
  "coordinates": {
    "longitude": -12.0,
    "latitude": -52.0
  }
}
```

Clients should subscribe to `children_changed` events for the geohash4 cells covering the visible viewport. Four-character geohashes are used because they are coarse enough for map viewport subscriptions while keeping event fan-out manageable.

## Groups

When enough group members check in at a place, the group can gain ownership over that place. Ownership updates are published to Firebase Realtime Database under:

```text
/groupOwnership/{geohash4}/{placeKey}
```

Example payload:

```json
{
  "colorIndex": 6,
  "groupKey": "<groupKey>",
  "imageName": "<groupKey>.jpg",
  "name": "Coding Challenges and Interviews",
  "placeCoordinates": {
    "latitude": -24.06170082092285,
    "longitude": -46.56660079956055
  },
  "placeKey": "4657624cb142bb0013a2bf2a6da90793",
  "solidColorHex": "#2196F3",
  "strokeColorHex": "#133A58",
  "timestamp": 1619420211525,
  "validCheckins": 1
}
```

Clients can ignore events outside the current viewport and fetch group/place details when a relevant but unloaded object appears.

## Subscriptions And Purchases

Group subscriptions are managed by the main group admin.

A group can activate subscriptions when:

- the main admin has a Stripe account in `READY_TO_USE` status;
- the group has at least one subscription configured.

Key endpoints:

| Endpoint | Purpose |
|---|---|
| `GET /groups/subscription/info` | Return commission data for pricing. |
| `POST /groups/subscription/{groupKey}` | Create a subscription. |
| `PUT /groups/subscription/{groupKey}` | Edit a subscription. |
| `DELETE /groups/subscription/{groupKey}/{subscriptionKey}` | Delete a subscription. |
| `GET /groups/subscription/{groupKey}` | List subscriptions for a group. |
| `POST /groups/subscription/{groupKey}/activate` | Activate subscriptions for a group. |
| `POST /groups/subscription/{groupKey}/deactivate` | Deactivate subscriptions for a group. |
| `POST /profile/subscription/{subscriptionKey}/subscribe` | Subscribe the current user. |
| `POST /profile/subscription/{subscriptionKey}/unsubscribe` | Unsubscribe the current user. |
| `GET /profile/subscriptions` | List current user subscriptions. |
| `GET /stripe` | Get current user's Stripe account status. |
| `POST /stripe` | Create a Stripe Connect account. |
| `PUT /stripe` | Edit a Stripe Connect account. |
| `DELETE /stripe` | Delete a Stripe Connect account when allowed. |

Stripe onboarding links are single-use and expire. When completed or already used, users are redirected to the configured Orbis redirect URL.

## Development Notes

- Code and OpenAPI are the source of truth for endpoint shapes.
- Some historical notes live under `docs/` and `src/main/java/README.md`.
- Mongo aggregation helpers are implemented under `org.springframework.data.mongodb.core.aggregation` to support pipeline operations not covered by the Spring wrapper used here.
- The backend is reactive; service methods usually return Reactor `Mono` or `Flux`.

## Troubleshooting

### Firebase credentials not found

Check `GOOGLE_APPLICATION_CREDENTIALS` and make sure the service account JSON is mounted or present at that path.

### Java cannot reach the clone proxy

Check `NODEJS_WORKER_URL`. In Docker Compose, use `http://orbis-clone-proxy:3000`; locally, use `http://127.0.0.1:3000`.

### Java and Node auth fails

Make sure `ORBIS_API_SECRET` is set and identical in both services.

### Mongo connection fails

Check `SPRING_DATA_MONGODB_URI`. If running in Docker, use the Mongo service name, not `localhost`.

### Swagger links have the wrong scheme

Use the scheme from your deployment. Local development usually uses `http`; production deployments usually use `https`.

## Related Docs

- Root stack README: `../README.md`
- Clone proxy README: `../clone-proxy/README.md`
- Clone proxy extra data flow: `../clone-proxy/extra-data-flow.md`
