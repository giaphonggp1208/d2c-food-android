# Firebase Emulator Suite tests

The environment currently has no Node.js/npm/firebase-tools, so these tests are specified but cannot run here. Run after installing Firebase CLI:

```powershell
firebase emulators:exec --only auth,firestore "npm test -- --runInBand"
```

Required cases mirror `docs/analysis-design.md` §7.4: profile self-read and role escalation rejection; Store owner UID/create and immutable ownerId; order ownership/immutable money/payment pairs; OrderLine ownership; voucher redemption/counter concurrency; all order/shipping/payment transitions; rating ownership/completion; notification behavior remains client-side only.
