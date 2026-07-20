# Orbis Protocol Mainnet Upgrade Guide

This guide covers **upgrading an already-deployed** Orbis Anchor program on Solana mainnet: build the new program and deploy it in place over the existing program ID.

It does **not** create a new program ID, ORBIS token mint, or compression Merkle tree, and it does **not** run initialization. Those all exist from the original deployment and are reused unchanged. If you have never deployed the program before, use a fresh-deployment procedure instead — do not improvise a partial one.

Program source:

```text
clone-proxy/programs/orbis-protocol/src/lib.rs
```

Run all commands from:

```bash
cd /mnt/c/Users/Ramzi/Documents/orbis-social-v3/clone-proxy
```

## Prerequisites (already in place)

This guide assumes the first deployment succeeded and you still have:

- Toolchain installed: Rust, Solana CLI, Anchor `1.0.0` (via AVM), Node, Yarn/npm. Versions are pinned in `Cargo.toml` and `Anchor.toml`.
- The **program keypair** at `clone-proxy/target/deploy/orbis_protocol-keypair.json`. Its public key **is** the on-chain program ID. Restore it from your backup if it is not present. Do not generate a new one — a new keypair would deploy a different program instead of upgrading this one.
- The **upgrade-authority wallet** (the admin/deploy wallet that originally deployed the program) at `clone-proxy/wallet/wallet.json`, funded with enough mainnet SOL for the upgrade.
- A `.env` containing `RPC_URL`, `PROGRAM_ID`, `ORBIS_MINT`, and `MERKLE_TREE_MINT` from the original deployment.
- The existing on-chain `global_config`, ORBIS mint, and Merkle tree — reused as-is.

## 1. Get the new code

Check out the branch/commit with your program changes and confirm the working tree:

```bash
cd /mnt/c/Users/Ramzi/Documents/orbis-social-v3/clone-proxy
git status --short
```

## 2. Confirm the program ID (do not change it)

An upgrade must target the same program ID. Confirm all four agree:

```bash
solana-keygen pubkey target/deploy/orbis_protocol-keypair.json
grep -n "declare_id" programs/orbis-protocol/src/lib.rs
grep -n "orbis_protocol" Anchor.toml
grep -n "PROGRAM_ID" .env
```

All must print the same address. Do **not** run `solana-keygen new` for the program and do **not** run `anchor keys sync` — `lib.rs` and `Anchor.toml` already hold the correct ID from the first deploy.

## 3. Point Solana at mainnet and the upgrade authority

```bash
export DEPLOY_WALLET="$PWD/wallet/wallet.json"
export RPC_URL="<YOUR_MAINNET_RPC_URL>"
solana config set --url "$RPC_URL"
solana config set --keypair "$DEPLOY_WALLET"
solana config get
solana balance
```

The configured wallet must be the program's current **upgrade authority**, or the deploy is rejected.

## 4. Build

```bash
anchor build
```

Produces:

```text
target/deploy/orbis_protocol.so
target/idl/orbis_protocol.json
```

## 5. Sync the IDL used by the Node worker

The Node service calls the program through the tracked IDL, so copy the freshly built one over it:

```bash
cp target/idl/orbis_protocol.json programs/idl/orbis_protocol.json
git diff -- programs/idl/orbis_protocol.json programs/orbis-protocol/src/lib.rs
```

## 6. Deploy the upgrade

Because the program already exists and your wallet is its upgrade authority, `anchor deploy` performs an in-place upgrade at the same program ID:

```bash
anchor deploy \
  --provider.cluster "$RPC_URL" \
  --provider.wallet "$DEPLOY_WALLET"
```

Equivalent lower-level command (more robust for large programs):

```bash
solana program deploy target/deploy/orbis_protocol.so \
  --program-id target/deploy/orbis_protocol-keypair.json \
  --upgrade-authority "$DEPLOY_WALLET" \
  --url "$RPC_URL"
```

If the new build is larger than the deployed one, extend the program account first:

```bash
solana program extend "$PROGRAM_ID" <ADDITIONAL_BYTES> --url "$RPC_URL"
```

A failed or interrupted upgrade can leave a buffer account holding your SOL. Reclaim it, then retry:

```bash
solana program show --buffers --url "$RPC_URL"
solana program close --buffers --url "$RPC_URL"
```

## 7. Verify

```bash
solana program show "$PROGRAM_ID" --url "$RPC_URL"
```

Confirm:

- the program is Executable;
- the Upgrade Authority is still your admin wallet;
- the Last Deployed Slot and Data Length reflect the new build.

## 8. Account compatibility (no re-initialization)

An upgrade does not create or reset any accounts. The existing `global_config`, `CloneInfo` PDAs, streaming escrows, ORBIS mint, and Merkle tree keep working with the new code.

- Adding, removing, or changing **instructions and events** (for example replacing `sync_collection_batch` with `sync_index_manifest`) is safe and needs no migration.
- Changing the on-chain layout of an `#[account]` struct (`GlobalConfig`, `CloneInfo`, `StreamingEscrow`) is **not** covered by a plain upgrade and would require a data migration. This upgrade does not change those layouts.

To change only fee values on the existing config (optional, independent of a code upgrade), use the `update-config-fees` instruction/script rather than re-initializing.

## 9. Refresh the Node workers

Each running clone-proxy loads `programs/idl/orbis_protocol.json` at startup. After the upgrade, ship the updated code and IDL to every clone and restart it so it can encode and decode the new instructions and events.

## 10. Git

Commit the shared source and config; never commit secrets.

Commit:

```text
clone-proxy/programs/orbis-protocol/src/lib.rs
clone-proxy/Anchor.toml
clone-proxy/programs/idl/orbis_protocol.json
clone-proxy/MAINNET_DEPLOYMENT.md
```

Do not commit:

```text
clone-proxy/.env
clone-proxy/wallet/
clone-proxy/target/deploy/orbis_protocol-keypair.json
```

Keep backed up securely, outside Git:

```text
~/.config/solana/<your-deployer>.json
clone-proxy/target/deploy/orbis_protocol-keypair.json
```
