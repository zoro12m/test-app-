# Shop Finance Manager

Arabic-first Android app (Kotlin, Jetpack Compose, Firebase) for small shop owners to track cash flow, customer debts, expenses, capital, liabilities and due dates.

## Building

The debug APK is built by GitHub Actions (`.github/workflows/android.yml`) on every pull request and push to `main`; download it from the run's **shop-finance-debug-apk** artifact.

Locally, with JDK 17 and the Android SDK installed: `./gradlew test assembleDebug`.

Firebase config (`app/google-services.json`) is never committed. CI writes it from the `GOOGLE_SERVICES_JSON` repository secret when that secret exists; without it the app builds without Firebase.
