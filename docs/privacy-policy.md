---
layout: default
title: Privacy Policy — Statement Sense
---

# Privacy Policy — Statement Sense

**Effective date:** 8 September 2026
**Last updated:** 8 September 2026
**Application:** Statement Sense (Android)
**Package name:** `com.madtitan94.transactionsparser`
**Developer:** Sandeep Chorge
**Contact:** statementsensesupport@gmail.com

---

## 1. The short version

Statement Sense reads UPI statement PDFs and turns them into categorised spending. **It does this
entirely on your phone.** There is no Statement Sense server, no account on our side, and no copy of
your statements or your transactions anywhere but your own device.

Two things do leave your device, and only two:

1. **Anonymous usage events and crash reports**, sent to Google's Firebase, so we can tell whether the
   app is working for people. These never contain a payee name, an amount, a date, a reference
   number, or a file name.
2. **Your Google sign-in**, which happens between you and Google directly. We receive your name,
   email address, profile photo link and Google account id from Google, and store them *on your
   phone* — we do not transmit them anywhere.

Your financial data — every transaction, payee, category and statement — stays in a database on the
device and is never uploaded by this app.

| | |
|---|---|
| Do we run a server that holds your data? | **No.** There is none |
| Are your statements or transactions uploaded? | **No** |
| Are your statement PDFs kept? | **No.** The temporary copy is deleted as soon as parsing finishes |
| Is anything sold or shared with advertisers? | **No.** Never, under any circumstances |
| Is there tracking for advertising? | **No.** No ad SDK, no ad identifier, no ad network |
| What does leave the device? | Anonymous usage events and crash reports only |

---

## 2. Who this policy covers

This policy applies to the Statement Sense Android application distributed through Google Play under
the package name `com.madtitan94.transactionsparser`. It does not cover Google's own services, your
bank's or payment app's services, or any other app you may open an exported file with. Those are
governed by their own policies.

---

## 3. What the app actually does with a statement

Understanding this makes the rest of the policy easier to trust, because it explains *why* so little
data can leave.

1. You pick a PhonePe or Google Pay statement PDF using Android's own file picker. The app never
   browses your storage; it receives only the single file you chose.
2. The app copies that file into its own private temporary storage so it can be read.
3. The text is extracted **on the device** using a bundled PDF library. Nothing is sent to a service
   to be read, parsed, transcribed, or interpreted. There is no AI or cloud parsing in this version.
4. The extracted transactions are written to a database inside the app's private storage.
5. **The temporary copy of the PDF is deleted immediately**, in a `finally` block, before you are
   even told the import succeeded. The original file in your Downloads folder, or wherever you keep
   it, is untouched — the app never modifies or deletes your own files.

From that point on, everything you see — dashboards, the payee directory, search, totals — is
computed on the device from that local database.

---

## 4. Information we collect

### 4.1 Information you provide by signing in

Statement Sense requires a Google sign-in, because your data is scoped to a specific account so that
two people sharing a phone do not see each other's spending.

Signing in uses Android's Credential Manager. **You authenticate with Google directly; the app never
sees, handles, or asks for your password.** After you approve it, Google returns to the app:

| Item | Why it is needed | Where it is kept |
|---|---|---|
| Google account id (the permanent `sub` identifier) | The key every row of your data is scoped to | On your device only |
| Email address | Shown on your profile screen; used to derive an anonymous analytics id | On your device only |
| Display name | Shown on your profile screen | On your device only |
| Profile photo link | Shown on your profile screen | On your device only |

All four are stored in the app's private storage on the device. **None of them is transmitted to us
or to anyone else in a form that identifies you.** See §4.3 for the one exception, which is a
one-way hash.

We request no additional Google scopes. The app cannot read your Gmail, Drive, Contacts, Calendar,
or anything else in your Google account.

### 4.2 Financial information, which stays on your device

The app creates and stores the following on your device, and only on your device:

| Stored | Contains |
|---|---|
| Transactions | Payee name as printed on the statement, amount, date and time, direction, transaction/UTR reference, and flags for duplicates and exclusions |
| Payees | The name *you* gave a payee, and the category you put them in |
| Payee identifiers | The raw statement names that all refer to the same payee |
| Statements/sessions | The file name you imported, its source app, the period it covers, and its status |
| Import log | Every import attempt, including failures and the reason |
| Preferences | Your theme choice, your dashboard arrangement, and your signed-in session |

This is the sensitive material, and it is exactly the material that never leaves. It is held in the
app's private application directory, which on Android is not readable by other apps.

### 4.3 Usage analytics

The app sends anonymous usage events to **Google Analytics for Firebase**, so we can tell whether
features work in the real world — for example, whether statement imports are succeeding, or whether
a particular bank format is failing for everyone.

**Every event carries these nine parameters, and nothing else:**

| Parameter | Example value |
|---|---|
| `accountHash` | A salted SHA-256 hash of your Google account id |
| `emailHash` | A salted SHA-256 hash of your email address |
| `appName` | `Statement Sense` |
| `appVersion` | `1.0.42` |
| `device` | The device's internal codename |
| `manufacturer` | `Google` |
| `model` | `Pixel 8` |
| `osVersion` | `35` |
| `osRelease` | `15` |

**About the two hashes.** They exist so that we can count *people* rather than *launches* — so that
"200 imports" can be told apart from "one person importing 200 times". They are one-way: a hash
cannot be turned back into your email or your account id. They are also salted, meaning a secret
value is mixed in before hashing, which is what prevents someone with a leaked copy of the analytics
data from checking a guessed email address against it. If you are not signed in, these two
parameters are **omitted entirely** rather than sent empty.

**The complete list of events sent:**

| Event | Sent when | What it carries beyond the nine defaults |
|---|---|---|
| `app_started` | The app is launched | Nothing |
| `statement_imported` | A statement import finishes | Which app the statement came from (PhonePe / Google Pay), whether it succeeded, how many transactions were found, and — on failure — a fixed error category |
| `mapping_completed` | You finish naming the payees in a statement | How many payees were mapped |
| `mapping_cancelled` | A statement is discarded before mapping finishes | How many transactions were discarded |
| `default_dashboard_changed` | You star a different dashboard | The *kind* of dashboard, never the id of one you built |
| `data_exported` | An export or backup is successfully written | Which format |
| `logged_in` | Sign-in succeeds | Nothing |
| `logged_out` | You sign out | Nothing |

**What these events deliberately do not contain:** no payee names, no aliases, no category names, no
amounts, no dates from your statements, no reference numbers, no file names, no free-text field of
any kind. The event definitions are a closed, fixed set in the source code — they cannot carry
arbitrary text — and this is enforced by an automated test that fails the build if a free-text field
is ever added to one.

**What Google collects on top of this.** Google Analytics for Firebase also collects some
information automatically, which we do not control the content of. This typically includes a
randomly generated app instance identifier, the app version and first-open time, session start and
duration, device model and operating system, language and country, and an **approximate location
(country, and sometimes city) derived from your IP address**. Google's handling of this is governed
by [Google's Privacy Policy](https://policies.google.com/privacy) and the
[Firebase data disclosures](https://firebase.google.com/support/privacy). IP addresses are
[not logged or stored by Google Analytics for Firebase](https://support.google.com/firebase/answer/6318039)
after being used to derive coarse location.

### 4.4 Crash reports

The app uses **Firebase Crashlytics**. When the app crashes, a report is sent containing the stack
trace, the app version, the device model and operating system version, the state of the device at
the time (memory, storage, orientation, whether it was rooted), and the `accountHash` described
above — so a crash can be tied to the same anonymous identity as the events that preceded it.

Crash reports are generated from program state, not from your data. They do not include the contents
of your database, your statements, or your screen.

### 4.5 What is never collected, in any form

- Your bank account number, card number, UPI ID, or any credential
- Your password (the app never has the opportunity — Google handles authentication)
- Your statement PDFs, or any part of their text
- Any transaction, payee name, alias, amount, date, or reference number
- Your precise location, contacts, photos, microphone, camera, call log, or SMS. The app requests
  **one Android permission in total: `INTERNET`**, and it needs that only for sign-in and for the
  analytics and crash reporting described above. Statement parsing itself works with no network at
  all.
- Advertising identifiers. There is no advertising SDK in the app.

---

## 5. Every way data leaves your device — the complete list

There are exactly three, and you can verify all three in the public source code.

1. **Sign-in.** A conversation between your device and Google. We are a recipient of the result, not
   a party to your credentials.
2. **Analytics events**, to Google Analytics for Firebase, as itemised in §4.3.
3. **Crash reports**, to Firebase Crashlytics, as described in §4.4.

There is no fourth. There is no upload of statements, no sync, no cloud database, and no
Statement Sense backend to send anything to.

---

## 6. Files you create yourself

The app can write two kinds of file, both only when you ask:

- **A CSV export** of your transactions.
- **A JSON backup** of your whole dataset, for restoring later or moving to a new phone.

Both are written **to a location you choose** in Android's file picker. Once written, that file is an
ordinary file on your device or in whatever cloud storage folder you pointed the picker at, and it
contains your real financial data in readable form. It is outside the app's control from that moment
on. If you save a backup into a synced folder — Google Drive, Dropbox, or similar — you are choosing
to upload your financial data to that service, under that service's privacy policy rather than this
one. Treat these files the way you would treat the original statement.

---

## 7. Android's own backup

Android provides a system feature called Auto Backup, which can copy an app's data to **your own
Google Drive**, where it does not count against your Drive storage quota. Statement Sense currently
leaves this feature at Android's default setting, which means **the app's database may be included
in your device's Google Drive backup.**

To be precise about what that means: this is a transfer between your device and *your* Google
account, performed by the Android operating system, not by this app. We have no access to it. On
Android 9 and above it is encrypted with a key derived from your device's PIN, pattern or password,
which Google states it does not hold. You can turn it off for all apps, or for this app, in
**Settings → Google → Backup** on your device.

We consider this worth stating plainly because the rest of this policy promises that your financial
data stays on your device, and this is the one system-level exception to that.

---

## 8. Third-party services we rely on

| Service | Purpose | Their policy |
|---|---|---|
| Google Sign-In / Credential Manager | Authentication | [policies.google.com/privacy](https://policies.google.com/privacy) |
| Google Analytics for Firebase | Anonymous usage measurement | [firebase.google.com/support/privacy](https://firebase.google.com/support/privacy) |
| Firebase Crashlytics | Crash reporting | [firebase.google.com/support/privacy](https://firebase.google.com/support/privacy) |
| Google Play | Distribution | [play.google.com/about/play-terms](https://play.google.com/intl/en_us/about/play-terms/) |

We do not use any advertising network, attribution SDK, social login other than Google, or
third-party analytics provider besides the Firebase products named above.

**We do not sell your personal information, and we do not share it with anyone for advertising,
marketing, or any other commercial purpose.** The only disclosures that occur are to the Google
services listed above, acting as our data processors.

---

## 9. How long things are kept

| Data | Retained for |
|---|---|
| Everything on your device | Until you delete it in the app, clear the app's data, or uninstall the app. Uninstalling removes all of it |
| Categories you delete | Held in *Recently deleted* until you restore or permanently delete them |
| Analytics events | Google's default retention for Firebase Analytics event data, currently 2 months for user-level data, with aggregate reporting retained longer |
| Crash reports | Retained by Crashlytics for 90 days |

---

## 10. Your choices and your rights

- **Stop all local storage:** uninstall the app. Android deletes the app's private directory, which
  is where every transaction, payee, category and preference lives. There is no copy elsewhere for
  us to hold on to.
- **Sign out:** your session is cleared and the data scoped to that account becomes inaccessible in
  the app.
- **Take your data with you:** use the CSV export or the JSON backup. Neither is locked to this app.
- **Stop Android's Google Drive backup:** device **Settings → Google → Backup**.
- **Analytics:** this version has no in-app analytics toggle, because nothing collected identifies
  you. Uninstalling the app stops all collection. A toggle in Settings is planned, and the app is
  already built to accommodate one.
- **Requests about analytics data:** because the identifiers we hold are irreversible hashes, we
  generally cannot locate "your" records in the analytics data on request — which is the point of
  hashing them. If you have a specific concern, write to us at the contact address below and we will
  do what we can, including deleting the relevant data set.

If you are in a jurisdiction that grants you rights of access, correction, erasure, portability or
objection — such as the EU/EEA and UK under the GDPR, or India under the Digital Personal Data
Protection Act 2023 — those rights are honoured. In practice, the app's design satisfies most of
them directly: your data is in your possession, exportable by you, and erasable by you without our
involvement.

**Our lawful basis** for processing the limited data described here, where such a basis is required,
is legitimate interest in operating and improving the application, and — for the sign-in — the
performance of the service you asked for.

---

## 11. Security

- Your data lives in the app's private storage, which Android isolates from other applications.
- Identifiers sent for analytics are one-way salted hashes, not the values themselves.
- The app has no server, no login of its own, and no API key that grants access to your data —
  because there is nothing remote to grant access to. This removes an entire category of breach.
- The application is open source, so these claims can be checked rather than taken on trust.

No system is perfectly secure. Files you export yourself, and backups Android makes, are protected
by the security of wherever you put them and of your Google account respectively.

---

## 12. Children

Statement Sense is not directed at children. It is a tool for reading one's own bank and payment
statements, and it is not intended for anyone under the age of 13 (or under 16 in jurisdictions
where that is the applicable threshold). We do not knowingly collect information from children. If
you believe a child has used the app and you would like the associated analytics data removed,
contact us.

---

## 13. International transfers

Analytics and crash data are processed by Google on infrastructure that may be located outside your
country, including in the United States. Google maintains its own safeguards for these transfers,
described in [Google's Privacy Policy](https://policies.google.com/privacy). Your financial data is
not transferred anywhere, because it does not leave your device.

---

## 14. Changes to this policy

If this policy changes materially — in particular if the app ever begins collecting something it
does not collect today — the effective date at the top will be updated and the change will be
described in the app's release notes. The full history of this document is publicly visible in the
project's version control, so any change can be diffed against what came before.

---

## 15. Contact

Questions, requests, or concerns about this policy or about the app's handling of data:

**statementsensesupport@gmail.com**

---

## Appendix A — Google Play Data safety declarations

Provided so that the Play Console form and this document say the same thing. The Data safety section
must match this policy, and a mismatch is a common cause of rejection.

**Does your app collect or share any of the required user data types?** — Yes.

| Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| Personal info → Name | Yes | No | App functionality (profile display) | Required |
| Personal info → Email address | Yes | No | App functionality; Analytics (hashed) | Required |
| Personal info → User IDs | Yes | No | App functionality; Analytics (hashed) | Required |
| App activity → App interactions | Yes | No | Analytics | Required |
| App info and performance → Crash logs | Yes | No | Crash reporting | Required |
| App info and performance → Diagnostics | Yes | No | Crash reporting, Analytics | Required |
| Financial info → Purchase history / other financial info | **No** | No | — | — |
| Location | **No** | No | — | — |
| Files and docs | **No** | No | — | — |

Notes for the form:

- **Financial info is declared as *not collected*.** Play defines "collected" as data transmitted off
  the device. Transaction data is created and read entirely on the device and is never transmitted,
  so it is out of scope for this declaration. It is nonetheless described in §4.2 of this policy,
  because users deserve to know it exists regardless of what the form requires.
- **Location is declared as *not collected*.** The coarse country-level signal Firebase derives from
  IP is Google's own automatic processing, not a location collected by this app.
- Data is **encrypted in transit** — yes; Firebase uses HTTPS.
- Users **can request data deletion** — yes; via the contact address, and by uninstalling.
- The app **has not been independently reviewed** against a global security standard.

---

## Appendix B — Verifying any of this

The app is open source. Every claim above corresponds to code that can be read:

| Claim | Where to check |
|---|---|
| The nine analytics parameters, and the hashing | `core/analytics/` and `core/domain/.../analytics/` |
| The complete, closed set of events | The sealed `AnalyticsEvent` type in `core/domain` |
| That events carry no free text | The test that enumerates every event subclass and asserts it |
| That the temporary PDF is deleted | The `finally` block in the upload use case |
| That only `INTERNET` is requested | `app/src/main/AndroidManifest.xml` |
| That there is no server | The absence of any HTTP client in the dependency list |
