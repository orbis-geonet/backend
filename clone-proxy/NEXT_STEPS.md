# NEXT_STEPS.md

Drafting the transition roadmap for our Orbis Social V3 deployment and contract optimization.

### 1- Production Dockerization & Mainnet Flow
Before we open-source the project, we must dockerize the Node.js worker to ensure a consistent environment for providers.

- **Dockerization Strategy**: I will create a `Dockerfile` using a lightweight Node.js Alpine image. We need to ensure that the container handles the MongoDB replica set connection strings and injects Solana wallet keys securely via volume mounts or secrets. This step is critical to make the node "plug-and-play" for the community.
- **Deployment Flow**: Once dockerized, our deployment to Mainnet will follow a streamlined process: we build the production image, deploy the smart contract to Mainnet-Beta, and initialize the global configuration state.

### 2- Smart Contract Fine-Tuning
We need to adjust several hardcoded numbers in our smart contract before moving to Mainnet. These fixed parameters determine trust and economics and need final review:

- **Trust Score Dynamics**:
    - **Initial Score**: Currently set at `1000` (in `register_clone`). We should evaluate if new providers should start with a lower "probationary" score.
    - **Punishment Scaling**: In `flag_provider`, we subtract `100` points. For `flag_unpaid_request`, we only subtract `10`. We need to re-calibrate these based on the severity of the offense.
    - **Trust Ceiling**: The current cap is `2000` (in `claim_streaming_payment`). We need to decide if this provides enough overhead for long-term reliable providers.
- **Economic Parameters**:
    - **Treasury Cut**: The `2%` fee in `claim_streaming_payment` is hardcoded. We need to confirm if this is our final mainnet percentage.
    - **Cooling Period**: The `86400` second (1 day) window for withdrawals in `withdraw_unused_funds` should be reviewed to see if it allows enough time for dispute resolution.

### 3- Optimized Merkle Tree (No Canopy)
For our specific use case, we are treating the Merkle Tree as a high-throughput event log.

- **Removing Canopy**: We do not need a canopy depth for this implementation. Since we are fetching individual transactions and extracting data from `noop` instructions via RPC, we don't need the on-chain cache (canopy) typically used for verifying proofs in-contract.
- **Cost Impact**: Removing the canopy significantly reduces our upfront SOL cost. A Depth 20 tree without a canopy will cost approximately **0.42 SOL**, compared to the ~1.4 SOL required if we used a canopy. This makes it much cheaper for us to scale the network.
- **Program Deployment**: Deploying the compiled Anchor program will still cost approximately **2.5 - 3.5 SOL**.
