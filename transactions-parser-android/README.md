# Transactions Parser

Android app that turns PhonePe / Google Pay transaction statement PDFs into categorized expenses — parsed **entirely on-device**, because statements contain bank data and nothing may leave the phone.

> **Reading this as an AI assistant picking up the project?** This README is the authoritative context. Sections 4–6 (architecture, conventions, data model) are *rules*, not descriptions — follow them when generating code so new work matches what exists. Section 11 lists open work.

---

## 1. The product idea

Manually entering daily expenses is tedious, so people stop doing it. Instead: upload a statement PDF once a month, and the app extracts every transaction, groups it by payee, and learns your naming.

**The game changer:** payee mappings persist. The first upload asks you to map `AWDHOOT SNACKS CENTRE` → alias "Morning tea stall" → category "Food". Every future upload recognizes that payee automatically and needs one confirmation tap. Only new payees require work, so effort drops to near-zero after month one.

**Example of the payee summary the app produces:**

> **abc shop** — amounts ₹10, ₹20 · total ₹30 · 2 entries · usually around ≈1 PM, ≈4 PM

---

## 2. Current status

**V1 is feature-complete and code-complete.** 18 Gradle modules, ~69 Kotlin files, 5 test suites. Not yet run against a real PDF on a device — see §11.

| Spec requirement | Status |
|---|---|
| Google login | ✅ Credential Manager + Sign in with Google |
| Profile: name (required), mobile (optional, validated), email (read-only), gender (optional) | ✅ |
| PDF upload, 80 MB limit | ✅ SAF picker + size/extension checks |
| Reject password-protected PDFs | ✅ Detected by PdfBox → specific error dialog |
| Parse PhonePe + Google Pay statements | ✅ On-device regex parsers |
| Reject wrong documents with clear error | ✅ Content-based routing → `UNRECOGNIZED_FORMAT` |
| Temp storage, immediate deletion, tell the user | ✅ Deleted in a `finally` block; dialog confirms |
| Sessions: Pending / Completed / Cancelled tabs | ✅ |
| Payee grouping (amounts, total, count, typical times) | ✅ `PayeeGrouper`, unit-tested |
| Alias + category mapping, save per payee | ✅ |
| Resume partially mapped sessions | ✅ Stays Pending until fully mapped |
| Auto-map known payees, one-tap confirm | ✅ "N payees recognized — Confirm all" |
| Categories: edit always, delete only when unlinked | ✅ UI + ViewModel + DB `RESTRICT` |
| Upload history (success and failure) | ✅ |
| Local scalable DB | ✅ Room, 5 tables |

**Deferred to V2 (hooks already in place):** bank statements, password-protected PDFs, cloud sync, AI-assisted parsing fallback.

---

## 3. Setup

**Prerequisites:** Android Studio with SDK 37, JDK 21 (Gradle toolchain), a device/emulator with Google Play services.

**Google Sign-In** — GCP project `transactionparser-502216` exists and the web client ID is already in `gradle.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=368689223649-....apps.googleusercontent.com
```

Still verify in [GCP Credentials](https://console.cloud.google.com/apis/credentials?project=transactionparser-502216):
1. An **Android** OAuth client exists — package `com.madtitan94.transactionsparser` + debug SHA-1 from `./gradlew signingReport`. It's never referenced in code, but sign-in fails without it.
2. Your Google account is listed under consent-screen **Test users**.

Release builds later need the release keystore's SHA-1 as a second Android client. Use the **Web** client ID in `setServerClientId` — passing the Android one is the most common sign-in failure.

`gradle.properties` holding the client ID must stay gitignored.

**Build:** `./gradlew assembleDebug` · **Test:** `./gradlew test`

---

## 4. Architecture

Clean Architecture with **feature-layered modularization** — split by feature first, then by layer. `presentation → domain ← data`; domain depends on nothing. **Features never depend on each other**; anything shared moves to `core`. MVI in the presentation layer.

```
:app                              auth gate, bottom bar, NavHost, startKoin
:build-logic                      Gradle convention plugins

:core:domain          (pure JVM)  Result<T,E>, DataError, domain models,
                                  parser contracts, datasource interfaces
:core:presentation                UiText, ObserveAsEvents, error→UiText, ₹/date formatters
:core:designsystem                theme + shared composables
:core:database                    Room: entities, DAOs, datasource impls, mappers
:core:parsing         (pure JVM)  PhonePe/GPay parsers + registry
:core:pdf                         PdfBox-Android text extractor

:feature:auth:data                DataStore session storage
:feature:auth:presentation        login screen, GoogleCredentialHelper
:feature:profile:domain (JVM)     UserProfile, Gender, ProfileValidator
:feature:profile:data             DataStore profile storage
:feature:profile:presentation     profile view/edit
:feature:upload:domain  (JVM)     ImportStatementUseCase ← the import pipeline
:feature:upload:data              SAF file access, cache copy, delete
:feature:upload:presentation      upload screen, validation, dialogs
:feature:sessions:domain (JVM)    PayeeGrouper
:feature:sessions:presentation    history tabs, mapping screen, upload history
:feature:categories:presentation  category CRUD
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

**DI (Koin):** one module per feature layer, assembled *only* in `:app` (`TransactionsParserApp.kt`, 13 modules). Prefer `viewModelOf(::X)` / `singleOf(::X) { bind<Y>() }`.

**Navigation:** type-safe `@Serializable` routes; intra-feature navigation via the passed `NavController`; cross-feature via lambda callbacks — never import another feature's route.

**Domain purity:** `:core:domain`, `:core:parsing`, `:feature:*:domain` are pure Kotlin. No Android imports. This is what makes parsing and grouping testable without an emulator — don't break it.

**Money and time:** amounts are **paise (`Long`)**, never floats or Doubles. Statement date-times are stored as wall-clock **as-if-UTC** epoch millis so they render identically in any timezone — always read back with `ZoneOffset.UTC` (helpers in `core/presentation/Formatters.kt`).

---

## 6. Data model

Room DB `transactions_parser.db` v1, five tables. The Google session and user profile live in **DataStore** (`session_store`, `user_profile_store`), not SQLite.

```
categories
    ↑ RESTRICT (can't delete a category that has payees)
payees  (unique normalizedName ← the auto-map key)
    ↑ SET_NULL
transactions ──CASCADE──→ sessions

upload_logs (standalone audit trail, no FK)
```

**`categories`** — `id` PK, `name` (unique index). Deletable only when unlinked.

**`payees`** — the persistent mapping that makes re-uploads fast.
`id` PK · `rawName` (as printed) · `normalizedName` (**unique**, = trim + collapse whitespace + uppercase — the dedup/auto-map key) · `alias` (user's own name) · `categoryId` FK `RESTRICT`.

**`sessions`** — one row per uploaded statement.
`id` PK · `fileName` · `source` (`PHONEPE`/`GOOGLE_PAY`) · `uploadedAtMillis` · `periodStartMillis?` · `periodEndMillis?` · `status` (`PENDING`/`COMPLETED`/`CANCELLED`). Starts PENDING; auto-flips to COMPLETED when zero transactions remain unmapped.

**`transactions`** — every extracted line.
`id` PK · `sessionId` FK CASCADE (indexed) · `dateTimeUtcMillis` · `rawPayee` · `normalizedPayee` (indexed) · `amountPaise` · `type` (`DEBIT`/`CREDIT`) · `transactionRef?` (PhonePe `T…` / GPay UPI id) · `utr?` (PhonePe only) · `payeeId?` FK SET_NULL (indexed) — **NULL means unmapped**, and this single field drives mapping progress and session completion.

**`upload_logs`** — `id` PK · `fileName` · `uploadedAtMillis` · `success` · `source?` · `failureReason?` (`ParseError` name) · `sessionId?`. No FK, so failed uploads — which never create a session — are still logged.

**Session summaries** come from one aggregate query, so tabs need no N+1 lookups; `COUNT(t.payeeId)` skips NULLs, giving mapped-vs-total in a single pass.

**Notes:** enums stored as **strings**, not ordinals (reordering can't corrupt data). No type converters — primitives only, which keeps future cloud serialization trivial. `exportSchema = false` today; turn it on and adopt `AutoMigration` before the first schema change ships.

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
  → persist as PENDING session, auto-mapping known payees by normalizedName
  → delete temp file (finally block — never outlives parsing) → tell the user
  → log to upload_logs (success and every failure reason)
```

Orchestrated by `ImportStatementUseCase` (pure JVM, fully unit-tested with fakes).

**Pattern-matching strategy.** Text is **chunked per transaction** using row-date anchors — PhonePe is month-first (`Jul 03, 2026`), GPay is day-first (`09 Jun, 2026`), which also stops header/period lines from matching. Within each chunk, independent regexes extract payee (anchor `Paid to`/`Received from` up to stop-tokens: `DEBIT`, `₹`, newline, `UPI Transaction ID`, `Paid by`), amount (`₹1,234.56` → paise), time (`h:mm AM/PM`), transaction ref, UTR, and DEBIT/CREDIT. Being chunk-scoped and order-independent makes it tolerant of PdfBox extraction quirks.

**Adding a new source (V2 bank statements):** implement `StatementParser` (`source`, `canParse`, `parse`) in `:core:parsing`, register it in `coreParsingModule`. One class plus one line — no feature code changes.

**Why no AI/cloud parsing:** bank-data privacy was a hard requirement. The `StatementParser` interface keeps an opt-in AI fallback possible later without touching feature code.

---

## 8. Key flows

**Import** — see §7. Session lands in **Pending**.

**Mapping** (`SessionDetailViewModel`) — transactions are grouped by `normalizedPayee` via `PayeeGrouper`, which computes distinct amounts, total, count, and the top-3 most frequent hours. Unmapped groups sort first, then by spend. Each group gets an alias field + category dropdown (with inline "New category…") and a Save button. Saving upserts a `Payee` and assigns `payeeId` to every matching transaction in the session.

**Auto-map** — on import, each distinct payee is looked up by `normalizedName`; hits are assigned immediately. The detail screen shows "N payees recognized — Confirm all" for one-tap bulk confirmation. Known payees also pre-fill alias/category so a single Save confirms them individually.

**Completion** — after any assignment, unmapped count is re-checked; at zero the session flips to COMPLETED and becomes read-only. Partial mapping simply stays Pending and is resumable anytime.

---

## 9. Tech stack

Kotlin 2.2.10 · AGP 9.1.1 · Gradle 9.3.1 · compileSdk 37 / targetSdk 36 / **minSdk 26** · Compose BOM 2026.02.01 + Material 3 · Room 2.7.1 + KSP 2.3.9 · Koin 4.1.0 · Navigation Compose 2.9.0 · DataStore 1.1.4 · Credential Manager 1.5.0 + googleid 1.1.1 · PdfBox-Android 2.0.27.0 · JUnit5 + AssertK + Turbine + coroutines-test.

**Decisions worth knowing:**
- **minSdk 24 → 26** so `java.time` works without core-library desugaring. Revert by adding desugaring to the convention plugins if 24 is required.
- **compileSdk 37** forced by androidx.core 1.19.0 and lifecycle 2.11.0.
- Parsing split into a pure-JVM module (`:core:parsing`) separate from the Android PdfBox module (`:core:pdf`) specifically so parser logic is testable and portable.
- No backend exists. The only network call in the app is Google Sign-In.

---

## 10. Tests

`./gradlew test` — JUnit5, AssertK, Turbine, fakes over mocks.

| Suite | Covers |
|---|---|
| `:core:parsing` — PhonePe/GPay parser tests + registry | field extraction, amounts, times, periods, cross-parser rejection, junk-document rejection |
| `:feature:sessions:domain` — `PayeeGrouperTest` | grouping, distinct amounts, typical times, sort order, known-payee suggestions (includes the spec's abc-shop example) |
| `:feature:upload:domain` — `ImportStatementUseCaseTest` | success path, auto-mapping, unrecognized document, password-protected, storage failure, upload logging |
| `:feature:categories:presentation` — `CategoriesViewModelTest` | add, validation, delete blocking, delete confirmation |

Parser fixtures in `SampleStatements.kt` replicate the two real sample statements (PhonePe Jul-2026: 5 rows/₹154; GPay Jun-2026: MSEDCL ₹2,580 + Blinkit ₹205). The regex logic was additionally verified by executing an equivalent implementation against these fixtures — all fields matched.

---

## 11. Known gaps and next steps

**Must do before trusting it**
1. **Run a real statement PDF through it.** Fixtures approximate PdfBox output; if actual extraction order differs, tune the fixtures/regexes in `:core:parsing`. Parsers are deliberately chunk-based to minimize this risk.
2. **Verify Google Sign-In end to end** (see §3) — needs Play services and a test-user account.

**Known hole: no duplicate-import detection.** `transactionRef`/`utr` are stored but unused. Re-uploading the same statement creates a second session with duplicate transactions and double-counted payee groups. Recommended fix: before creating a session, check incoming `transactionRef`s against the DB; on significant overlap ask "This statement looks already imported on <date>. Import anyway?" Plus a non-unique index on `transactionRef` for lookup speed. (A unique index + `OnConflictStrategy.IGNORE` is simpler but silently drops rows and misses null refs.)

**Other polish:** R8/ProGuard rules + release signing config; per-payee merge/rename tooling; Room `AutoMigration` policy; analytics/spend dashboards (the data model already supports them); CSV/xlsx export.

**V2 roadmap with existing hooks:** bank statement parsers (§7) · password-protected PDFs (extractor already distinguishes `PASSWORD_PROTECTED`; add a prompt and `PDDocument.load(file, password)`) · cloud sync (persistence is behind `:core:domain` interfaces with Room as single source of truth — add remote datasources + a WorkManager sync job, offline-first, without touching ViewModels; add a `:core:data` module with Ktor `HttpClientFactory` + `safeCall` helpers when a backend exists) · KMP (`:core:domain` and `:core:parsing` are already Android-free and could be shared with iOS or a JVM backend).

---

## 12. Repository / monorepo plan

This project will move under a `transactions-platform` monorepo alongside a web client and backend services:

```
transactions-platform/
├── transactions-parser-android/   ← this project, self-contained Gradle build
├── transactions-parser-web/
├── services/transactions-api/
├── shared/api-contract/           ← OpenAPI spec both clients generate from
├── docs/
└── .github/workflows/
```

Rules that matter: keep the Gradle wrapper, `settings.gradle.kts`, and `build-logic/` inside the Android folder (no Gradle build at repo root); **path-filtered CI** per project or every change triggers a full Android build; **independent tag-prefixed versioning** (`android/v1.2.0`, `api/v0.4.1`) since app-store and backend cadences differ; derive `versionCode` from `GITHUB_RUN_NUMBER` rather than hand-maintaining it; Conventional Commits scoped by project (`feat(android): …`).

---

## 13. File index

| Path | What |
|---|---|
| `core/parsing/…/PhonePeStatementParser.kt`, `GooglePayStatementParser.kt` | the pattern matchers |
| `core/parsing/…/ParserSupport.kt` | chunking, amount/time parsing, UTC helpers |
| `core/pdf/…/PdfBoxStatementTextExtractor.kt` | PDF text extraction + password detection |
| `feature/upload/domain/…/ImportStatementUseCase.kt` | import pipeline + auto-mapping |
| `feature/upload/presentation/…/UploadViewModel.kt` | validation, temp-file lifecycle, dialogs |
| `feature/sessions/domain/…/PayeeGrouper.kt` | payee summaries |
| `feature/sessions/presentation/…/detail/SessionDetailViewModel.kt` | mapping, confirm-all, auto-complete |
| `core/database/…/entity/Entities.kt`, `dao/Daos.kt` | schema |
| `core/domain/…/util/Result.kt` | error system |
| `app/…/TransactionsParserApp.kt` | Koin assembly |
| `app/…/AppRoot.kt` | auth gate + bottom nav + NavHost |
| `build-logic/src/main/kotlin/` | build conventions |
| `SESSION_SUMMARY.md` | narrative log of the build session |
