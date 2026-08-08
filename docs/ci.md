# CI structure

Single workflow, `.github/workflows/build.yml`. A `changes` job uses `dorny/paths-filter` to
detect which project(s) a push/PR touches (markdown-only changes don't count), then only the
matching job (`android` / `web` / `api`) runs.

This is the same idea as larger multi-project monorepos (a routing table of paths → projects,
markdown exempted, only affected lanes run) scaled down for three projects. No shared-lib fanout,
E2E parent/leaf gating, or review-deploy aggregation yet — add those if/when the project count and
shared-code surface grow enough to need them.

**Required repo secret:** `GOOGLE_WEB_CLIENT_ID` — the Android job writes it into
`transactions-parser-android/gradle.properties` before building, since that file is gitignored.
Add it under repo Settings → Secrets and variables → Actions.
