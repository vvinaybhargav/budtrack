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

## The chat

A tab that can read and change everything, given an OpenAI key in Settings.
It calls tools rather than answering from memory, so a figure it quotes is the
figure the screens show, and a change it makes goes through the same code a tap
would — syncing, moving balances, respecting profiles.

```
How much did I spend on groceries this month?
Add 450 for Swiggy from ICICI Joint
The last Swiggy one should be 540, fix it
Set the eating out budget to 4000
Confirm this month's car EMI
```

It can add, edit and delete transactions and commitments, confirm a month's
payment, add accounts, cards and loans, set budgets and categories, and switch
between the personal and joint sides.

**Your figures are sent to OpenAI** to answer — balances, amounts, payees,
account names. Not your PINs, Firebase config or the raw text of any bank
message. The tab does nothing until a key is set.

Two guards worth knowing: an **Undo** button appears after any change the
assistant made, restoring the state from just before it; and the tool loop is
bounded, so a model that keeps calling tools stops instead of spending credit.

## Bank SMS import

Transactions come from your bank's own alerts rather than from anything you
type. Settings → Bank SMS → allow access → turn on, and each debit or credit
message is parsed as it arrives and recorded, whether or not the app is open.
"Import past 60 days" reads the existing inbox once so history isn't lost.

Bank SMS is used in preference to reading a payment app's receipts because it
is the authoritative record: it states which account moved the money, and it
covers card, ATM, NEFT and EMI debits, not only UPI.

**Set each account's last digits** in its editor on the Home screen — the field
matching `A/c XX1234` in the message. Without it, imports fall back to the
default account.

Messages are parsed on the device. Only the parsed amount, payee, reference and
date are stored; nothing raw is uploaded. `READ_SMS`/`RECEIVE_SMS` mean this
build cannot be published on Play, which is fine for a sideloaded app.

The parser skips OTPs, adverts, failed payments and due-date reminders, and
takes the transacted amount rather than the "Avl Bal" that banks append. Every
import keeps its reference, so re-reading the inbox cannot double-count.

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

Both profiles share one document, written as real nested maps and arrays so the
data is browsable in the Firestore console. Sync is last-write-wins. The config
text and the OpenAI key are never uploaded — they stay in the device's own
storage.

### Where the data lives

```
workspaces/household/budtrack/state
```

This deliberately sits under `workspaces/household/**`, the path the existing
[vvinaybhargav/fintrack](https://github.com/vvinaybhargav/fintrack) household
app already authorises, so its published `firestore.rules` cover this app with
no change. The `budtrack` section keeps it clear of that app's `entries`,
`accounts`, `loans`, `goals`, `bills` and `meta` collections — the two apps
share a project and a rule, not documents.

If a write is rejected with a missing-permissions error, the path is almost
always the reason: rules scoped to one workspace deny everything outside it.

### Sign-in

The app signs in anonymously when it can, so rules that require an
authenticated user work. It is best-effort — if the Anonymous provider is
disabled, sync carries on unauthenticated rather than failing, because the
household rules authorise by path rather than by user. Settings says which
happened.

To require auth instead, enable it in **Firebase console → Authentication →
Sign-in method → Anonymous**, then use the UID-restricted rules below.

### Security rules

The household workspace is currently open by path — anyone who knows the
project ID and the path can read it. That is the existing app's tradeoff, and
this app inherits it:

```
match /workspaces/household/{document=**} {
  allow read, write: if true;
}
```

To tighten it, publish [`firestore.rules`](firestore.rules) from this repo. It
carries the steps in its header: enable anonymous sign-in, take the **This
device's ID** value Settings shows on each phone, and list them in a `uids`
array on `workspaces/household/members`.

Anonymous IDs are per-install: reinstalling or clearing app data mints a new
one, which then has to be added to `uids` again. Note this rule also governs
the other household app, so roll it out to both phones together.
