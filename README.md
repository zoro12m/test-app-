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

## Currency

Stage 2 handles USD only: new shops are created in USD and the rules reject anything else. The domain module
still supports LBP for a later stage.
