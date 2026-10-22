# Shop Finance Manager

Arabic-first Android app (Kotlin, Jetpack Compose, Firebase) for small shop owners to track cash flow, customer debts, expenses, capital, liabilities and due dates.

## Building

The debug APK is built by GitHub Actions (`.github/workflows/android.yml`) on every pull request and push to `main`; download it from the run's **shop-finance-debug-apk** artifact.

Locally, with JDK 17 and the Android SDK installed: `./gradlew test assembleDebug`.

Firebase config (`app/google-services.json`) is never committed. CI writes it from the `GOOGLE_SERVICES_JSON` repository secret when that secret exists; without it the app builds without Firebase.

Debug APKs from CI are signed with a shared test key so each new build installs as an update over the last one.
The key is never committed (the repository is public): CI writes `app/ci-debug.keystore` from the
`DEBUG_KEYSTORE_BASE64` secret (the keystore file, base64-encoded; alias `androiddebugkey`, passwords `android`).
Without the secret, every run signs with a new random key, and Android refuses to install that build over an
earlier one until the old app is uninstalled.

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
"access to the entries was refused", and after the debts feature is merged, publish again or the debts screen
shows "access to the debts was refused".

## What the app does so far

- Stage 2: sign in or create an account, then create the shop.
- Stage 3: the cash ledger. Record sales, expenses (with a category), and money the owner puts in or takes out.
  The dashboard shows cash on hand and today's and this month's sales and expenses; the entries list shows
  everything. Entries are never edited or deleted: a mistake is fixed by cancelling (voiding) the entry with a
  reason, and it stays in the list, struck through. Entries are written to the phone first and synced when online;
  unsynced ones are marked.

- Debts: people who owe the shop money and what they repay. The dashboard's Debts button opens the list of
  people, with the total still owed per currency, a name search and an Open / Paid / All filter, the oldest unpaid
  debt first. A person's page shows each debt (the day the money was taken, the amount, paid so far, remaining,
  open or paid) with its payments. A debt is in USD or LBP and can be dated back to 2000; a payment is refused when
  it is zero, more than the remaining, or dated before the debt. Paid debts stay in the history. Deleting a debt
  or a payment asks first and only marks it deleted. Debts are a separate book: they never change cash on hand.

Entries are stored under `shops/{shopId}/entries/{entryId}` with the amount in cents (`amountMinor`), and every
total is computed from the entries. Debts are stored under `shops/{shopId}/debtors`, `debts` and `debtPayments`,
again in minor units (cents or lira) with the day as `YYYY-MM-DD` text; what remains on a debt is always computed
from its payments, never stored. Suppliers and due dates come in later stages.

## Currency

The cash ledger handles USD only: shops and entries are in USD and the rules reject anything else. Debts are in
USD or LBP. Dollars and lira are never added together: every total is shown once per currency.
