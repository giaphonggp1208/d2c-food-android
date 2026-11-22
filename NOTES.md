# NOTES

- `app/google-services.json` is committed (Firebase Spark project, shared with the team
  via Console membership — it is not a secret; credentials live in the Console).
  `MainActivity.useFirebase` switches to the live Firebase repositories automatically.
- §7.4.5 local-notification tests are Nhịp 4 — not in Nhịp 1 scope.
- Voucher 5-way concurrency stress needs the client-side `runTransaction` retry loop (Nhịp 2).
