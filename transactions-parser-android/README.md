# Statement Sense (Android)

Android app that turns PhonePe / Google Pay transaction statement PDFs into categorized expenses — parsed **entirely on-device**, because statements contain bank data and nothing may leave the phone.

Display name: **Statement Sense**. `applicationId` stays `com.madtitan94.transactionsparser` (immutable once published).

> **Where to read what**
>
> | Need | Go here |
> |---|---|
> | Product story & how the pieces hang together | [`docs/APP_OVERVIEW.md`](../docs/APP_OVERVIEW.md) |
> | How to work here (emulator, GitHub account, domain traps, schema ritual) | [`CLAUDE.md`](../CLAUDE.md) |
> | What is left to do | Issues [#9](https://github.com/SandeepChorge/transactions-platform/issues/9) (release pipeline) and [#16](https://github.com/SandeepChorge/transactions-platform/issues/16) (dashboards & insights) |
> | Architecture rules, data model, parsing, conventions | **This README** |

---

## 1. The product idea

Manually entering daily expenses is tedious, so people stop doing it. Instead: upload a statement PDF once a month, and the app extracts every transaction, groups it by payee, and learns your naming.

**The game changer:** payee mappings persist. The first upload asks you to map `AWDHOOT SNACKS CENTRE` → alias "Morning tea stall" → category "Food". Every future upload recognizes that payee automatically. Only new payees require work, so effort drops to near-zero after month one.

**Example of the payee summary the app produces:**

> **abc shop** — amounts ₹10, ₹20 · total ₹30 · 2 entries · usually around ≈1 PM, ≈4 PM

---

## 2. Current status

**25 Gradle modules · ~227 Kotlin files · Room schema v4 · 57 test files.**

V1 (import → map → sessions) shipped first. Everything below that was added through closed PRs #2–#40 — see the summary table at the end of this file, or the issues for remaining work.

| Area | Status |
|---|---|
| Google login | ✅ Credential Manager + Sign in with Google |
| Profile | ✅ name required; mobile / gender optional; email read-only |
| PDF upload (≤80 MB), password rejection, wrong-doc rejection | ✅ |
| PhonePe + Google Pay parsers | ✅ on-device; PhonePe keeps imperfect rows rather than dropping them (#24) |
| Temp PDF deleted before success is shown | ✅ |
| Sessions: Pending / Completed / Cancelled | ✅ |
| Payee mapping, auto-map, confirm-all, resume | ✅ |
| Duplicate detection (`isDuplicate` / `isExcluded`) | ✅ (#4) |
| Multi-account isolation + soft delete | ✅ (#3); soft delete UI wired for categories only |
| Payee detail (paged cross-session history) | ✅ (#5) |
| Settings / CSV export / Recently deleted / sticky day headers | ✅ (#6) |
| Payee merge + `payee_identifiers` | ✅ (#7) |
| JSON backup / restore | ✅ (#17) |
| Design theme (light/dark, artboard-sourced) | ✅ (#25–#26) |
| Home as dashboard catalog (Pulse / Categories / Mapping health / Payees + custom) | ✅ (#29–#34) |
| Bottom bar: Home · Statements · ⊕ · Payees · You | ✅ (#33) |
| Category insight carousel | ✅ (#35) |
| Global search + anomaly callouts | ✅ (#36) |
| Firebase Analytics + Crashlytics | ✅ (#37) |
| R8 on release; CI versioning / signing / Firebase + Play internal upload | ✅ (#10–#14, #19–#22); Play upload still hardening under #9 |
| Privacy policy / store listing / delete-account docs | ✅ (#38–#39) |
| Prefill parsed payee name on map | ✅ (#40) |

**Still open (tracked on issues, not here):** budgets, recurring detection, shareable report, home-screen widget, app lock (#16); remaining Play / distribution polish (#9). **Deferred hooks still unused:** other bank parsers, password-prompted PDFs, cloud sync, AI-assisted parsing fallback.

---

## 3. Setup

**Prerequisites:** Android Studio with SDK 37, JDK 21 (Gradle toolchain), a device/emulator with Google Play services.

**Emulator:** use `emulator-5556` only — see `CLAUDE.md`. Never drive `emulator-5554`.

**Google Sign-In** — GCP project `transactionparser-502216` exists; put the web client ID in (gitignored) `gradle.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=368689223649-....apps.googleusercontent.com
```

Copy from `gradle.properties.example`. CI builds its properties from that example, so a setting that exists only in your local file does not exist in CI.

Still verify in [GCP Credentials](https://console.cloud.google.com/apis/credentials?project=transactionparser-502216):
1. An **Android** OAuth client exists — package `com.madtitan94.transactionsparser` + debug SHA-1 from `./gradlew signingReport`.
2. Your Google account is listed under consent-screen **Test users**.

Use the **Web** client ID in `setServerClientId` — passing the Android one is the most common sign-in failure.

**Build / test** (from this directory, not the repo root):

```bash
./gradlew assembleDebug
./gradlew test verifyRoomMigrations
ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug
```

---

## 4. Architecture

Clean Architecture with **feature-layered modularization** — split by feature first, then by layer. `presentation → domain ← data`; domain depends on nothing. **Features never depend on each other**; anything shared moves to `core`. MVI in the presentation layer.

```
:app                              auth gate, bottom bar, NavHost, startKoin, BuildConfig
:build-logic                      Gradle convention plugins

:core:domain          (pure JVM)  Result<T,E>, models, datasource interfaces,
                                  DuplicateDetector, backup/export/analytics contracts
:core:presentation                UiText, ObserveAsEvents, error→UiText, ₹/date formatters
:core:designsystem                theme + shared composables + chart primitives
:core:database                    Room: entities, DAOs, migrations, datasource impls
:core:parsing         (pure JVM)  PhonePe/GPay parsers + registry
:core:pdf                         PdfBox-Android text extractor
:core:analytics                   Firebase Analytics / Crashlytics adapters

:feature:auth:{data,presentation}
:feature:profile:{domain,data,presentation}
:feature:upload:{domain,data,presentation}     ImportStatementUseCase
:feature:sessions:{domain,presentation}        history, mapping, payee directory/detail, search
:feature:categories:presentation
:feature:dashboard:{domain,data,presentation}  Home catalog, manage/builder, category insight
:feature:settings:{domain,data,presentation}   You tab, theme, CSV, backup, recently deleted
```

**Dependency rules**

| Layer | May depend on |
|---|---|
| `presentation` | own `domain`, `core:domain`, `core:presentation`, `core:designsystem` |
| `data` | own `domain`, `core:domain` |
| `domain` | `core:domain` only |
| `:app` | everything (wires it all) |

**Convention plugins** (`build-logic/src/main/kotlin/`) — no versions or config duplicated in module build files:

| Plugin | Purpose |
|---|---|
| `transactionsparser.android.library` | compileSdk 37, minSdk 26, JUnit5 + AssertK + Turbine |
| `transactionsparser.compose` | above + Compose BOM, Material 3, icons |
| `transactionsparser.android.feature` | above + Koin, Navigation, serialization, core module deps |
| `transactionsparser.jvm.library` | pure Kotlin, JVM 11, test stack |
| `transactionsparser.room` | Room + KSP |

All dependency versions live in `gradle/libs.versions.toml`.

---

## 5. Conventions (follow these when adding code)

**Presentation — MVI.** Every screen has four parts:

```kotlin
data class XState(...)                          // single state data class
sealed interface XAction { ... }                // user intents
sealed interface XEvent { ... }                 // one-time effects (nav, snackbar)
class XViewModel : ViewModel() {                // StateFlow + Channel
    private val _state = MutableStateFlow(XState())
    val state = _state.asStateFlow()            // always update via _state.update { }
    private val _events = Channel<XEvent>()
    val events = _events.receiveAsFlow()
    fun onAction(action: XAction) { ... }
}
```

Composables split into **Root** (owns `koinViewModel()`, observes events via `ObserveAsEvents`, holds navigation callbacks) and **Screen** (pure `state` + `onAction`, previewable, zero business logic) — both in the same file. Never pass ViewModels down the tree. Collect with `collectAsStateWithLifecycle()`. Text-field state lives in the ViewModel; every keystroke dispatches an Action.

**Errors — no exceptions for expected failures.** Everything returns `Result<T, E : Error>`:

```kotlin
repository.save(x)
    .onSuccess { ... }
    .onFailure { _events.send(ShowMessage(it.toUiText())) }
```

Error types: `DataError.Local` (DB), `ParseError` (parsing/PDF), `AuthError`, `ProfileValidationError`. Each user-facing error gets a `.toUiText()` mapping to a string resource. The layer that owns an exception catches it and converts it.

**Naming**

| Thing | Convention | Example |
|---|---|---|
| Data source interface | `<Entity><Local/Remote>DataSource` | `SessionLocalDataSource` |
| Implementation | describes what it wraps — **never `Impl`** | `RoomSessionDataSource`, `DataStoreProfileStorage` |
| Room entity | `<Model>Entity` | `TransactionEntity` |
| Mapper | extension fun | `fun TransactionEntity.toTransaction()` |
| UI model | `<Model>Ui` | `PayeeGroupUi` |
| Nav route | `<Screen>Route`, `@Serializable` | `SessionDetailRoute(sessionId)` |
| Feature nav graph | `<feature>Graph()` on `NavGraphBuilder` | `sessionsGraph(navController)` |
| Koin module | `<feature><Layer>Module` | `uploadDataModule` |

**DI (Koin):** one module per feature layer, assembled *only* in `:app` (`TransactionsParserApp.kt`). Prefer `viewModelOf(::X)` / `singleOf(::X) { bind<Y>() }`. `:app` is the only module with `BuildConfig`, so build-time values (analytics salt, OAuth client id) are injected from there.

**Navigation:** type-safe `@Serializable` routes; intra-feature navigation via the passed `NavController`; cross-feature via lambda callbacks — never import another feature's route.

**Domain purity:** `:core:domain`, `:core:parsing`, `:feature:*:domain` are pure Kotlin. No Android imports. This is what makes parsing, grouping, duplicate detection and the backup codec testable without an emulator — don't break it.

**Money and time:** amounts are **paise (`Long`)**, never floats or Doubles. Statement date-times are stored as wall-clock **as-if-UTC** epoch millis so they render identically in any timezone — always read back with `ZoneOffset.UTC` (helpers in `core/presentation/Formatters.kt`).

**Account scoping:** every method in `RoomDataSources` resolves `ownerId` through `ActiveAccountProvider` internally. ViewModels and use cases neither pass it nor filter on it.

**Payee-scoped queries:** use the `SAME_PAYEE_NAMES` predicate in `dao/Daos.kt`, never a bare `normalizedPayee = :x`. Matching on `transactions.payeeId` alone drops rows imported before that payee was mapped.

---

## 6. Data model

Room DB `transactions_parser.db` **v4**, six tables. Schema history is exported and committed under `core/database/schemas/` (`1.json`–`4.json`). The Google session, user profile, theme and dashboard preferences live in **DataStore**, not SQLite.

```
categories  (ownerId-scoped; soft delete wired)
    ↑ RESTRICT
payees  (alias + categoryId; no statement name)
    ↑ RESTRICT
payee_identifiers  (raw/normalized statement names → payeeId)
                     unique (ownerId, normalizedName)

sessions  ←CASCADE─ transactions
                      isDuplicate / duplicateOfTransactionId / isExcluded
                      payeeId? SET_NULL  (NULL = unmapped)

upload_logs  (standalone audit trail, no FK)
```

Every table carries `ownerId`. Soft-delete columns (`isDeleted` / `deletedAtMillis`) exist on the main tables; **only `CategoryDao` writes them today**. Cancelling a session flips status and leaves `isDeleted = 0`, so cancelled sessions are not in Recently deleted.

**`categories`** — `id` PK · `ownerId` · `name` (unique per owner). Deletable only when unlinked.

**`payees`** — the user's name for someone + their category. No statement name lives here anymore (#7).

**`payee_identifiers`** — one statement spelling per row. Auto-map key is `(ownerId, normalizedName)`. Merging re-points identifiers; it does not rewrite history.

**`sessions`** — one row per uploaded statement. `status`: `PENDING` / `COMPLETED` / `CANCELLED`. Starts PENDING; auto-flips to COMPLETED when zero countable transactions remain unmapped.

**`transactions`** — amounts in paise. `payeeId?` NULL means unmapped. `isDuplicate` is a system fact set at import; `isExcluded` starts equal to it and is then owned by the user. **Aggregates filter on `isExcluded`, never on `isDuplicate`.**

**`upload_logs`** — success and every failure reason, including failed uploads that never created a session.

**Changing the schema** needs all four: bump version → write `Migration` into `ALL_MIGRATIONS` → `MigrationTest` that proves data survives → `./gradlew verifyRoomMigrations`. Never add `fallbackToDestructiveMigration()`. Detail in `CLAUDE.md`.

**Notes:** enums stored as **strings**, not ordinals. No type converters — primitives only. Session summaries and dashboard numbers come from aggregate queries, not N+1 loads.

---

## 7. Parsing pipeline

```
SAF pick → validate (≤80 MB, .pdf) → copy to app cache
  → PdfBox-Android extraction (PDFTextStripper, sortByPosition = true)
      · isEncrypted / InvalidPasswordException → PASSWORD_PROTECTED
  → StatementParserRegistry.findFor(text)          ← content-based routing
      · PhonePe: "Transaction Statement for" + UTR/Txn-ID
      · GPay:    "google pay"/"transaction statement" + "UPI Transaction ID"
      · no match → UNRECOGNIZED_FORMAT (wrong-document rejection)
  → parser.parse(text) → ParsedStatement
  → DuplicateDetector flags repeats against everything the account already holds
      (issuer ref → UTR → weaker composite; never by date overlap)
  → persist as PENDING session, auto-mapping known payees by normalized identifier
  → delete temp file (finally block — never outlives parsing) → tell the user
  → log to upload_logs (success and every failure reason)
```

Orchestrated by `ImportStatementUseCase` (pure JVM, unit-tested with fakes).

**Pattern-matching strategy.** Text is **chunked per transaction** using row-date anchors — PhonePe is month-first (`Jul 03, 2026`), GPay is day-first (`09 Jun, 2026`). Within each chunk, independent regexes extract payee, amount (→ paise), time, transaction ref, UTR, and DEBIT/CREDIT. PhonePe keeps rows it cannot fully classify rather than dropping them (#24).

**Adding a new source:** implement `StatementParser` (`source`, `canParse`, `parse`) in `:core:parsing`, register it in `coreParsingModule`. One class plus one line — no feature code changes.

**Why no AI/cloud parsing:** bank-data privacy was a hard requirement. The `StatementParser` interface keeps an opt-in AI fallback possible later without touching feature code.

---

## 8. Key flows

**Import** — see §7. Session lands in **Pending**. Duplicates are flagged and start excluded; the user can re-include a false positive without erasing the detection fact.

**Mapping** (`SessionDetailViewModel`) — transactions are grouped by `normalizedPayee` via `PayeeGrouper` (distinct amounts, total, count, top-3 hours). Unmapped groups sort first. Alias field is prefilled with the parsed statement name (#40); category stays empty for new payees. Saving upserts a payee + identifier and assigns matching rows in the session.

**Auto-map** — on import, each distinct payee is looked up by normalized identifier; hits are assigned immediately. Detail screen offers "N payees recognized — Confirm all".

**Completion** — at zero unmapped countable rows the session flips to COMPLETED and becomes read-only. Partial mapping stays Pending and is resumable.

**Payee directory / detail** — everyone the account knows; detail is paged cross-session history with exclusion toggles and merge of identifiers (#5, #7).

**Home / dashboards** — one rendering engine over a widget catalog; four built-ins plus user-built dashboards; shared date range; Mapping health sits early so partial totals are not mistaken for complete ones (#31–#34). Category tap opens the insight carousel (#35). Search and anomaly callouts live off Home (#36).

**You (Settings)** — profile, categories, theme, manage/default dashboard, CSV export, backup/restore, recently deleted, logout, delete account.

---

## 9. Tech stack

Kotlin 2.2.10 · AGP 9.1.1 · compileSdk 37 / targetSdk 36 / **minSdk 26** · Compose BOM 2026.02.01 + Material 3 · Room 2.7.1 + KSP 2.3.9 · Koin 4.1.0 · Navigation Compose 2.9.0 · Paging 3.3.6 · DataStore 1.1.4 · Credential Manager 1.5.0 + googleid 1.1.1 · Firebase BOM 34.1.0 (Analytics + Crashlytics) · PdfBox-Android 2.0.27.0 · JUnit5 + AssertK + Turbine + coroutines-test.

**Decisions worth knowing:**
- **minSdk 26** so `java.time` works without desugaring.
- **compileSdk 37** forced by androidx.core 1.19.0 and lifecycle 2.11.0.
- Parsing is a pure-JVM module (`:core:parsing`) separate from PdfBox (`:core:pdf`) so parser logic is testable without a device.
- No backend exists. Network use is Google Sign-In plus Firebase SDK traffic (analytics/crashlytics). Statement PDFs never leave the device.
- Release builds run R8 + resource shrinking (#22).

---

## 10. Tests

```bash
./gradlew test verifyRoomMigrations
ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest
```

Unit tests are JUnit 5 + AssertK + Turbine, fakes over mocks. Instrumented tests are JUnit 4 and **must use camelCase method names** (backtick names fail dexing below DEX 040).

Notable suites: `:core:parsing` (PhonePe/GPay + registry), `:core:domain` (duplicate detector, backup codec), `:feature:upload:domain` (import + duplicates), `:feature:sessions:domain` (`PayeeGrouper`), `:feature:dashboard:domain` (`DashboardLayout`), ViewModel tests across features, Room `MigrationTest` / `DatabaseWiringTest` / backup restore on device.

Parser fixtures in `SampleStatements.kt` approximate real PdfBox output (PhonePe / GPay samples).

---

## 11. Known gaps and next steps

Tracked on the issues — do not treat this section as the backlog:

- **[#9](https://github.com/SandeepChorge/transactions-platform/issues/9)** — versioning, signing, Firebase App Distribution and Play internal upload are largely in; remaining phases are polish / verification of Play runs.
- **[#16](https://github.com/SandeepChorge/transactions-platform/issues/16)** — dashboard Phase 6–8-ish shipped (#29–#36); still open: budgets, recurring detection, shareable report, widgets, app lock.

**Hooks still unused:** other bank parsers · password prompt for protected PDFs · cloud sync behind the existing datasource interfaces · AI fallback on `StatementParser`.

**Cancelled sessions** are not recoverable from Recently deleted (soft delete is categories-only).

---

## 12. Monorepo

This Android tree already lives under `transactions-platform/` alongside scaffolding for web, API and a shared OpenAPI contract. Rules that matter:

- Gradle wrapper / `settings.gradle.kts` / `build-logic/` stay **inside** this folder — no Gradle build at repo root.
- Path-filtered CI (see `docs/ci.md`).
- Conventional Commits scoped by project: `feat(android): …`.
- Secrets never committed; `gradle.properties.example` is the CI source of truth for build settings.

---

## 13. File index

| Path | What |
|---|---|
| `core/parsing/…/PhonePeStatementParser.kt`, `GooglePayStatementParser.kt` | pattern matchers |
| `core/parsing/…/ParserSupport.kt` | chunking, amount/time parsing, UTC helpers |
| `core/pdf/…/PdfBoxStatementTextExtractor.kt` | PDF text extraction + password detection |
| `core/domain/…/duplicate/DuplicateDetector.kt` | import-time duplicate identity |
| `core/domain/…/backup/` | JSON backup / restore use cases |
| `core/database/…/entity/Entities.kt`, `dao/Daos.kt`, `migration/Migrations.kt` | schema |
| `core/database/…/RoomDataSources.kt` | account-scoped datasource impls |
| `core/designsystem/` | theme + chart primitives |
| `core/analytics/` | Firebase adapters |
| `feature/upload/domain/…/ImportStatementUseCase.kt` | import pipeline + auto-mapping |
| `feature/sessions/domain/…/PayeeGrouper.kt` | payee summaries |
| `feature/sessions/presentation/…/detail/SessionDetailViewModel.kt` | mapping UI |
| `feature/dashboard/domain/…/DashboardLayout.kt`, `DashboardCatalog.kt` | which dashboards, order, default |
| `feature/dashboard/presentation/` | Home, manage/builder, category insight |
| `feature/settings/presentation/` | You tab |
| `app/…/TransactionsParserApp.kt` | Koin assembly |
| `app/…/AppRoot.kt` | auth gate + bottom nav + NavHost |
| `build-logic/src/main/kotlin/` | build conventions |

---

## 14. Closed PRs that shaped this tree

All closed PRs on the repo to date were **merged** (none closed without merge). Short map:

| PR | What it did |
|---|---|
| [#2](https://github.com/SandeepChorge/transactions-platform/pull/2) | Room `exportSchema` + `verifyRoomMigrations` + logout confirm |
| [#3](https://github.com/SandeepChorge/transactions-platform/pull/3) | Schema v2: per-account `ownerId` + soft-delete columns |
| [#4](https://github.com/SandeepChorge/transactions-platform/pull/4) | Schema v3: duplicate detection + `isExcluded` |
| [#5](https://github.com/SandeepChorge/transactions-platform/pull/5) | Payee detail with paged cross-session history |
| [#6](https://github.com/SandeepChorge/transactions-platform/pull/6) | Settings, CSV export, recently deleted, sticky headers |
| [#7](https://github.com/SandeepChorge/transactions-platform/pull/7) | Schema v4: `payee_identifiers` + merge |
| [#8](https://github.com/SandeepChorge/transactions-platform/pull/8) | Agent tooling / token-hygiene chore |
| [#10](https://github.com/SandeepChorge/transactions-platform/pull/10) | CI versioning, release signing, build visibility |
| [#11](https://github.com/SandeepChorge/transactions-platform/pull/11)–[#14](https://github.com/SandeepChorge/transactions-platform/pull/14) | Release-job gating, gradle.properties parity, Firebase App Distribution + pin |
| [#17](https://github.com/SandeepChorge/transactions-platform/pull/17) | Lossless JSON backup / account-reassigning restore |
| [#18](https://github.com/SandeepChorge/transactions-platform/pull/18) | Synthetic schema-1 fixture driven through every migration |
| [#19](https://github.com/SandeepChorge/transactions-platform/pull/19)–[#21](https://github.com/SandeepChorge/transactions-platform/pull/21) | Play internal AAB upload + gem/path/pin fixes |
| [#22](https://github.com/SandeepChorge/transactions-platform/pull/22) | R8 + resource shrinking on release |
| [#24](https://github.com/SandeepChorge/transactions-platform/pull/24) | PhonePe parser keeps rows it used to drop |
| [#25](https://github.com/SandeepChorge/transactions-platform/pull/25)–[#26](https://github.com/SandeepChorge/transactions-platform/pull/26) | Design artboards → Compose theme |
| [#27](https://github.com/SandeepChorge/transactions-platform/pull/27) | CI actually runs `verifyRoomMigrations` |
| [#28](https://github.com/SandeepChorge/transactions-platform/pull/28) | Commit dashboard design artboards |
| [#29](https://github.com/SandeepChorge/transactions-platform/pull/29)–[#34](https://github.com/SandeepChorge/transactions-platform/pull/34) | Chart primitives → aggregates → Home catalog → shell/Payees tab → manage/custom dashboards |
| [#32](https://github.com/SandeepChorge/transactions-platform/pull/32) | Fix "Other"/uncategorised chart slice |
| [#35](https://github.com/SandeepChorge/transactions-platform/pull/35) | Category insight carousel |
| [#36](https://github.com/SandeepChorge/transactions-platform/pull/36) | Global search + anomaly callouts |
| [#37](https://github.com/SandeepChorge/transactions-platform/pull/37) | Analytics, Crashlytics, Statement Sense rename + icon |
| [#38](https://github.com/SandeepChorge/transactions-platform/pull/38) | Privacy policy, Play listing assets, APP_OVERVIEW |
| [#39](https://github.com/SandeepChorge/transactions-platform/pull/39) | Delete-account flow + docs page |
| [#40](https://github.com/SandeepChorge/transactions-platform/pull/40) | Prefill parsed payee name + clear action on mapping |

Issue #1 (V2 improvements, phases 0–5) is closed; #9 and #16 remain the live backlogs.
