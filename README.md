# D2C Food — Nhịp 1 (Xương sống)

Android Kotlin + Jetpack Compose, Firebase Spark (Auth + Firestore, no Cloud Functions, no billing).

## Layout

- `app/` — `:app` module
  - `domain/` — models + `PricingCalculator` (pure, unit-tested)
  - `data/firebase/` — `FirebaseAuthRepository` (Email/Password, bootstraps `users/{uid}` CUSTOMER), `FirestoreStoreRepository` (realtime Store/Menu reads)
  - `data/demo/` — in-memory repositories used when no Firebase project is configured
  - `ui`/`MainActivity.kt` — role-based navigation: Customer / Store Owner (STORE_OWNER is Admin-seeded; the client cannot self-elevate — enforced by rules)
- `firestore.rules` — full §7.3 ruleset (users/stores/menu/vouchers+redemptions/orders/lines/ratings, 3 state machines, money lock, P0-E payment pairs)
- `firebase-tests/` — §7.4 rules tests (mocha + `@firebase/rules-unit-testing` v3, runs against the local Firestore emulator)
- `firebase.json` / `firestore.indexes.json` — emulator + rules deployment config

## Build (Android)

Requires JDK 17 (e.g. `D:\Android\jdk-17`) and `local.properties` → `sdk.dir=D:/Android/Sdk` (forward slashes — Windows SDK path validation rejects backslash-doubled entries):

```powershell
$env:JAVA_HOME='D:\Android\jdk-17'; $env:ANDROID_HOME='D:\Android\Sdk'
gradle :app:testDebugUnitTest :app:assembleDebug   # or Android Studio: open project, Run
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Firebase Emulator Suite tests (§7.4)

Uses portable Node in `tools/node-v20.18.0-win-x64/` and `firebase-tools` (needs JDK 21 — available at `~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`). Two terminals:

```powershell
# terminal 1 — emulator (JDK 21 on PATH/JAVA_HOME)
$env:JAVA_HOME='C:\Users\ADMIN\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
D:\Android-Final\tools\node-v20.18.0-win-x64\node.exe ... # npx firebase-tools emulators:start --only firestore --project demo-d2cfood --config firebase.json

# terminal 2 — tests (emulator must be running)
cd D:\Android-Final\firebase-tests
D:\Android-Final\tools\node-v20.18.0-win-x64\npx.cmd mocha rules.test.js
```

## Known engine quirk (verified in this environment)

`get()/getAfter()` in rules v2 on the current emulator return objects exposing **only `.data` and `.id`** — the `.exists` property throws `evaluation error` (and the design doc §7.3 writes `.exists`). This project's `firestore.rules` uses the equivalent `.data != null`.

## Blockers (need user/team input)

1. **No `google-services.json`** — the app compiles and the demo repositories drive the UI, but live Auth/Firestore requires a Firebase project. Create a Spark project, enable Email/Password, place `google-services.json` in `app/`, then the Firebase repositories activate automatically (see `MainActivity.useFirebase`).
2. §7.4.5 (local notifications foreground/background) is Nhịp 4 — not in Nhịp 1 scope.
3. Voucher concurrency "5 parallel customers" stress test (§7.4.3 last item) — the rules enforce the `usedCount == resource.usedCount + 1 && <= maxUses` delta; a true 5-way race check would need the `firestore.runTransaction` retry loop in the app (Nhịp 2).
