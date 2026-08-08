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

## Firestore sync

Sync is configured inside the app, not in this repo — there is deliberately no
`google-services.json` and no `google-services` Gradle plugin. Firebase is
initialised at runtime from the config you paste into **Settings → Sync**, as
six comma-separated values in this order:

```
apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId
```

A block copied straight from the Firebase console (`apiKey: "…",` and so on) is
also accepted — the key names and quotes are stripped. Tap **Connect**; the tag
turns *Live* once the listener is attached.

Both profiles share one document, `fintrack/household`, written as real nested
maps and arrays so the data is browsable in the Firestore console. Sync is
last-write-wins. The config text and the OpenAI key are never uploaded — they
stay in the device's own storage.

### Security rules

The app does not sign in to Firebase, so **the default test rules leave the
household document readable and writable by anyone who has the project ID.**
That is real financial data. Before putting anything genuine in it, either turn
on Firebase Auth and require it in the rules, or restrict access another way.
