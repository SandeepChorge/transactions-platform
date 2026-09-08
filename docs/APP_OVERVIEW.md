# Statement Sense — what this app is and how it works

Written for whoever picks the project up next, human or agent, with no memory of it. It answers
*what the thing does and how it hangs together*. It deliberately does not repeat two documents that
already exist and are better at their own jobs:

| Read this for | Go here |
|---|---|
| **How to work here** — which emulator, which GitHub account, how to build, the domain rules that bite | [`CLAUDE.md`](../CLAUDE.md) |
| **The V1 build in detail** — the parsing pipeline, setup, the file index | [`transactions-parser-android/README.md`](../transactions-parser-android/README.md) *(accurate about V1, frozen there — it still says "18 modules, ~69 Kotlin files"; there are now 25 and ~216)* |
| **What is left to do** | Issues [#9](https://github.com/SandeepChorge/transactions-platform/issues/9) (release pipeline) and [#16](https://github.com/SandeepChorge/transactions-platform/issues/16) (dashboards & insights). Those are the single source of truth for scope; this file is not |

Last written against `main` at `634bf98` plus the analytics branch. Treat every number in it as a
snapshot to re-check, not a fact.

---

## 1. The problem it solves

People stop tracking expenses because entering them by hand is tedious. So the app never asks anyone
to type a transaction. Once a month you hand it a **PhonePe or Google Pay statement PDF**, and it
extracts every transaction, groups them by who was paid, and asks you to name the ones it has not
seen before.

**The whole product rests on one idea: the naming persists.** The first upload asks you to map
`AWDHOOT SNACKS CENTRE` → alias "Morning tea stall" → category "Food". Every later upload recognises
that payee on its own. Month one is work; month two is a few taps; month six is one tap. If a change
ever breaks the persistence of payee mappings, it has broken the product, not a feature.

**Everything happens on the device.** Statements contain bank data, so nothing is uploaded, and there
is no server — the "platform" in the repo name is a monorepo shape for a future web and API, not
something that exists. The temporary copy of the PDF is deleted in a `finally` block before the user
is even told the import succeeded.

---

## 2. The life of a transaction

This is the spine. Almost every screen is a view onto some stage of it.

```
a PDF the user picks
   → validated (≤ 80 MB, .pdf, not password-protected)
   → text extracted on-device (PdfBox-Android)
   → routed by content to a parser (PhonePe / Google Pay) — no match is a clean rejection
   → parsed into transactions
   → repeats flagged against everything the account already holds
   → saved as a PENDING session
   → known payees auto-mapped by their normalized name
   ↓
the user maps whatever is left: alias + category, per payee
   → at zero unmapped, the session flips to COMPLETED and turns read-only
   ↓
mapped transactions become the dashboards, the payee directory, search, and CSV export
```

Two consequences worth holding onto:

- **A session is never "half imported".** It is imported whole and then *mapped* over time. Mapping
  is resumable; a Pending session is normal, not a failure.
- **Duplicate detection is on payment identity, never on dates.** Re-importing a statement whose
  period overlaps an earlier one falls out for free, with no period-comparison logic anywhere. It
  matches on issuer reference, then UTR, then a weaker composite for rows that carry neither.

---

## 3. What the user actually sees

Five destinations behind a bottom bar, with a centre `+` that goes straight to upload.

**Home** is not a screen so much as a **catalog of dashboards**. Four ship built-in — *Pulse*,
*Categories*, *Mapping health*, *Payees* — and the user can reorder them, switch any off, star one as
the default, and build their own out of the same widgets. A date range (this week / this month / last
month / all time) applies across them. Tapping a category opens an insight screen that explains that
one category on its own. An anomaly callout surfaces charges far above what that payee or category
normally costs.

*Mapping health sits third on purpose.* If a quarter of the period's spend has no name on it, every
total on Pulse and Categories is a partial total being presented as a complete one, and the user
should meet that fact before they reach the payee ranking.

**Statements** is the upload history — every import, successful or failed, with its reason. Opening a
session is where mapping happens.

**Payees** is the directory: everyone ever paid, what they cost, and their whole history across every
statement. Merged payees show their combined history, which is subtler than it sounds — see §6.

**Search** (from Home) finds a transaction by payee, amount, or date across all statements.

**You** holds profile, categories, theme (light/dark, user-switchable), CSV export, backup and
restore, and *Recently deleted*.

---

## 4. How the code is arranged

Clean Architecture, feature-layered modules, MVI presentation, Koin DI, Room, Compose. Gradle
convention plugins live in `build-logic`. **The Gradle root is `transactions-parser-android/`, not the
repo root.**

```
core/      domain · presentation · designsystem · database · parsing · pdf · analytics
feature/   auth · profile · upload · sessions · categories · dashboard · settings
app/       assembles Koin modules, owns the nav graph and the bottom bar
```

Each feature splits into `domain` / `data` / `presentation` where it needs to. Dependencies point
inward: a `core` module never depends on a `feature` module, and **`core:domain` is pure Kotlin — no
Android, no DI framework**. That constraint is load-bearing, not stylistic; it is why the analytics
contracts, the duplicate detector, and the backup codec are all unit-testable without a device.

`:app` is the only module with `BuildConfig`, so anything configured at build time (the analytics
salt, the OAuth client id) is injected from there rather than read where it is used.

---

## 5. The data

Room, six tables, currently schema version 4, with the exported schema history committed under
`core/database/schemas/`.

| Table | Holds |
|---|---|
| `categories` | User-defined categories. The only table with soft delete actually wired up |
| `payees` | The user's name for someone, plus their category |
| `payee_identifiers` | The raw statement names that all mean the same payee — this is what makes merging work |
| `sessions` | One import: file, source, period, status |
| `transactions` | The rows themselves, in **paise** (never floats), with duplicate and exclusion flags |
| `upload_logs` | Every import attempt, including the failures |

**Changing the schema has a four-step ritual and CI enforces it.** Bump the version, write the
migration into `ALL_MIGRATIONS`, add a `MigrationTest` case that proves *data survives*, and run
`verifyRoomMigrations`. Never add `fallbackToDestructiveMigration()` — it silently wipes user data,
which is the exact failure the whole apparatus exists to prevent. `CLAUDE.md` has the detail.

---

## 6. Five things that look like bugs and are not

These have each been "fixed" wrongly before. `CLAUDE.md` carries the full list; these are the ones
that most often trip someone reading the code cold.

1. **Statement timestamps are the PDF's printed wall clock stored as-if-UTC**, and read back with
   `ZoneOffset.UTC` everywhere. Adding a timezone conversion "to fix" a date *introduces* a bug.
2. **`isDuplicate` is a system fact; `isExcluded` is a user decision.** `isExcluded` starts equal to
   `isDuplicate` and is then owned by the user. Aggregates filter on `isExcluded`, never on
   `isDuplicate` — that is what lets a "count this anyway" override survive a later import.
3. **A payee is identified by its statement name, not by `transactions.payeeId`.** Matching on
   `payeeId` would look tidier and would drop every row imported before that payee was ever mapped.
   Any new payee-scoped query must use the `SAME_PAYEE_NAMES` predicate in `dao/Daos.kt`.
4. **Account scoping lives in the data layer.** `RoomDataSources.kt` resolves `ownerId` internally.
   ViewModels neither pass it nor filter on it, and must not start.
5. **Dashboard preferences store the *disabled* set, not the enabled one.** Stored-as-enabled would
   mean a dashboard added in a later release arrives switched off for every existing account and is
   never discovered. Same reasoning applies to any future on/off catalog.

---

## 7. Instrumentation

Firebase Analytics and Crashlytics, added on `feat/analytics-and-crashlytics`. Contracts sit in
`core:domain`; only `core:analytics` and `:app` see Firebase.

Nine parameters ride every event — app name and version, device, manufacturer, model, OS version and
release, plus **salted SHA-256 hashes** of the account id and email. Signed out, those two keys are
*absent* rather than empty, because an empty string is a real bucket in Firebase and would merge
every signed-out user into one phantom account.

Eight events: `app_started`, `statement_imported`, `mapping_completed`, `mapping_cancelled`,
`default_dashboard_changed`, `data_exported`, `logged_in`, `logged_out`. Events are a sealed type, so
a typo in a name is a compile error rather than a permanent hole in the data, and each one is
validated against Firebase's documented limits *before* the SDK sees it — Firebase discards malformed
events silently, and a dashboard that is quietly wrong is worse than one that is visibly broken.

`isEnabled` is a lambda read per event, so a consent toggle can be added later without touching the
module. There is no toggle today; nothing identifying is collected.

**`ANALYTICS_SALT` must exist as a repository secret before the next release build**, and must never
change once released — a new salt re-hashes every account into a new id and every returning user is
counted as new from that build on.

---

## 8. Where the release stands

- **Name:** *Statement Sense*. `applicationId` stays `com.madtitan94.transactionsparser` — it is
  immutable once published; the display name is not.
- **Icon:** an adaptive icon drawn entirely as strokes, in the design system's amber on its own near-
  black green. Stroke-only is the constraint, not a style choice: the same drawable is the
  `monochrome` layer for Android 13 themed icons, where the launcher discards colour and tints it one
  flat shade, and a filled page would survive as a featureless amber rectangle.
- **Store assets:** `design/play-store-icon.svg` is the source; rendered PNGs live in
  `design/play-store/`.
- **Screenshots are not done.** They must be captured against *synthetic* data — the development
  emulator holds real statements, and this repository is public.
- The release pipeline (signing, versioning, Play upload) is issue #9 and is at Phase 6 of 8.

---

## 9. What does not exist yet

Bank statements beyond PhonePe and Google Pay. Password-protected PDFs. Cloud sync or any server —
`transactions-parser-web/`, `services/` and `shared/` are empty scaffolding. AI-assisted parsing
(the `StatementParser` interface leaves room for an opt-in fallback, deliberately unused). Budgets
and home-screen widgets, which are the later phases of #16. Recovery of a cancelled session — soft
delete is wired for categories only, so *Recently deleted* does not cover sessions.

---

## 10. If you are starting work right now

Read `CLAUDE.md` first — it will stop you pushing as the wrong GitHub account and installing to the
wrong emulator, both of which have gone wrong before. Then read whichever of #9 or #16 owns the phase
you are on; their phase numbers collide, so always say which issue a phase belongs to.

```bash
cd transactions-parser-android && ./gradlew test verifyRoomMigrations
```

One pull request per phase, and tick the issue's checklist in the same wrap-up step. A merged PR is
not a finished phase: tick the box against a verified run, not against a merge.
