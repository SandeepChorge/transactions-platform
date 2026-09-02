# Working in this repo

Operational context for anyone — human or agent — picking up work here cold. Edit freely; this is meant to be corrected as things change.

This file covers **how to work here**. It deliberately does not describe *what* is being built or *what is left* — that lives in the tracking issues below, which are the single source of truth.

---

## Where the work is tracked

Three issues, each the single source of truth for its own scope. None of them describes
*how* to work here — that is this file's job, and the split is deliberate.

| Issue | Scope | State |
|---|---|---|
| [#1 — V2 Improvements](https://github.com/SandeepChorge/transactions-platform/issues/1) | The 8 original app requirements, Phase 0 → Phase 5 | **Closed 2026-08-31.** History, not a work list |
| [#9 — Versioning, Signing & Distribution](https://github.com/SandeepChorge/transactions-platform/issues/9) | The release pipeline, Phase 1 → Phase 8 | Open — Phase 6 nearly done, Phase 7–8 not started |
| [#16 — Dashboard & Insights (V3)](https://github.com/SandeepChorge/transactions-platform/issues/16) | Dashboard, search, budgets, widgets, Phase 6 → Phase 12 | Open — not started |

**Read #9 and #16 before starting anything.** #1 is worth reading for context on why the
app is shaped the way it is, but every box in it is ticked and shipped.

> **Phase numbers collide across issues.** #9 counts 1→8 and #16 counts 6→12, so "Phase 7"
> is R8 shrinking in one and the dashboard settings screen in the other. Always say which
> issue a phase belongs to.

All three carry the same structure, and it is worth matching when you add to them:
requirements verbatim first, then the decisions taken and why, then a phased plan with a
checkbox per item. **Deviations are recorded inline on the item they belong to** — where
the implementation departs from the requirement text, and why — and a **"Done in #N"**
paragraph closes each finished phase with test counts and what was verified on a real
device. Recording what was *not* done, and what is proven versus merely merged, matters
more here than a tidy checklist.

### Convention for finishing a phase

One pull request per phase. Commit, open the PR, and tick the issue's checklist as one
wrap-up step — do not leave the ticking for later.

PR descriptions follow the pattern set by #2–#7 and continued in #10–#21: an opening tie
to the issue, then `Summary`, `What this affects`, `What was asked for, and what we built
instead`, `What we achieved`, `Test plan`, `Follow-up`. Read the most recent PR before
writing a new one.

A merged PR is not a finished phase. The Play upload step merged green in #19 and then
failed three CI runs in a row on things no local check could have caught — gem
permissions, a metadata path, and a version pin that silently did nothing. Fixes landed
in #20 and #21. Tick a box against a verified run, not against a merge.

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

> emulator-5556 currently holds a **600-row synthetic statement dated Jan–Jul 2024**, imported to exercise deep pagination in #1's Phase 4. That is test data. The real statements are May/June 2026 — the fixture was dated 2024 specifically to keep the two apart. It was never committed to the repo.

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

### Keeping tool output small

The expensive thing in this repo is not reading code — there are only ~126 Kotlin files,
and the whole source tree is small. It is unbounded tool output: a full `logcat` dump,
raw Gradle chatter, or a screenshot per verification step. Cap it at the source.

- **Never run `adb logcat -d` unbounded.** Always bound and filter:

  ```bash
  adb -s emulator-5556 logcat -d -t 200 | grep -i transactionsparser
  ```

- **Filter Gradle.** Full output only when the grep is not enough to explain a failure:

  ```bash
  ./gradlew test verifyRoomMigrations assembleDebug 2>&1 | grep -E "^e:|FAILED|BUILD"
  ```

  `--console=plain` also helps when a task's progress bars are the noise.

- **Verifying UI state is not the same as looking at the UI.** To confirm a label,
  a row count, or whether a control is enabled, dump the hierarchy and grep it —
  it is far cheaper than an image:

  ```bash
  adb -s emulator-5556 shell uiautomator dump /sdcard/ui.xml
  adb -s emulator-5556 shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"'
  ```

  Use `screencap` only for genuine visual checks — layout, spacing, theming.

- **Read regions, not whole files.** `sed -n '120,180p' path/to/File.kt` over `cat`,
  for the large ones: `Daos.kt`, `RoomDataSources.kt`, `SessionDetailViewModel.kt`,
  `SessionDetailScreen.kt`, `PayeeDetailViewModel.kt`, `PayeeDetailScreen.kt`.

- **`/clear` at phase boundaries.** One PR per phase means the previous phase's context
  has no value in the next one, but it is resent on every turn until cleared.

### Test conventions that will bite you

- **Unit tests use JUnit 5** (`org.junit.jupiter.api.Test`). Importing `org.junit.Test` gives `Unresolved reference 'Test'`. This includes `core:parsing`, which is easy to assume otherwise.
- **Instrumented tests use JUnit 4** (`org.junit.Test` + `@RunWith(AndroidJUnit4::class)`).
- **Instrumented test method names must be camelCase.** Backtick names containing spaces fail dexing below DEX 040. Unit tests may use backticks freely.

### Changing the database schema

Existing local data must never be lost on upgrade — that is requirement 7, and there is machinery in place to enforce it. Every schema change needs all four of these:

1. Bump the version in `TransactionsDatabase.kt`. The exported schema history lands in `core/database/schemas/` (currently `1.json` through `4.json`) and **is committed**.
2. Write the `Migration` in `core/database/.../migration/Migrations.kt` and add it to `ALL_MIGRATIONS`.
3. Add a `MigrationTest` case using Room's `MigrationTestHelper` — `MigrationTest.kt` is the template, and it checks data survives, not just that the migration runs.
4. Run `./gradlew verifyRoomMigrations`. It diffs the schema history against the registered migrations and fails the build if a version pair has no path. It is wired into `check`, so CI blocks on it too.

Two traps worth knowing:

- **Never add `fallbackToDestructiveMigration()`.** It silently wipes user data on a version mismatch, which is the exact failure all of the above exists to prevent.
- **Registering a migration is not the same as it reaching Room.** `MigrationTest` builds Room directly, so a migration missing from the *production* builder would pass every migration test and only fail on a real user's upgrade. `DatabaseWiringTest` closes that gap by going through `buildDatabase()` — the same function `coreDatabaseModule` calls. Keep it that way; don't inline the builder back into the Koin module.

---

## Domain rules that are easy to get wrong

- **Statement timestamps are the PDF's printed wall clock stored as-if-UTC.** They are read back with `ZoneOffset.UTC` everywhere — day/month bucketing, CSV export, list headers. Adding a timezone conversion "to fix" a date will *introduce* a bug, not remove one.
- **`isDuplicate` is a system fact; `isExcluded` is a user decision.** `isExcluded` is initialized from `isDuplicate` at import and then owned by the user. Aggregates filter on `isExcluded`, never on `isDuplicate` — that separation is what lets a user's "count this anyway" override survive a later import.
- **Account scoping is enforced in the data layer, not above it.** Every method in `core/database/.../RoomDataSources.kt` resolves `ownerId` through `ActiveAccountProvider` internally. ViewModels and use cases neither pass it nor filter on it, and must not start.
- **Soft delete is only wired up for categories.** All five tables carry `isDeleted` / `deletedAtMillis`, but only `CategoryDao` writes them. Cancelling a session flips its status and leaves `isDeleted = 0`, so a cancelled session is **not** recoverable from Settings › Recently deleted.
- **A payee is still identified by its statement name — and that is now correct, not a shortcut.** `PayeeDetailRoute` is keyed on `normalizedPayee`, and the payee-scoped queries match on names rather than on `transactions.payeeId`. Matching on `payeeId` would look tidier and would be a regression: a row imported before its payee was ever mapped keeps a null `payeeId`, and would drop out of that payee's own history. #1's Phase 5 fixed the real bug — a merged payee hiding its other identifiers' history — by joining siblings in through `payee_identifiers`, not by re-keying on the mapping.
  - **Any new payee-scoped query must use the `SAME_PAYEE_NAMES` predicate** in `core/database/.../dao/Daos.kt`, never a bare `normalizedPayee = :x`. The bare form silently shows one identifier of a merged payee and hides the rest — which is exactly the bug that was fixed. Its `:includeLinkedNames` flag collapses it back to one name for the detail screen's per-identifier filter, so one parameterised predicate serves both and a filtered header cannot drift from the list beneath it.
  - The two remaining bare matches (`setDuplicatesExcluded`, `assignPayee`) are session-scoped *writes* on one exact name. Those are meant to be exact — do not "fix" them.
- **Exclusions belong in aggregates, not in JOIN conditions.** Putting `AND t.isExcluded = 0` in a `LEFT JOIN` collapses an all-excluded parent to zero rows and makes a successful import look like a failed one. Use conditional aggregation, and wrap `SUM` in `IFNULL` — a `LEFT JOIN` with no matches yields NULL from `SUM` (but 0 from `COUNT`), which fails to bind to a non-null `Int`.

---

## Module layout

Clean Architecture with feature-layered modularization, MVI presentation, Koin DI, Room, and Kotlin convention plugins in `build-logic`.

```
core/       domain · presentation · designsystem · database · parsing · pdf
feature/    auth · profile · upload · sessions · categories · settings
app/        assembles Koin modules, owns the nav graph and bottom bar
```

`core:domain` is pure Kotlin and must stay free of Android and of any DI framework. Dependencies point inward — a `core` module never depends on a `feature` module.

Follow the project's existing Android skills (data-layer, presentation-MVI, navigation, testing, compose-ui) rather than freehand patterns.

---

## Monorepo

`transactions-parser-android/` is the only actively developed module today. `transactions-parser-web/`, `services/` and `shared/` are scaffolding. CI is path-filtered, so Android changes only trigger the Android pipeline.
