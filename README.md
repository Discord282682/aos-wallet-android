# AOS Wallet (Android)

A self-custodial Android wallet for **AOS (Austlander Ounce Stralen)**, talking directly to your
deployed `feeless-coin` gateway at `https://feeless-coin.vercel.app` (changeable in-app under
Settings). It implements the real v2 wire protocol from `aos/protocol.py` / `api/index.py` —
Ed25519 keys, `Au5` addresses, SHA-256 proof-of-work, and the `/earn/claim` mining endpoint —
not a mock.

## Why there's no .apk in this download

This project was built in a sandboxed environment that can reach PyPI/npm/GitHub but is blocked
from `dl.google.com`, so the Android SDK itself couldn't be downloaded here to compile a binary.
What you have instead is the **complete, ready-to-build Android Studio project** — every Kotlin
file, the Gradle config, resources, manifest. Building the actual `.apk` from this is one button
once it's open in Android Studio (steps below), and that step lets you install it straight to a
phone over USB too.

## Build the APK

1. Install **Android Studio** (free, from developer.android.com) if you don't have it.
2. Open this folder (`aos-wallet-android/`) in Android Studio: *File → Open*.
3. Let it sync Gradle on first open (downloads the Android SDK/build tools automatically if you
   don't already have them — this is the step that needed `dl.google.com`, which your own
   machine can reach).
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. When it finishes, click the
   "locate" link in the notification, or find it at
   `app/build/outputs/apk/debug/app-debug.apk`.
5. To install straight to a phone: plug it in with USB debugging enabled, then the green ▶ Run
   button in Android Studio installs and launches it directly. Or copy the `.apk` to the phone
   and open it (you'll need to allow "install unknown apps" for whatever app you use to open it).

No signing setup is required for a debug APK — fine for your own phone. If you ever want to
distribute it more widely, Android Studio's *Build → Generate Signed Bundle / APK* walks through
creating a release key.

## Matching your server configuration

The app reads `earn_reward` and `earn_cooldown_seconds` live from `GET /` on every mining screen
refresh — it does **not** hardcode "5 AOS every 24h". To get that exact behavior, set these on
your Vercel project (Settings → Environment Variables), then redeploy:

| Variable | Value for "5 AOS every 24h" |
|---|---|
| `EARN_REWARD` | `5.00000000` |
| `EARN_COOLDOWN_SECONDS` | `86400` |

Until you set those, the server's own defaults apply (1 AOS / 60s cooldown — convenient for
testing the whole flow quickly before you widen the cooldown to a real day).

## What the mining & "node" features actually do

- **Mining** is real proof-of-work against your gateway's `pow_prefix` (`"0000"` by default,
  ~65,536 average SHA-256 attempts — milliseconds on a phone). Tapping **Start mining session**
  schedules a WorkManager background job for whenever your wallet's cooldown next clears; that
  job runs even if you fully close the app, does the PoW, signs the claim with your key, and
  submits `POST /earn/claim`. You'll get a notification and see the new balance the next time you
  open the app — then tap again to queue the next cycle, same as you described.
- **Difficulty** in this protocol is a single fixed constant (`PREFIX = '0000'` in
  `aos/protocol.py`) — it isn't currently retargeted up/down like Bitcoin's, and the claim amount
  must match `earn_reward` *exactly* or the server rejects it (see `validate_earn_claim`). So
  reward doesn't dynamically scale with difficulty yet. If you want real difficulty retargeting
  later, that's a server-side change (tracking recent claim-rate and adjusting `PREFIX`/reward
  together) — happy to help with that next if you want it.
- **"Temporary node"** is an honest simplification: `feeless-coin` is one centralized Postgres
  ledger (its own README says so explicitly) — there's no peer-to-peer network or pending-
  transaction mempool for a phone to actually help validate. With the toggle on, the app instead
  runs a lightweight periodic background sync (every 15 minutes, Android's floor for periodic
  background work) that keeps the transaction/supply cache warm and shows a "node uptime" stat in
  Settings. It's labeled accordingly in the UI so it doesn't overclaim.
- **"Blocks"** aren't a thing in this protocol either — it's a flat, confirmed transaction ledger,
  not a chain of blocks. The Explorer tab is named "Transactions" and says so up front rather than
  inventing a fake block view.

## Security notes

- The private key (raw 32-byte Ed25519 seed, hex) is stored in **EncryptedSharedPreferences**,
  AES-256-GCM, with the encryption key held in the Android Keystore. It's excluded from Android's
  auto-backup/device-transfer so a cloud backup never carries an unusable encrypted blob to a
  device with a different Keystore key.
- The private key is only ever shown under **Settings → Wallet options**, behind an explicit
  "Reveal" tap, exactly as you asked for.
- Import accepts the same 64-hex-character raw private key format the server's own
  `test_tx.py wallet` command writes — a key made by one works in the other.
- This is solid everyday practice, not an audited hardware-wallet-grade implementation. The
  server's own docs are explicit that AOS is a non-monetary prototype currency — treat it that
  way, not as a place to hold anything you can't afford to lose.

## Project layout

```
app/src/main/java/com/austland/aos/wallet/
  crypto/Crypto.kt         Ed25519 keys, Au5 address derivation, SHA-256 proof-of-work
  data/Models.kt           Response data classes
  data/AosApi.kt           HttpURLConnection + org.json client for every gateway endpoint
  data/WalletStore.kt      Encrypted key storage + app settings
  data/TransactionBuilder.kt  Builds+signs transfer / earn-claim payloads
  work/MiningWorker.kt     Background PoW + /earn/claim (survives app close)
  work/NodeSyncWorker.kt   Background "node" ledger sync
  work/WorkScheduler.kt    Schedules/cancels both
  ui/...                   Jetpack Compose screens: onboarding, wallet, send, mine, explorer, settings
```

## Known rough edges / good next steps

- No QR scanning/generation for addresses yet (copy/paste only) — straightforward to add with
  CameraX + ML Kit or ZXing if you want it.
- No multi-wallet support — one wallet per install, matching what you asked for.
- This hasn't been run through an actual compile in this sandbox (no Android SDK access here), so
  treat the first Android Studio sync/build as the real first compile. If it surfaces an error,
  paste it back to me — Compose/Gradle errors are almost always a one-line fix (a missing import,
  a version bump) rather than a structural problem.
