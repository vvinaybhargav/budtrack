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
comma-separated values:

```
apiKey, projectId, storageBucket, messagingSenderId, appId
```

Values are recognised by shape, so the order doesn't matter and extras are
tolerated: `authDomain` may be included but is ignored, since it exists only for
the Auth web SDK and Android's `FirebaseOptions` has no field for it. Only
apiKey, projectId and appId are genuinely required — storageBucket and
messagingSenderId are derived from them when absent.

A block copied straight from the Firebase console (`apiKey: "…",` and so on) is
also accepted — the key names and quotes are stripped. Tap **Connect**; the tag
turns *Live* once the listener is attached.

Both profiles share one document, `fintrack/household`, written as real nested
maps and arrays so the data is browsable in the Firestore console. Sync is
last-write-wins. The config text and the OpenAI key are never uploaded — they
stay in the device's own storage.

### Sign-in

The app signs in anonymously before touching Firestore, so the rules can require
an authenticated user. Enable it once per project:

**Firebase console → Authentication → Sign-in method → Anonymous → Enable.**

Without this, sign-in fails and Settings says so.

### Security rules

This document holds salaries, balances and loans, so don't leave it open. The
weak version — any signed-in user, and *anyone* can create an anonymous account
in your project — is:

```
match /fintrack/{doc} {
  allow read, write: if request.auth != null;   // not enough on its own
}
```

Better: allow only the devices you name. Settings shows **This device's ID**
once connected; create a `fintrack/members` document with a `uids` array holding
each phone's ID, then:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /fintrack/members {
      allow read: if request.auth != null;
      allow write: if false;                    // edit in the console only
    }
    match /fintrack/household {
      allow read, write: if request.auth != null
        && request.auth.uid in get(/databases/$(database)/documents/fintrack/members).data.uids;
    }
  }
}
```

Anonymous IDs are per-install: reinstalling or clearing app data mints a new one,
which then has to be added to `uids` again.
