# Working in this repo

Operational context for anyone — human or agent — picking up work here cold. Edit freely; this is meant to be corrected as things change.

This file covers **how to work here**. It deliberately does not describe *what* is being built or *what is left* — that lives in the tracking issue below, which is the single source of truth.

---

## Where the work is tracked

**[Issue #1 — Transactions Parser: V2 Improvements](https://github.com/SandeepChorge/transactions-platform/issues/1)**

Read it first. It carries:

- All 8 original requirements, verbatim, in Part 1
- The open technical questions and their answers, in Part 2
- A six-phase plan (Phase 0 → Phase 5) with a checkbox per item
- **Deviations recorded inline on the items they belong to** — where the implementation departs from the requirement text, and why
- A **"Done in #N"** paragraph closing each finished phase, with test counts and what was verified on a real device

Ticked boxes mean shipped and merged. Phase 5 (payee merge / multi-identifier support) is the only phase still open.

One pull request per phase — #2, #3, #4, #5, #6 — each following the same description pattern: an opening tie to #1, then `Summary`, `What this affects`, `What was asked for, and what we built instead`, `What we achieved`, `Test plan`, `Follow-up`. Read the most recent one before writing a new one.

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

The expensive thing in this repo is not reading code — there are only 106 Kotlin files,
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

1. Bump the version in `TransactionsDatabase.kt`. The exported schema history lands in `core/database/schemas/` (currently `1.json`, `2.json`, `3.json`) and **is committed**.
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
- **A payee is currently identified by its statement name, not by a person.** `PayeeDetailRoute` is keyed on `normalizedPayee`, and the history query is `WHERE normalizedPayee = :x`. That was a deliberate Phase 3 choice — a `PayeeEntity` only exists once an alias and category are saved, so `payeeId` is null for exactly the unmapped payees the detail screen has to support. **It must change in Phase 5.** The moment one payee owns several identifiers, that query shows one identifier's history and silently hides the rest. The change is contained — one route class, four queries, two call sites — but it is a correctness bug, not a polish item, if it is forgotten. Recorded on the Phase 3 checklist item in issue #1.
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
