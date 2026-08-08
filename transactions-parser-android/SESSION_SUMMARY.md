# Transactions Parser — Session Summary

**Date:** 13 July 2026 · **Project:** `TransactionsParser` (Android Studio) · **Package:** `com.madtitan94.transactionsparser`

---

## 1. What is this about?

An Android app that removes the boring, manual part of expense tracking. Instead of typing daily expense entries, the user uploads a **PhonePe or Google Pay transaction statement PDF**, and the app:

1. Parses every transaction **entirely on-device** (nothing is ever uploaded — the PDF contains bank data, so privacy was a hard requirement).
2. Groups transactions by payee with a smart summary — repetitive amounts (e.g. ₹10, ₹20), session total, entry count, and typical times of day (the "cold coffee from abc shop at ~1 PM" example from the spec).
3. Lets the user map each statement payee to an **alias they recognize** plus a **category** (Food, Bills, …).
4. **The game changer:** mappings are remembered. On the next month's upload, known payees are auto-mapped and confirmed with one tap — only genuinely new payees need manual work.

### V1 feature checklist (all implemented)

| Spec item | Status |
|---|---|
| Google login (fast registration) | ✅ Credential Manager + Sign in with Google |
| Profile: name (mandatory), mobile (optional, validated), email (read-only), gender (optional) | ✅ View/edit with validation |
| PDF upload, 80 MB limit | ✅ SAF picker, size + extension checks |
| Reject password-protected PDFs | ✅ Detected by PdfBox, specific error popup |
| Parse PhonePe + Google Pay statements | ✅ On-device pattern matching |
| Reject wrong documents with clear error | ✅ Content-based parser routing; `UNRECOGNIZED_FORMAT` / `NO_TRANSACTIONS` errors |
| Temp storage + immediate deletion + user notification | ✅ Copy to cache → parse → delete in `finally` → "document deleted" notice |
| Sessions with Pending / Completed / Cancelled tabs | ✅ Room-backed, resumable |
| Payee grouping with amounts/total/count/typical times | ✅ `PayeeGrouper` (unit-tested) |
| Alias + category mapping, per-payee save, new-category inline | ✅ Mapping screen |
| Resume partially mapped sessions | ✅ Session stays Pending until every transaction is mapped |
| Auto-map known payees on re-upload, one-tap confirm | ✅ "N payees recognized — Confirm all" banner |
| Category screen: edit always, delete only when unlinked | ✅ UI + ViewModel re-check + DB `RESTRICT` FK |
| Upload history log (success + failure) | ✅ Every attempt logged with reason |
| Local, scalable storage | ✅ Room, 5 entities |

---

## 2. How we implemented it (session log)

1. **Clarified decisions up front:** multi-module structure, fully on-device parsing (no AI/cloud fallback in V1), Google Sign-In wired with a placeholder client ID, full V1 build in one session.
2. **Foundation:** rewrote `gradle/libs.versions.toml` (verified Koin 4.1.0, PdfBox-Android 2.0.27.0, KSP 2.3.9 against Maven Central), added `build-logic` with 5 convention plugins, registered 18 modules in `settings.gradle.kts`.
3. **Core modules:** typed `Result<T,E>` error system → domain models → parser contracts → Room database → shared UI utilities → design system → PdfBox extractor.
4. **Features:** auth → profile → upload (import pipeline) → sessions (tabs + mapping) → categories.
5. **App shell:** auth-gated root, bottom navigation (Statements / Upload / Categories / Profile), NavHost assembling feature graphs, `startKoin` with 13 DI modules.
6. **Verification:** parser regex logic ported to Python and executed against fixtures of the two real sample statements — PhonePe 5/5 rows, GPay 2/2 rows, amounts, times, periods, and cross-rejection all passed. Static sweep found and fixed one real bug (a ViewModel flow collected forever instead of once).
7. **Build fixes after first sync:** added the missing `org.jetbrains.kotlin:kotlin-serialization` gradle-plugin artifact to build-logic; bumped compileSdk 36 → 37 (required by androidx.core 1.19.0 / lifecycle 2.11.0).
8. **Google auth setup:** GCP project `transactionparser-502216` created; web client ID confirmed present in `gradle.properties`.

**Stats:** 18 Gradle modules, ~69 Kotlin files, 5 test suites.

---

## 3. Architecture & modularization

**Pattern:** Clean Architecture with feature-layered modularization — split by feature first, then by layer (`presentation → domain ← data`). Features never depend on each other; anything shared lives in `core`. MVI in the presentation layer.

```
:app                              auth gate, bottom bar, NavHost, startKoin
:build-logic                      Gradle convention plugins (see below)

:core:domain          (pure JVM)  Result<T,E>, Error/DataError, domain models,
                                  parser contracts, datasource interfaces
:core:presentation                UiText, ObserveAsEvents, error→UiText mapping, ₹/date formatters
:core:designsystem                TransactionsParserTheme + shared components
:core:database                    Room: 5 entities, 5 DAOs, datasource impls, mappers
:core:parsing         (pure JVM)  PhonePeStatementParser, GooglePayStatementParser, registry
:core:pdf                         PdfBox-Android text extractor

:feature:auth:data                DataStore session storage
:feature:auth:presentation        Login screen, GoogleCredentialHelper, BuildConfig client ID
:feature:profile:domain (JVM)     UserProfile, Gender, ProfileValidator
:feature:profile:data             DataStore profile storage
:feature:profile:presentation     Profile view/edit screen
:feature:upload:domain  (JVM)     ImportStatementUseCase (the import pipeline)
:feature:upload:data              ContentResolver file datasource (SAF, cache copy, delete)
:feature:upload:presentation      Upload screen, validation, dialogs
:feature:sessions:domain (JVM)    PayeeGrouper (grouping/summary logic)
:feature:sessions:presentation    History tabs, mapping screen, upload history
:feature:categories:presentation  Category CRUD
```

**Convention plugins** (`build-logic/src/main/kotlin/`): `transactionsparser.android.library` (compileSdk 37, minSdk 26, JUnit5), `transactionsparser.compose` (library + Compose), `transactionsparser.android.feature` (compose + Koin + navigation + serialization + core deps), `transactionsparser.jvm.library` (pure Kotlin, JVM 11), `transactionsparser.room` (Room + KSP). All versions in the `libs` version catalog.

**Key conventions per layer:**

- **Presentation (MVI):** every screen = `State` data class + `Action` sealed interface + `Event` channel + ViewModel (`StateFlow`, `.update {}`); Root composable (owns `koinViewModel()`, observes events) / Screen composable (pure `state` + `onAction`, previewable). Type-safe navigation with `@Serializable` routes, one nav graph per feature, cross-feature navigation via callbacks.
- **Domain:** pure Kotlin, no Android imports. Interfaces for everything a ViewModel touches.
- **Data:** entity/DTO ↔ domain mappers as extension functions; implementations named for what they are (`RoomSessionDataSource`, `DataStoreSessionStorage`, `ContentResolverStatementFileDataSource`) — never `Impl`.
- **Errors:** no exceptions for expected failures — `Result<T, E: Error>` everywhere (`DataError.Local`, `ParseError`, `AuthError`, `ProfileValidationError`), mapped to user strings via `toUiText()`.
- **DI:** Koin — one module per feature layer, assembled only in `:app` (13 modules).

**Database schema (Room):** `categories` ←(RESTRICT)— `payees` (unique `normalizedName` — the auto-map key) ←(SET_NULL)— `transactions` —(CASCADE)→ `sessions`; plus `upload_logs`. Money stored as **paise (Long)**; statement timestamps stored as wall-clock-as-UTC epoch millis so they render identically in any timezone.

---

## 4. PDF parsing & pattern matching

**Pipeline** (`ImportStatementUseCase`):

```
SAF pick → validate (≤80 MB, .pdf) → copy to app cache
  → PdfBox-Android (com.tom-roush:pdfbox-android 2.0.27.0) text extraction
      · isEncrypted / InvalidPasswordException → PASSWORD_PROTECTED
      · PDFTextStripper with sortByPosition = true
  → StatementParserRegistry.findFor(text)   ← content-based routing
      · PhonePe:  "Transaction Statement for" + UTR/Txn-ID pattern
      · GPay:     "google pay"/"transaction statement" + "UPI Transaction ID"
      · no match → UNRECOGNIZED_FORMAT (wrong-document rejection)
  → parser.parse(text) → ParsedStatement
  → persist as PENDING session (+ auto-map known payees by normalizedName)
  → delete temp file (finally-block — never outlives parsing) → notify user
  → log to upload_logs (success and every failure reason)
```

**Pattern-matching strategy** (pure Kotlin regex, `:core:parsing`, fully unit-testable on JVM):

- Text is **chunked per transaction** by row-date anchors (PhonePe: `Jul 03, 2026` month-first; GPay: `09 Jun, 2026` day-first — the difference also prevents header/period lines from matching).
- Within each chunk, independent regexes pull payee (anchor `Paid to`/`Received from` up to stop-tokens like `DEBIT`, `₹`, newline, `UPI Transaction ID`), amount (`₹1,234.56` → paise), time (`h:mm AM/PM`), transaction ID (`T\d+` / UPI id), UTR, and DEBIT/CREDIT type. Order-tolerant within a chunk, so it survives PdfBox extraction-order quirks.
- Statement period extracted separately (GPay uses full month names, PhonePe short).
- Payee dedup key: `normalizePayee()` = trim + collapse whitespace + uppercase.

**Why no AI/cloud:** bank data privacy — the spec explicitly preferred on-device. The `StatementParser` interface keeps the door open for an *opt-in* AI fallback later without touching any feature code.

**Verification:** regex logic was executed against fixtures replicating the two real sample statements (PhonePe Jul-2026: 5 rows/₹154 total; GPay Jun-2026: MSEDCL ₹2,580 + Blinkit ₹205) — all fields matched, cross-parser rejection confirmed. Kotlin unit tests encode the same cases. *Caveat:* fixtures approximate PdfBox output; after the first real-PDF run, adjust fixtures if extraction order differs.

---

## 5. How this project scales

**Already designed in:**

- **New statement sources (V2 bank statements):** implement `StatementParser`, register in `coreParsingModule` — one class + one line; nothing else changes.
- **Password-protected PDFs (V2):** extractor already distinguishes `PASSWORD_PROTECTED`; add a password prompt and `PDDocument.load(file, password)`.
- **Cloud sync (V2):** all persistence is behind interfaces in `:core:domain` with Room as the single source of truth — add remote datasources + a WorkManager sync job (offline-first pattern) without touching ViewModels. Add a `:core:data` module with the Ktor `HttpClientFactory` + `safeCall` helpers when a backend appears.
- **Team scaling:** features are isolated modules with enforced dependency rules — parallel work without conflicts; convention plugins keep new modules to ~5-line build files; per-feature Koin modules and nav graphs mean features plug in, not wire in.
- **Larger PDFs:** the 80 MB cap is a single constant (`UploadViewModel.MAX_FILE_SIZE_BYTES`); for very large statements, move import into WorkManager with progress notifications.
- **Testability as scale insurance:** parsing, grouping, and the import pipeline are pure JVM — fast tests, no emulator.

**Recommended next steps:** spend/category analytics dashboards (data model already supports it), export (CSV/xlsx), R8/ProGuard rules + release signing, per-payee merge/rename tooling, and a Room `AutoMigration` policy once the schema changes.

---

## 6. Other necessary information

**Tech stack:** Kotlin 2.2.10 · AGP 9.1.1 (built-in Kotlin) · Gradle 9.3.1 · Compose (BOM 2026.02.01, Material 3) · Room 2.7.1 + KSP 2.3.9 · Koin BOM 4.1.0 · Navigation Compose 2.9.0 (type-safe) · DataStore · Credential Manager 1.5.0 + googleid 1.1.1 · PdfBox-Android 2.0.27.0 · JUnit5 + AssertK + Turbine + coroutines-test.

**Deliberate decisions:**
- `minSdk` raised 24 → 26 (java.time without desugaring) — flag if 24 is required.
- `compileSdk` 37 (forced by androidx.core 1.19.0); `targetSdk` 36.
- Parsing modules are JVM-pure and separated from the Android PdfBox module specifically so the core logic is testable and portable.

**Google Sign-In setup status:** GCP project `transactionparser-502216`; web client ID present in `gradle.properties` ✓. Remaining to verify in [Credentials](https://console.cloud.google.com/apis/credentials?project=transactionparser-502216): an **Android** OAuth client (package `com.madtitan94.transactionsparser` + debug SHA-1 from `./gradlew signingReport`) and your account under consent-screen **Test users**. Release builds later need the release-keystore SHA-1 as a second Android client.

**Tests** (`./gradlew test`): PhonePe/GPay parser suites + registry routing (`:core:parsing`), payee grouping incl. the spec's abc-shop example (`:feature:sessions:domain`), full import pipeline incl. auto-mapping and failure logging (`:feature:upload:domain`), categories ViewModel incl. delete-blocking (`:feature:categories:presentation`).

**Known open items:**
1. First run against a **real** statement PDF may need parser-fixture/regex tuning (extraction order).
2. Sign-in requires a Play-services device with a signed-in Google account matching a consent-screen test user.
3. androidx versions were pinned without live metadata access — accept Studio's upgrade suggestions freely.

**Key files to know:**
- `core/parsing/…/PhonePeStatementParser.kt` / `GooglePayStatementParser.kt` — the pattern matchers
- `feature/upload/domain/…/ImportStatementUseCase.kt` — the import pipeline + auto-mapping
- `feature/sessions/domain/…/PayeeGrouper.kt` — payee summaries
- `feature/sessions/presentation/…/detail/SessionDetailViewModel.kt` — mapping/confirm-all/auto-complete logic
- `build-logic/src/main/kotlin/` — build conventions
- `README.md` — setup instructions
