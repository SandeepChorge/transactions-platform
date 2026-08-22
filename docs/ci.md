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

## Distribution

Release runs upload to Firebase App Distribution when the `workflow_dispatch` `channel` input is
`firebase` or `both`. Two more secrets are involved:

| Secret | What it is |
| --- | --- |
| `FIREBASE_APP_ID` | The `mobilesdk_app_id` from `google-services.json` — `1:…:android:…`. Not sensitive; a secret only for consistency. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | The full JSON key for a service account holding **Firebase App Distribution Admin** and nothing else. Genuinely sensitive — it can publish builds to testers. |

The tester group is targeted by its **alias** (`testers`), which is not the same field as its
display name. A wrong alias fails as "group not found"; it does not silently upload to nobody.

The upload uses `firebase-tools` through `npx` rather than a third-party action, because this job
has already handled the signing key by the time it runs and every extra action is another
dependency with access to that path. **It is currently unpinned** — the version is printed at the
start of the step, and should be pinned to that exact value once a run has confirmed it works.
Unpinned means a broken upstream release can break releases without any change on our side.

Unlike `gradle.properties`, `google-services.json` is **committed**. Its contents ship inside every
APK and the API key in it is restricted by package name plus signing fingerprint, so treating it as
a secret would buy nothing and add a second file for CI to synthesise — which is exactly the
mechanism that caused the metaspace failure above.

Play Store upload is not wired yet (issue #9, Phase 6). A dispatch with `channel: play` or `both`
builds and signs normally and reports in the job summary that Play distribution is unavailable,
rather than skipping silently.
