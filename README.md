# Transactions Platform

Monorepo for the transactions product: Android app, web portal, and backend API.

```
transactions-platform/
├── transactions-parser-android/   Android app — parses PhonePe/GPay statement PDFs on-device.
│                                  Self-contained Gradle build (own wrapper, settings.gradle.kts,
│                                  build-logic). See its own README.md for full architecture.
├── transactions-parser-web/       Web portal (not yet scaffolded).
├── services/
│   └── transactions-api/          Backend API (not yet scaffolded).
├── shared/
│   └── api-contract/              OpenAPI spec both web and Android generate clients from.
├── docs/                          Cross-project docs.
└── .github/workflows/             Path-filtered CI — see docs below.
```

## Why a monorepo

Android, web, and API will share an API contract and evolve together. Keeping them in one repo
makes cross-cutting changes (e.g. an API field rename touching both clients) a single PR instead
of a coordination problem across repos.

## Rules that matter

- **No Gradle build at repo root.** The Android project stays fully self-contained under
  `transactions-parser-android/` — its own wrapper, `settings.gradle.kts`, `build-logic/`.
- **Path-filtered CI.** A change to one project should not trigger builds for the others.
  See `.github/workflows/build.yml`.
- **Independent, tag-prefixed versioning** per project (`android/v1.2.0`, `api/v0.4.1`,
  `web/v0.3.0`) since app-store and backend release cadences differ.
- **Conventional Commits scoped by project**: `feat(android): …`, `fix(api): …`, `feat(web): …`.
- **Secrets never committed.** Each project's `.gitignore` excludes local secret files
  (e.g. `transactions-parser-android/gradle.properties` holds a Google OAuth client ID —
  copy `gradle.properties.example` and fill in real values locally; CI injects it from a
  repo secret).

## Getting started

See each project's own README:
- [`transactions-parser-android/README.md`](transactions-parser-android/README.md)
