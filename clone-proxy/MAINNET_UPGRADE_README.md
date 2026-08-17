# Mainnet Smart Contract Upgrade

Short checklist for upgrading the existing Orbis program on mainnet without wasting SOL on unnecessary program space.

Run from:

```bash
cd /mnt/c/Users/Ramzi/Documents/orbis-social-v3/clone-proxy
```

## 1. Set Mainnet Values

```bash
export RPC_URL="<YOUR_MAINNET_RPC_URL>"
export DEPLOY_WALLET="$PWD/wallet/wallet.json"
export PROGRAM_KEYPAIR="$PWD/target/deploy/orbis_protocol-keypair.json"
export PROGRAM_ID="$(solana-keygen pubkey "$PROGRAM_KEYPAIR")"
export SO="$PWD/target/deploy/orbis_protocol.so"

solana config set --url "$RPC_URL"
solana config set --keypair "$DEPLOY_WALLET"
solana balance
```

`DEPLOY_WALLET` must be the current upgrade authority. `PROGRAM_KEYPAIR` must be the existing program keypair. Do not generate a new one.

## 2. Build

```bash
anchor build
cp target/idl/orbis_protocol.json programs/idl/orbis_protocol.json
```

## 3. Calculate Exact Extra Space

Use the Solana CLI `Data Length` value, not Solscan's raw ProgramData account size.

```bash
NEW_BYTES=$(wc -c < "$SO" | tr -d ' ')
CURRENT_BYTES=$(solana program show "$PROGRAM_ID" --url "$RPC_URL" | sed -n 's/^Data Length: \([0-9][0-9]*\).*/\1/p')

test -n "$CURRENT_BYTES" || { echo "Could not read current on-chain Data Length"; exit 1; }

EXTRA_BYTES=$(( NEW_BYTES > CURRENT_BYTES ? NEW_BYTES - CURRENT_BYTES : 0 ))

printf "Current on-chain capacity: %s bytes\n" "$CURRENT_BYTES"
printf "New .so size:              %s bytes\n" "$NEW_BYTES"
printf "Extra bytes needed:        %s bytes\n" "$EXTRA_BYTES"
```

## 4. Extend Only If Needed

```bash
if [ "$EXTRA_BYTES" -gt 0 ]; then
  solana program extend "$PROGRAM_ID" "$EXTRA_BYTES" \
    --url "$RPC_URL" \
    --keypair "$DEPLOY_WALLET"
else
  echo "No extension needed."
fi
```

This extends by the exact missing byte count. Do not round up unless you intentionally want prepaid spare capacity.

## 5. Upgrade

```bash
solana program deploy "$SO" \
  --program-id "$PROGRAM_KEYPAIR" \
  --upgrade-authority "$DEPLOY_WALLET" \
  --url "$RPC_URL"
```

## 6. Verify

```bash
solana program show "$PROGRAM_ID" --url "$RPC_URL"
```

Check:

- `Program Id` is unchanged.
- `Upgrade Authority` is still your deploy wallet.
- `Data Length` is at least the new `.so` size.
- `Last Deployed In Slot` changed.

## If Upgrade Fails Midway

Close leftover upgrade buffers before retrying:

```bash
solana program show --buffers --url "$RPC_URL" --keypair "$DEPLOY_WALLET"
solana program close --buffers --url "$RPC_URL" --keypair "$DEPLOY_WALLET"
```
