# Working in this repo

Operational context for anyone — human or agent — picking up work here cold. Edit freely; this is meant to be corrected as things change.

This file covers **how to work here**, plus a one-paragraph note on how far along the work is. It deliberately does not restate *what* is being built or itemise *what is left* — that lives in the tracking issue below, which is the single source of truth.

---

## Where the work is tracked

**[Issue #1 — Transactions Parser: V2 Improvements](https://github.com/SandeepChorge/transactions-platform/issues/1)**

Read it first. It carries:

- All 8 original requirements, verbatim, in Part 1
- The open technical questions and their answers, in Part 2
- A six-phase plan (Phase 0 → Phase 5) with a checkbox per item
- **Deviations recorded inline on the items they belong to** — where the implementation departs from the requirement text, and why
- A **"Done in #N"** paragraph closing each finished phase, with test counts and what was verified on a real device

A ticked box means shipped — merged, or open in the PR named by its phase's "Done in #N" line.

**Where things stand (Aug 2026):** all six phases are implemented and every checkbox in #1 is ticked. Phases 0–4 are merged; **Phase 5 is in review as [#7](https://github.com/SandeepChorge/transactions-platform/pull/7)** and is the only one not yet on `main`. There is no Phase 6 — the plan is finished. What remains is the *Follow-up* list at the bottom of #7 (and the equivalent lists in the earlier PRs), which is loose ends rather than planned work. The one worth knowing before touching payees: **the merge has never been executed on a device** — it is covered by instrumented tests against real Room only. See the Devices note below for why.

One pull request per phase — #2, #3, #4, #5, #6, #7 — each following the same description pattern: an opening tie to #1, then `Summary`, `What this affects`, `What was asked for, and what we built instead`, `What we achieved`, `Test plan`, `Follow-up`. Read the most recent one before writing a new one.

> **Known stale:** issue #1 still ends with a *Process* section saying "No commits or pushes from me — you commit and push yourself." That is no longer how this works; the current convention is to commit, open the PR, and tick the issue checklist as one wrap-up step.

---

## GitHub account — read before any push

All git and GitHub operations on this repo must use the personal account **`SandeepChorge`**. Never `sandeepchorge03` (the work account).

This has gone wrong before: macOS `osxkeychain` cached `sandeepchorge03` credentials ahead of `gh`'s per-host credential helper and silently pushed as the wrong account until the stale entry was erased. Both accounts are authenticated in `gh` simultaneously, so the wrong one is always one mistake away.

Before pushing or running any `gh` command:

```bash
gh auth status
```

Confirm `SandeepChorge` is the **active** account. If a push lands as the wrong user, erase the stale keychain entry with `git credential-osxkeychain erase` (host `github.com`) and retry.

---

## Devices

Use **`emulator-5556`** — Pixel Tablet Play 36, Android 16 "Baklava", arm64.

**Never use `emulator-5554`** (Pixel Tablet, API 32). It is kept running for separate manual testing and must not be installed to or driven.

Both are usually booted at once, so an unqualified command will pick the wrong one or fail on ambiguity. Always be explicit:

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest
```

```bash
adb -s emulator-5556 shell am start -n com.madtitan94.transactionsparser/.MainActivity
```

> emulator-5556 currently holds a **600-row synthetic statement dated Jan–Jul 2024**, imported to exercise deep pagination in Phase 4. That is test data. The real statements are May/June 2026 — the fixture was dated 2024 specifically to keep the two apart. It was never committed to the repo.

> **The emulator holds real financial data, and most of it cannot be undone from inside the app.** Prefer a throwaway row over a real one, and dump the DB before and after anything that writes. The DB is not readable in place — pull it and query on the host:
>
> ```bash
> adb -s emulator-5556 exec-out run-as com.madtitan94.transactionsparser cat databases/transactions_parser.db > /tmp/t.db
> ```
>
> Pull the `-wal` file alongside it or recent writes will be missing. Delete the copies when done.
>
> This is why **the payee merge has never been run live**: both answers to the same-name prompt write irreversibly into the real payee set, and the synthetic statement has only one payee name, so it cannot supply a throwaway pair to merge. Staging that needs a second synthetic statement carrying a deliberately misspelled payee.

---

## Building and testing

The Gradle project root is **`transactions-parser-android/`**, not the repo root. `./gradlew` from the repo root fails with "no such file or directory".

```bash
cd transactions-parser-android
```

| Command | What it does |
|---|---|
| `./gradlew test` | All unit tests (JVM, no device) |
| `./gradlew verifyRoomMigrations` | Fails if a schema version bump shipped without a migration path. Wired into `check`, so CI enforces it too |
| `ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest` | Instrumented tests against real Room |
| `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug` | Install on the emulator |

Run `test` and `verifyRoomMigrations` before calling any phase done. Run the instrumented suite too whenever `core:database` changed.

### Test conventions that will bite you

- **Unit tests use JUnit 5** (`org.junit.jupiter.api.Test`). Importing `org.junit.Test` gives `Unresolved reference 'Test'`. This includes `core:parsing`, which is easy to assume otherwise.
- **Instrumented tests use JUnit 4** (`org.junit.Test` + `@RunWith(AndroidJUnit4::class)`).
- **Instrumented test method names must be camelCase.** Backtick names containing spaces fail dexing below DEX 040. Unit tests may use backticks freely.
- **There is no Robolectric and no `isReturnDefaultValues`, so a ViewModel that reads its route cannot be unit-tested.** Both `SessionDetailViewModel` and `PayeeDetailViewModel` call `savedStateHandle.toRoute<...>()`, which needs Android. The established workaround is to extract the decision into a pure object in `feature:<name>:domain` and test that — `AliasSuggester`, `MappingDecider`, `PayeeGrouper` and `DuplicateSelection` all exist for this reason. Reach for it before reaching for Robolectric.
- **`feature:sessions:presentation` has no test source set at all.** A crash that a single test would have caught shipped to the device because of it (see the `combine` trap below). Worth knowing before assuming a change there is covered.

### Changing the database schema

Existing local data must never be lost on upgrade — that is requirement 7, and there is machinery in place to enforce it. Every schema change needs all four of these:

1. Bump the version in `TransactionsDatabase.kt`. The exported schema history lands in `core/database/schemas/` (currently `1.json` … `4.json`) and **is committed**.
2. Write the `Migration` in `core/database/.../migration/Migrations.kt` and add it to `ALL_MIGRATIONS`.
3. Add a `MigrationTest` case using Room's `MigrationTestHelper` — `MigrationTest.kt` is the template, and it checks data survives, not just that the migration runs.
4. Run `./gradlew verifyRoomMigrations`. It diffs the schema history against the registered migrations and fails the build if a version pair has no path. It is wired into `check`, so CI blocks on it too.

Three traps worth knowing:

- **Removing a column means rebuilding the table.** SQLite below 3.35 cannot drop one. `MIGRATION_3_4` is the worked example: create `payees_new`, `INSERT ... SELECT` **carrying the ids across explicitly** so foreign keys pointing at them stay valid, drop, rename, then recreate every index — the old table's indexes go with it. Leaving the dead columns in place instead is worse than the work: two sources of truth for the same fact.
- **Never add `fallbackToDestructiveMigration()`.** It silently wipes user data on a version mismatch, which is the exact failure all of the above exists to prevent.
- **Registering a migration is not the same as it reaching Room.** `MigrationTest` builds Room directly, so a migration missing from the *production* builder would pass every migration test and only fail on a real user's upgrade. `DatabaseWiringTest` closes that gap by going through `buildDatabase()` — the same function `coreDatabaseModule` calls. Keep it that way; don't inline the builder back into the Koin module.

---

## Domain rules that are easy to get wrong

- **Statement timestamps are the PDF's printed wall clock stored as-if-UTC.** They are read back with `ZoneOffset.UTC` everywhere — day/month bucketing, CSV export, list headers. Adding a timezone conversion "to fix" a date will *introduce* a bug, not remove one.
- **`isDuplicate` is a system fact; `isExcluded` is a user decision.** `isExcluded` is initialized from `isDuplicate` at import and then owned by the user. Aggregates filter on `isExcluded`, never on `isDuplicate` — that separation is what lets a user's "count this anyway" override survive a later import.
- **Account scoping is enforced in the data layer, not above it.** Every method in `core/database/.../RoomDataSources.kt` resolves `ownerId` through `ActiveAccountProvider` internally. ViewModels and use cases neither pass it nor filter on it, and must not start.
- **Soft delete is only wired up for categories.** All five tables carry `isDeleted` / `deletedAtMillis`, but only `CategoryDao` writes them. Cancelling a session flips its status and leaves `isDeleted = 0`, so a cancelled session is **not** recoverable from Settings › Recently deleted. The one deliberate exception is the payee a merge empties out — that is **hard**-deleted, because after its names move it owns nothing and restoring it would produce a payee no screen can reach.
- **A payee owns its statement names; it is not one of them.** Since Phase 5 (v4), `payees` holds only what the user decided — alias and category — and every statement name is a row in `payee_identifiers`. Resolve a name through the identifier join (`PayeeDao.findByNormalizedName` / `observeByNormalizedName` already do), never through a name column on the payee; that is what makes a merged-away spelling still auto-map to the survivor on a re-import.
- **Payee history is matched by the payee's set of *names*, not by `transactions.payeeId`.** This looks wrong and is not. A transaction imported before its payee was ever mapped keeps a null `payeeId`, so an id-based history would silently drop every pre-mapping row. The four Payee Detail queries share one predicate, `SAME_PAYEE_NAMES` in `Daos.kt`: this name, plus every sibling name its payee owns. The union is load-bearing — an unmapped name owns no identifier, so the subquery is empty and only the first branch fires, which is how an unmapped payee still gets a working detail screen with no special case. The `:includeLinkedNames` flag collapses the predicate back to a single name and is what the per-identifier filter binds; keep it as one parameterised predicate rather than writing exact-match twins, so a filtered header can never drift from the filtered list above it.
- **A merge is three statements in one transaction, and the order is load-bearing.** Re-point identifiers, re-point transactions, then delete the source payee. The identifier FK is `RESTRICT`, so a merge that skips the first step fails loudly at the delete instead of quietly taking the names with it. All of it lives in `RoomPayeeDataSource.linkToPayee`; `repointTransactions` sits on `PayeeDao` despite writing `transactions` so one data source can run the whole thing inside a single `withTransaction`.
- **A flow an `init` block collects must be declared above it.** Kotlin runs property initialisers in declaration order, interleaved with `init`. Declaring a flow *below* `init { observeX() }` leaves it null when the block runs — which shipped once as `Flow.collect on a null object reference`, crashing the app on opening any session. Related: `combine` has typed overloads only up to five flows, so a sixth has to be pre-paired into one (`payeePool` in `SessionDetailViewModel`), and that pairing is exactly the kind of declaration that ends up in the wrong place.
- **Exclusions belong in aggregates, not in JOIN conditions.** Putting `AND t.isExcluded = 0` in a `LEFT JOIN` collapses an all-excluded parent to zero rows and makes a successful import look like a failed one. Use conditional aggregation, and wrap `SUM` in `IFNULL` — a `LEFT JOIN` with no matches yields NULL from `SUM` (but 0 from `COUNT`), which fails to bind to a non-null `Int`.

---

## Module layout

Clean Architecture with feature-layered modularization, MVI presentation, Koin DI, Room, and Kotlin convention plugins in `build-logic`.

```
core/       domain · presentation · designsystem · database · parsing · pdf
feature/    auth (data · presentation)
            profile (domain · data · presentation)
            upload (domain · data · presentation)
            sessions (domain · presentation)
            categories (presentation)
            settings (presentation)
app/        assembles Koin modules, owns the nav graph and bottom bar
```

Features carry only the layers they need — most have no `data` module because persistence lives in `core:database` behind the data-source interfaces in `core:domain`. A feature's `domain` module is pure Kotlin and is where testable decision logic goes.

`core:domain` is pure Kotlin and must stay free of Android and of any DI framework. Dependencies point inward — a `core` module never depends on a `feature` module.

Follow the project's existing Android skills (data-layer, presentation-MVI, navigation, testing, compose-ui) rather than freehand patterns.

---

## Monorepo

`transactions-parser-android/` is the only actively developed module today. `transactions-parser-web/`, `services/` and `shared/` are scaffolding. CI is path-filtered, so Android changes only trigger the Android pipeline.
