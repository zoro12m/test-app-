# Shop Finance Manager

Arabic-first Android app (Kotlin, Jetpack Compose, Firebase) for small shop owners to track cash flow, customer debts, expenses, capital, liabilities and due dates.

## Building

The debug APK is built by GitHub Actions (`.github/workflows/android.yml`) on every pull request and push to `main`; download it from the run's **shop-finance-debug-apk** artifact.

Locally, with JDK 17 and the Android SDK installed: `./gradlew test assembleDebug`.

Firebase config (`app/google-services.json`) is never committed. CI writes it from the `GOOGLE_SERVICES_JSON` repository secret when that secret exists; without it the app builds without Firebase.

## Firestore security rules

The rules live in `firestore.rules` and are tested against the Firestore emulator (`firestore-tests/`, run in CI by
`.github/workflows/firestore-rules.yml`). Locally, with Java 21 and Node 22:

```
cd firestore-tests
npm ci
npm run test:emulator
```

Rules are not deployed by CI. After a change to `firestore.rules` is merged, publish it in the Firebase console
(Firestore Database > Rules: paste the file, then Publish) or with `firebase deploy --only firestore:rules`.
Until the rules are published the app cannot read or create shops and shows an "access refused" screen.
Each stage that adds data adds rules too: after Stage 3 is merged, publish again or the dashboard shows
"access to the entries was refused".

## What the app does so far

- Stage 2: sign in or create an account, then create the shop.
- Stage 3: the cash ledger. Record sales, expenses (with a category), and money the owner puts in or takes out.
  The dashboard shows cash on hand and today's and this month's sales and expenses; the entries list shows
  everything. Entries are never edited or deleted: a mistake is fixed by cancelling (voiding) the entry with a
  reason, and it stays in the list, struck through. Entries are written to the phone first and synced when online;
  unsynced ones are marked.

Entries are stored under `shops/{shopId}/entries/{entryId}` with the amount in cents (`amountMinor`), and every
total is computed from the entries. Customer debts, suppliers and due dates come in later stages.

## Currency

The app handles USD only: shops and entries are in USD and the rules reject anything else. The domain module
still supports LBP for a later stage.
