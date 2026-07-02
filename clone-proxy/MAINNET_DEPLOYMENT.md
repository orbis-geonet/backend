# Orbis Protocol Mainnet Fresh Deployment Guide

This guide explains how to build and deploy the Orbis Anchor program on Solana mainnet, create the ORBIS SPL token mint, create the compression Merkle tree, and initialize the program.

The program source is:

```text
clone-proxy/programs/orbis-protocol/src/lib.rs
```

Run the Solana, Anchor, and TypeScript commands from:

```bash
cd /mnt/c/Users/Ramzi/Documents/orbis-social-v3/clone-proxy
```

## 1. Project Structure

Relevant files:

```text
orbis-social-v3/
  .gitignore
  clone-proxy/
    Anchor.toml
    Cargo.toml
    package.json
    token_config.json
    assets/
      orbis_logo.png
    programs/
      idl/orbis_protocol.json
      orbis-protocol/
        Cargo.toml
        src/lib.rs
    scripts/
      initialize-program.ts
      mint-token.ts
    test/
      create-merkle-tree.ts
```

The Anchor program name is:

```text
orbis_protocol
```

The program ID must be written into:

```text
clone-proxy/programs/orbis-protocol/src/lib.rs
clone-proxy/Anchor.toml
```

This guide assumes a fresh deployment, so you will generate a new program ID and sync it into those files.

## 2. Git Ignore and Secret Files

The root `.gitignore` already ignores generated output and common dependency folders such as:

```text
node_modules/
dist/
target/
*.log
secrets.json
```

`clone-proxy/.gitignore` also ignores the local deployment/runtime files inside the Anchor workspace:

```text
/node_modules
.env
/logs
package-lock.json
/wallet
/target
Cargo.lock
```

That means these files should stay local and untracked:

```text
clone-proxy/.env
clone-proxy/wallet/
clone-proxy/target/
```

Do not commit wallets, `.env` files, private RPC URLs, Pinata tokens, recovery phrases, or keypair JSON files. Before committing, still run `git status --short` and confirm no secret file is staged.

## 3. Install WSL

Open PowerShell as Administrator.

Install Ubuntu:

```powershell
wsl --install -d Ubuntu
```

Restart Windows if prompted.

Check WSL:

```powershell
wsl --list --verbose
```

Enter Ubuntu:

```powershell
wsl -d Ubuntu
```

On first launch, create your Linux username and password.

## 4. Install Ubuntu Packages

Inside WSL Ubuntu:

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y build-essential pkg-config libssl-dev libudev-dev curl git ca-certificates
```

## 5. Enter the Repo from WSL

Your Windows repo path:

```text
C:\Users\Ramzi\Documents\orbis-social-v3
```

maps to this WSL path:

```text
/mnt/c/Users/Ramzi/Documents/orbis-social-v3
```

Enter the Anchor workspace:

```bash
cd /mnt/c/Users/Ramzi/Documents/orbis-social-v3/clone-proxy
```

Verify the workspace:

```bash
pwd
ls Anchor.toml Cargo.toml package.json programs/orbis-protocol/src/lib.rs
```

## 6. Install Rust, Solana, Anchor, Node, and Yarn

Run the Solana installer:

```bash
curl --proto '=https' --tlsv1.2 -sSfL https://solana-install.solana.workers.dev | bash
```

Reload your shell:

```bash
exec "$SHELL" -l
```

Verify tools:

```bash
rustc --version
cargo --version
solana --version
anchor --version
node --version
yarn --version
```

This repo uses Anchor `1.0.0` crates in `clone-proxy/Cargo.toml`:

```toml
anchor-lang = { version = "1.0.0", features = ["init-if-needed"] }
anchor-spl = { version = "1.0.0" }
```

If the installed Anchor CLI does not match, switch with AVM:

```bash
avm install 1.0.0
avm use 1.0.0
anchor --version
```

If `avm` is missing:

```bash
cargo install --git https://github.com/coral-xyz/anchor avm --locked --force
avm install 1.0.0
avm use 1.0.0
```

## 7. Install Node Dependencies

From `clone-proxy`:

```bash
cd /mnt/c/Users/Ramzi/Documents/orbis-social-v3/clone-proxy
```

Install dependencies:

```bash
yarn install
```

or:

```bash
npm install
```

Use one package manager consistently for the deploy branch.

## 8. Create the Mainnet Deploy Wallet

Create a dedicated deploy/admin wallet:

```bash
mkdir -p "$HOME/.config/solana"
solana-keygen new --outfile "$HOME/.config/solana/orbis-mainnet-deployer.json"
chmod 700 "$HOME/.config/solana"
chmod 600 "$HOME/.config/solana/orbis-mainnet-deployer.json"
```

Set it for this shell:

```bash
export DEPLOY_WALLET="$HOME/.config/solana/orbis-mainnet-deployer.json"
solana config set --keypair "$DEPLOY_WALLET"
solana address
```

Fund this wallet with real mainnet SOL before continuing.

The scripts in this repo expect a wallet at `clone-proxy/wallet/wallet.json`, so copy the deploy wallet there locally:

```bash
mkdir -p wallet
cp "$DEPLOY_WALLET" wallet/wallet.json
chmod 600 wallet/wallet.json
```

## 9. Configure Mainnet RPC and .env

Use a private mainnet RPC if possible:

```bash
export RPC_URL="<YOUR_MAINNET_RPC_URL>"
solana config set --url "$RPC_URL"
solana config get
solana balance
```

Create the initial `.env`:

```bash
cat > .env <<EOF
RPC_URL=$RPC_URL
PINATA_JWT=<YOUR_PINATA_JWT>
EOF
```

`PINATA_JWT` is required by `scripts/mint-token.ts` because the script uploads the ORBIS logo and token metadata JSON to IPFS through Pinata.

## 10. Generate a Fresh Program ID

Anchor expects the program keypair here:

```text
clone-proxy/target/deploy/orbis_protocol-keypair.json
```

Generate it:

```bash
mkdir -p target/deploy
solana-keygen new --outfile target/deploy/orbis_protocol-keypair.json
```

Export the generated program ID:

```bash
export PROGRAM_ID="$(solana-keygen pubkey target/deploy/orbis_protocol-keypair.json)"
echo "$PROGRAM_ID"
```

Back up this private keypair securely:

```text
clone-proxy/target/deploy/orbis_protocol-keypair.json
```

Do not commit it.

## 11. Sync the Program ID into Anchor

Write the generated program ID into `lib.rs` and `Anchor.toml`:

```bash
anchor keys sync
```

Verify all program IDs match:

```bash
solana-keygen pubkey target/deploy/orbis_protocol-keypair.json
grep -n "declare_id" programs/orbis-protocol/src/lib.rs
grep -n "orbis_protocol" Anchor.toml
```

Append the program ID to `.env`:

```bash
cat >> .env <<EOF
PROGRAM_ID=$PROGRAM_ID
EOF
```

## 12. Build the Program

Build:

```bash
anchor build
```

Expected output:

```text
target/deploy/orbis_protocol.so
target/idl/orbis_protocol.json
```

Copy the generated IDL into the tracked IDL used by the TypeScript service:

```bash
cp target/idl/orbis_protocol.json programs/idl/orbis_protocol.json
```

Review the deployment source changes:

```bash
git diff -- Anchor.toml programs/orbis-protocol/src/lib.rs programs/idl/orbis_protocol.json
```

## 13. Deploy the Program to Mainnet

Confirm mainnet RPC and deploy wallet:

```bash
solana config set --url "$RPC_URL"
solana config set --keypair "$DEPLOY_WALLET"
solana config get
solana balance
```

Deploy:

```bash
anchor deploy \
  --provider.cluster "$RPC_URL" \
  --provider.wallet "$DEPLOY_WALLET"
```

Verify:

```bash
solana program show "$PROGRAM_ID" --url "$RPC_URL"
```

Confirm the program is executable and the upgrade authority is the deploy wallet.

## 14. ORBIS Token Config

The ORBIS token script reads:

```text
clone-proxy/token_config.json
```

Current config:

```json
{
  "name": "Orbis",
  "symbol": "ORBIS",
  "decimals": 6,
  "supply": 1000000,
  "metadataUri": ""
}
```

Meaning:

- `name`: token display name in metadata.
- `symbol`: token ticker shown by wallets and explorers.
- `decimals`: token decimal precision. With `6`, `1 ORBIS = 1,000,000` raw units.
- `supply`: human token amount minted by the script. With this config, the script mints `1,000,000 ORBIS`.
- `metadataUri`: present in the config, but the current script does not use it. The script uploads fresh metadata to Pinata each time.

With `decimals = 6` and `supply = 1000000`, the raw minted amount is:

```text
1,000,000 * 10^6 = 1,000,000,000,000 raw units
```

## 15. ORBIS Metadata Storage

The script `clone-proxy/scripts/mint-token.ts` does four important things:

1. Reads `token_config.json`.
2. Uploads `clone-proxy/assets/orbis_logo.png` to IPFS through Pinata.
3. Uploads a token metadata JSON object to IPFS through Pinata.
4. Creates the SPL mint and its Metaplex Token Metadata account on Solana.

The metadata JSON uploaded to IPFS contains:

```json
{
  "name": "Orbis",
  "symbol": "ORBIS",
  "image": "<IPFS_IMAGE_URL>"
}
```

The image file itself is stored off-chain on IPFS. The metadata JSON is also stored off-chain on IPFS. The Solana metadata account stores the token name, symbol, and URI pointing to that metadata JSON.

The metadata account is a PDA derived from:

```text
"metadata"
Metaplex Token Metadata Program ID
ORBIS mint address
```

The script prints both:

```text
Mint     : <ORBIS_MINT>
Metadata : <METADATA_PDA>
```

The SPL mint account stores the mint authority, decimals, and token supply. The admin wallet is the mint authority. The freeze authority is set to `null`. The initial supply is minted into the admin wallet's associated token account.

## 16. Mint the ORBIS Token on Mainnet

Confirm `.env` has:

```text
RPC_URL=<YOUR_MAINNET_RPC_URL>
PINATA_JWT=<YOUR_PINATA_JWT>
```

Confirm the admin wallet exists:

```bash
ls wallet/wallet.json
```

Run the mint script:

```bash
./node_modules/.bin/tsx scripts/mint-token.ts --wallet wallet/wallet.json
```

The script outputs:

```text
Logo     : <IPFS_IMAGE_URL>
Metadata : <IPFS_METADATA_JSON_URL>
Mint     : <ORBIS_MINT>
Metadata : <METADATA_PDA>
ATA      : <ADMIN_ORBIS_TOKEN_ACCOUNT>
Minted   : 1,000,000 ORBIS
```

Save the printed `Mint` address as `ORBIS_MINT`:

```bash
export ORBIS_MINT="<PASTE_PRINTED_ORBIS_MINT>"
cat >> .env <<EOF
ORBIS_MINT=$ORBIS_MINT
EOF
```

Important: running `mint-token.ts` again creates a new token mint. It does not mint more supply into the same mint. The admin wallet remains the mint authority for the mint created by the script.

## 17. Create the Mainnet Compression Merkle Tree

The Merkle tree script is:

```text
clone-proxy/test/create-merkle-tree.ts
```

It reads:

```text
RPC_URL
PROGRAM_ID
wallet/wallet.json
```

The script now targets a 10,000,000-node tree with no canopy:

```text
TARGET_TREE_NODES = 10,000,000
MAX_DEPTH = 24
TREE_CAPACITY = 16,777,216 leaves
CANOPY_DEPTH = 0
```

Solana compression trees use a power-of-two depth. `maxDepth = 24` gives `2^24 = 16,777,216` leaves, which covers the 10,000,000-node target. `canopyDepth = 0` means no canopy is stored on-chain.

Run:

```bash
./node_modules/.bin/tsx test/create-merkle-tree.ts
```

The script prints:

```text
Merkle Tree Address: <MERKLE_TREE_ADDRESS>
Authority (Config PDA): <CONFIG_PDA>
Target Nodes: 10,000,000
Tree Capacity: 16,777,216 leaves
Canopy Depth: 0 (no canopy)
```

The script transfers tree authority to the program's `global_config` PDA. That is required because `sync_collection_batch` appends leaves by signing as the config PDA.

Save the printed Merkle tree address:

```bash
export MERKLE_TREE_MINT="<PASTE_PRINTED_MERKLE_TREE_ADDRESS>"
cat >> .env <<EOF
MERKLE_TREE_MINT=$MERKLE_TREE_MINT
EOF
```

The env name is `MERKLE_TREE_MINT` because the existing code expects that variable name, even though the value is a compression Merkle tree address, not an SPL token mint.

## 18. Initialization Fee Values

The program initializer stores these values in `GlobalConfig`:

| Parameter | Human Value | Raw Value with 6 ORBIS Decimals |
|---|---:|---:|
| `write_fee` | `0.0001 ORBIS` | `100` |
| `fee_per_mb` | `0.0001 ORBIS` per MB | `100` |
| `min_fee` | `0.0001 ORBIS` | `100` |
| `registration_fee` | `50 ORBIS` | `50,000,000` |

There is no separate `fee_per_byte` field in `lib.rs`. The on-chain config stores `fee_per_mb`, and `claim_streaming_payment` calculates the data fee from `data_size` in bytes using the configured MB fee and minimum fee.

The initializer script already uses these human values:

```text
write_fee        = 0.0001 ORBIS
fee_per_mb       = 0.0001 ORBIS
min_fee          = 0.0001 ORBIS
registration_fee = 50 ORBIS
```

Because the script fetches the ORBIS mint decimals, the raw values are derived from the deployed ORBIS mint.

## 19. Initialize the Program

Before initializing, `.env` must contain:

```text
RPC_URL=<YOUR_MAINNET_RPC_URL>
PROGRAM_ID=<DEPLOYED_PROGRAM_ID>
ORBIS_MINT=<MAINNET_ORBIS_MINT>
MERKLE_TREE_MINT=<MAINNET_MERKLE_TREE_ADDRESS>
PINATA_JWT=<YOUR_PINATA_JWT>
```

Inspect the initializer:

```bash
sed -n '1,220p' scripts/initialize-program.ts
```

Initialize with the admin wallet as treasury:

```bash
npm run init:contract
```

or:

```bash
yarn init:contract
```

To set a separate treasury:

```bash
npm run init:contract -- --treasury <MAINNET_TREASURY_PUBKEY>
```

or:

```bash
yarn init:contract --treasury <MAINNET_TREASURY_PUBKEY>
```

The script creates the `global_config` PDA and stores:

```text
admin
treasury
orbis_mint
merkle_tree
write_fee
fee_per_mb
min_fee
registration_fee
```

## 20. Final Verification

Verify the program:

```bash
solana program show "$PROGRAM_ID" --url "$RPC_URL"
```

Verify local source IDs:

```bash
solana-keygen pubkey target/deploy/orbis_protocol-keypair.json
grep -n "declare_id" programs/orbis-protocol/src/lib.rs
grep -n "orbis_protocol" Anchor.toml
```

Verify Git state:

```bash
git status --short
git diff -- Anchor.toml programs/orbis-protocol/src/lib.rs programs/idl/orbis_protocol.json test/create-merkle-tree.ts
```

Do not commit:

```text
clone-proxy/.env
clone-proxy/wallet/
target/deploy/orbis_protocol-keypair.json
```

Commit the source/config changes that should be shared:

```text
clone-proxy/Anchor.toml
clone-proxy/programs/orbis-protocol/src/lib.rs
clone-proxy/programs/idl/orbis_protocol.json
clone-proxy/test/create-merkle-tree.ts
clone-proxy/MAINNET_DEPLOYMENT.md
```

## 21. Cleanup

If you do not need the copied wallet for more admin actions, remove it from the repo working tree:

```bash
rm wallet/wallet.json
```

Keep these backed up securely outside Git:

```text
~/.config/solana/orbis-mainnet-deployer.json
clone-proxy/target/deploy/orbis_protocol-keypair.json
```
