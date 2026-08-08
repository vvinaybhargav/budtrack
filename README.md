# FinTrack (Android)

Kotlin + Jetpack Compose implementation of the **FinTrack Export** Claude Design mockup —
a household finance tracker for two profiles (Me / Wife) with joint and personal buckets.

## Screens

| Screen | What it does |
| --- | --- |
| Lock | Profile picker → 4-digit PIN keypad (default PIN `1234` for both profiles) |
| Home | Quick-add bar, total balance, accounts, month stats, category budgets, loans, credit cards, monthly commitments |
| Transactions | Joint/Personal toggle, search, category chips, edit/delete entries |
| Add | Smart Add chat parser, plus forms for expense / bill / EMI-loan / investment / one-time / bank account / credit card |
| Settings | Switch profile, change PIN, manage categories, default account, Firebase + OpenAI keys |

State persists to `SharedPreferences` as JSON (`kotlinx.serialization`); it seeds from the
mockup's sample data on first launch.

## Building

No Android SDK is required locally — pushing to `main` builds a signed debug APK via
GitHub Actions and publishes it to the `latest` release. To build locally, open the
project in Android Studio, or:

```bash
gradle wrapper --gradle-version 8.7 && ./gradlew assembleDebug
```

The app is signed with the checked-in `app/keystore/fintrack.jks` (password `fintrack`)
so every build — local or CI — installs over the previous one without uninstalling.
