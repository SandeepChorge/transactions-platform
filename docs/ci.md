# CI structure

Single workflow, `.github/workflows/build.yml`. A `changes` job uses `dorny/paths-filter` to
detect which project(s) a push/PR touches, then only the matching job (`android` / `web` / `api`)
runs.

Markdown-only changes are **not** exempt. The filters used to carry `!**/*.md` exclusions, but
paths-filter ORs its patterns rather than subtracting negations, so `!web/**/*.md` matched every
file that is not markdown under `web/` — including every Android source file. All three filters
returned true on every run. The exclusions were dropped rather than reworked: a docs change now
runs that project's tests, which is cheap, and far cheaper than a filter that quietly does nothing.

This is the same idea as larger multi-project monorepos (a routing table of paths → projects,
markdown exempted, only affected lanes run) scaled down for three projects. No shared-lib fanout,
E2E parent/leaf gating, or review-deploy aggregation yet — add those if/when the project count and
shared-code surface grow enough to need them.

**Required repo secret:** `GOOGLE_WEB_CLIENT_ID` — `transactions-parser-android/gradle.properties`
is gitignored, so CI builds it by copying the tracked `gradle.properties.example` and substituting
this value. Add it under repo Settings → Secrets and variables → Actions.

Because of that copy, **`gradle.properties.example` is the single source of truth for build
settings.** A setting that lives only in your local `gradle.properties` does not exist in CI. This
already caused one failure: the example lacked `-XX:MaxMetaspaceSize`, so CI ran the release build
on Gradle's stock 384m metaspace and the daemon died mid-build, while the same build passed locally.
