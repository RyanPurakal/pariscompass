# Measurements

Every number here was produced by running the command shown next to it. Nothing is estimated.
If a number is not in this file, it has not been measured and should not appear on a resume.

Environment for all entries unless noted: Apple M3 Pro, 18 GB RAM, macOS (Darwin 27), OpenJDK 21.0.8, Apache Maven 3.9.11, Node 24.8.0, Docker 29.4.3, PostgreSQL 17 (postgres:17-alpine).

## Phase 0: Cleanup (2026-10-01)

### Backend test suite

| | Baseline (`hackathon-baseline`, c4c7ce4) | After Phase 0 (e0f3bcb) |
|---|---|---|
| Tests run | 7 | 20 |
| Passing | 2 | 20 |
| Errors | 5 | 0 |

Baseline command (run with no Gemini key in the environment, as CI would):

```bash
git checkout hackathon-baseline
env -u GEMINI_API_KEY -u GOOGLE_API_KEY ./mvnw test
```

Baseline failure breakdown, from `target/surefire-reports/`:
- `AiApplicationTests.contextLoads`: 1 error. `GeminiConfig` threw `IllegalStateException` because no API key was set.
- `CountryMetricsServiceTest`: 4 of 6 tests errored with Mockito `UnnecessaryStubbingException`. Shared stubs in `@BeforeEach` were unused by some tests and strict stubbing rejects that. These failures do not depend on the environment, so these tests did not pass as committed.

After-Phase-0 command (no environment changes needed; the `test` profile forces the key empty):

```bash
./mvnw clean test
```

Breakdown: `GlobalExceptionHandlerTest` 6, `AppPropertiesTest` 6, `CountryMetricsServiceTest` 6, `GeminiServiceTest` 1, `ParisCompassApplicationTests` 1.

### Frontend lint

```bash
cd frontend && npm ci && npm run lint
```

Result: 3 errors, all `no-unused-vars` in `src/App.jsx` (`mapCenter`, `mapZoom`, `selectedCoords`). They predate Phase 0 and were left in place because Phase 3 replaces `App.jsx`.

### Manual verification of config and error handling (not a performance metric)

Prod profile without secrets refuses to start, reporting both missing values in one message:

```bash
./mvnw -DskipTests package
env -u GEMINI_API_KEY -u GOOGLE_API_KEY -u CORS_ALLOWED_ORIGINS \
  SPRING_PROFILES_ACTIVE=prod PORT=18081 java -jar target/paris-compass-0.0.1-SNAPSHOT.jar
# exit code 1; "Reason: set CORS_ALLOWED_ORIGINS" and
# "Reason: GEMINI_API_KEY must be set when app.gemini.required=true"
```

Dev profile without a key starts and returns problem+json for every error path:

```bash
env -u GEMINI_API_KEY -u GOOGLE_API_KEY PORT=18081 java -jar target/paris-compass-0.0.1-SNAPSHOT.jar &
curl -i localhost:18081/api/country/XXX               # 404 COUNTRY_NOT_FOUND
curl -i localhost:18081/api/country/US1               # 400 BAD_REQUEST
curl -i -X POST localhost:18081/api/country/USA/projection   # 503 PROJECTION_UNAVAILABLE
curl -i localhost:18081/api/nope                      # 404 NOT_FOUND
curl -i -X DELETE localhost:18081/api/countries       # 405 METHOD_NOT_ALLOWED
curl -i -X OPTIONS localhost:18081/api/countries \
  -H "Origin: https://evil.example" -H "Access-Control-Request-Method: GET"   # 403
```

All six returned the status shown, and every error response had `Content-Type: application/problem+json`.

## Phase 1: Real data layer (2026-10-01)

### ETL: rows ingested and rejected (real upstream files)

Source files as downloaded on 2026-10-01 (upstream changes over time; the hash identifies exactly what was ingested):

| Source | Bytes | SHA-256 |
|---|---|---|
| OWID CO2 | 14,377,942 | `7f78e2b218ce4bb8c538bbec04fdc9a7982e8d40bff972e650df603899edd5f6` |
| OWID Energy | 9,229,369 | `266f2e2baad7975351bc9bb4aa061d22b1da9fe4c47d51d2ac6071e01e171f76` |
| OWID ERA5 temperature | 570,765 | `16911f331e174a18040715d74df1a9d779b7b8db76a57a2b9ffcd7c2aebdf0b5` |

Command (fresh database):

```bash
docker compose up -d --wait
./mvnw -DskipTests package
SPRING_PROFILES_ACTIVE=dev,etl java -jar target/paris-compass-0.0.1-SNAPSHOT.jar
```

Run 1 results (from the log summary and `etl_source_result`):

| Source | Rows read | Skipped (aggregate) | Skipped (OWID non-ISO) | Rows rejected | Values inserted | Values rejected |
|---|---|---|---|---|---|---|
| OWID CO2 | 50,411 | 7,931 | 0 | 0 | 119,474 | 36 (`OUT_OF_RANGE`) |
| OWID Energy | 23,377 | 6,112 | 0 | 60 (`UNKNOWN_ISO3`) | 17,561 | 0 |
| OWID ERA5 temperature | 18,318 | 1,548 | 172 | 0 | 16,598 | 0 |
| **Total** | **92,106** | **15,591** | **172** | **60** | **153,633** | **36** |

What was rejected (`SELECT ... FROM etl_rejection WHERE run_id = 1`):
- 60 rows: `ANT` (Netherlands Antilles), dissolved in 2010 and no longer in ISO 3166-1.
- 36 values, all `co2_per_capita_t` above 100 t/person: Sint Maarten 1950 to 1977 (28 values, max 782.7), Curacao (3), Brunei (2), Qatar (2), Kuwait 1991 (364.8). The Kuwait value is real (Gulf War oil fires), so the bound rejects one true extreme.

Resulting database: 233 countries.

| Metric | Values | Countries | Years |
|---|---|---|---|
| `co2_per_capita_t` | 22,909 | 213 | 1750 to 2024 |
| `co2_total_mt` | 23,408 | 215 | 1750 to 2024 |
| `ghg_total_mt` | 34,825 | 199 | 1850 to 2024 |
| `low_carbon_share_elec_pct` | 6,551 | 213 | 1985 to 2025 |
| `population` | 38,332 | 216 | 1750 to 2024 |
| `renewables_share_elec_pct` | 6,551 | 213 | 1985 to 2025 |
| `renewables_share_energy_pct` | 4,459 | 79 | 1965 to 2024 |
| `temperature_anomaly_c` | 16,598 | 193 | 1940 to 2025 |

Query: `SELECT metric_code, count(*), count(DISTINCT iso3), min(year), max(year) FROM observation GROUP BY 1 ORDER BY 1;`

### ETL: runtime and idempotency

Single runs, one after another, on the machine above. Durations include downloading from GitHub and ourworldindata.org over a home connection, so they vary with the network. Treat them as one sample each, not a benchmark (Phase 6 repeats this properly).

| Run | What it tests | Command | Outcome | Run duration (`etl_run`) |
|---|---|---|---|---|
| 1 | Fresh load | `SPRING_PROFILES_ACTIVE=dev,etl java -jar target/paris-compass-0.0.1-SNAPSHOT.jar` | 153,633 inserted | 5,216 ms |
| 2 | Unchanged files | same command again | all 3 sources skipped by SHA-256 | 1,198 ms |
| 3 | Forced re-ingest | same, with `APP_ETL_FORCE=true` | 0 inserted, 0 updated, 153,633 unchanged | 2,640 ms |

Per-source durations for run 1: CO2 3,695 ms, Energy 800 ms, Temperature 705 ms. Whole process including JVM startup and Flyway migration: 8.14 s wall clock (`/usr/bin/time -p`).

### Backend test suite

```bash
./mvnw clean test   # Docker must be running (Testcontainers)
```

Result: 32 tests, 0 failures, 0 errors. Breakdown: `SourceParserTest` 9, `EtlIntegrationTest` 5, `CountryMetricsServiceIntegrationTest` 4, `GlobalExceptionHandlerTest` 6, `AppPropertiesTest` 6, `GeminiServiceTest` 1, `ParisCompassApplicationTests` 1. The 6 mocked `CountryMetricsServiceTest` tests from Phase 0 were removed along with the CSV loader they tested.

Each Phase 1 commit was also checked out in a separate worktree and run with the same command: a37d8db 20 passing, 734e0de 34 passing, 901608d 32 passing.

## Phase 2: Backend features (2026-10-01)

Routes changed from `/api/country/{iso3}` to `/api/countries/{iso3}` in this phase. The commands in the Phase 0 and Phase 1 sections above are kept as they were run.

### Backend test suite

```bash
./mvnw clean test   # Docker must be running (Testcontainers)
```

Result: 89 tests, 0 failures, 0 errors.

| Test class | Tests |
|---|---|
| `scoring.AlignmentScorerTest` | 13 |
| `controller.AnalyticsApiIntegrationTest` | 11 |
| `etl.SourceParserTest` | 9 |
| `controller.ProjectionApiIntegrationTest` | 8 |
| `projection.ProjectionValidatorTest` | 8 |
| `config.AppPropertiesTest` | 6 |
| `exception.GlobalExceptionHandlerTest` | 6 |
| `service.ProjectionServiceTest` | 6 |
| `etl.EtlIntegrationTest` | 5 |
| `projection.ProjectionPromptBuilderTest` | 4 |
| `service.CountryMetricsServiceIntegrationTest` | 4 |
| `controller.OpenApiDocsIntegrationTest` | 2 |
| `projection.TrendExtrapolatorTest` | 2 |
| `ratelimit.ProjectionRateLimiterTest` | 2 |
| `ratelimit.RateLimitWebTest` | 2 |
| `ParisCompassApplicationTests` | 1 |

### Query plans on the real data (single `EXPLAIN ANALYZE` runs)

Database: the Phase 1 load (153,633 observations), after `VACUUM ANALYZE observation`.

```bash
docker compose exec -T postgres psql -U pariscompass -d pariscompass -c "EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) <query>"
```

| Query | Plan | Execution time |
|---|---|---|
| Rankings: `co2_per_capita_t`, 2024, top 50 with `rank()` and country join | Index Only Scan on `idx_observation_metric_year`, **Heap Fetches: 0**, 213 rows | 0.346 ms |
| Series: USA, 2 metrics, 1990 to 2024 | Bitmap Index Scan on `observation_pkey`, 70 rows | 0.173 ms |

These are planner timings for one execution each, not endpoint latency. Endpoint p50/p95 is measured in Phase 6.

### Live Gemini projections (single requests)

Model `gemini-2.5-flash` via google-genai 1.74.0 with `responseJsonSchema`, prompt version `p1`. Key loaded from a local `.env`; app on the dev profile against the Phase 1 database.

```bash
./mvnw -DskipTests package
(set -a; source .env; set +a; PORT=18081 java -jar target/paris-compass-0.0.1-SNAPSHOT.jar) &
curl -s -X POST localhost:18081/api/countries/USA/projection   # then DEU, IND, then USA three more times
```

| Country | Status | Attempts | Server latency (`latencyMs`) | Tokens in / out (app log) |
|---|---|---|---|---|
| USA | VALID | 1 | 16,829 ms | 1,752 / 450 |
| DEU | VALID | 1 | 14,291 ms | 1,706 / 431 |
| IND | VALID | 1 | 13,852 ms | 1,715 / 489 |

All three passed schema and semantic validation on the first attempt (3 of 3; too few to quote as a validity rate).

Repeat `POST /api/countries/USA/projection` with unchanged inputs: served from the `projection` table (`cached: true`) in 0.086 s, 0.041 s and 0.032 s (curl `time_total`, single samples). Phase 6 measures cached vs. uncached latency properly.

### Behavior verified by tests (not performance numbers)

- 5 concurrent projection requests for the same inputs cause **1** model call and **1** stored row (`ProjectionApiIntegrationTest.concurrentRequestsShareOneModelCall`, fake model with a 500 ms delay).
- Changing one observation changes the input hash and triggers a new model call; an entry older than the TTL is not reused; a fallback caused by a model failure is stored but not reused.
- A third projection request within a minute at a limit of 2 returns 429 with `Retry-After` (`RateLimitWebTest`).

### Alignment scores

Computed from the real data; the table and inputs are in [SCORING.md](SCORING.md#results-on-real-data). Command: `curl -s localhost:18081/api/countries/{ISO3}/alignment`.

## Phase 3: Frontend rebuild (2026-10-01)

### Production bundle

```bash
cd frontend && rm -rf dist && npm run build
```

Sizes as printed by Vite (minified, then gzip).

| | Before (JS, `hackathon` UI on `main` at 1a617c3) | After (TypeScript rebuild) |
|---|---|---|
| JS loaded on the first page | 366.0 KB / 115.0 KB gzip (one chunk, all views) | 422.9 KB / 137.8 KB gzip (atlas) |
| JS loaded on demand | none | Country page 11.9 KB / 4.4 KB, Compare 3.9 KB / 1.8 KB, shared charts (Recharts) 357.6 KB / 103.3 KB |
| CSS | 29.2 KB / 9.6 KB | 16.2 KB / 4.5 KB (+ 7.5 KB lazy) |
| Map data | 56 hand-entered marker coordinates in JS | 90.3 KB TopoJSON / 28.6 KB gzip, 177 country shapes |
| Fonts | system | Newsreader wght 58.1 KB + italic 64.5 KB, Atkinson Hyperlegible Next 34.0 KB (woff2, latin subset) |

The atlas page ships more JS than the old single page did (+22.8 KB gzip), mostly React Router (31.6 KB gzip) and TanStack Query (9.5 KB gzip), measured by building with one chunk per package:

| Package | gzip |
|---|---|
| react-dom | 64.9 KB |
| recharts (lazy chunk only) | 60.9 KB |
| react-router | 31.6 KB |
| app code (atlas) | 11.3 KB |
| @tanstack/query-core | 9.5 KB |
| d3-geo | 7.7 KB |

Switching Newsreader from its optical-size variable file to the weight-only file cut the two Latin files from 132.0 KB + 146.9 KB to 58.1 KB + 64.5 KB.

### Lint and types

```bash
cd frontend && npx tsc -b && npm run lint
```

Result: 0 type errors, 0 lint errors (the Phase 0 baseline had 3 `no-unused-vars` errors in `App.jsx`). TypeScript 5.9.3 in strict mode with `noUncheckedIndexedAccess`.

### Backend test suite

```bash
./mvnw clean test
```

Result: 91 tests, 0 failures, 0 errors (89 in Phase 2, plus 2 OpenAPI contract tests added for the generated frontend types).

### Manual checks in Chrome (not performance numbers)

Run against the Phase 1 database with `VITE_API_URL=http://localhost:18081 npx vite --port 5173`:

- Horizontal overflow at a 390 px viewport, measured as `document.documentElement.scrollWidth` vs `innerWidth` in a 390 px iframe: first 548 vs 386 on the atlas (the header did not fit), fixed; after the fix 386 vs 386 on the atlas, country and compare pages.
- The map exposes exactly 1 tab stop (`svg path[tabindex="0"]`); with real key presses, Tab then ArrowRight moved focus through countries alphabetically, and the focused country showed the same outline and tooltip as hover, with the matching ranking row highlighted.
- Removing a country from a 4-country comparison left the other countries' colors unchanged.
- `/country/XYZ` showed the not-found state without retrying (4xx).
- No console errors while loading the atlas, two country pages and the 404 page.

Not measured: Lighthouse or axe scores (automated accessibility tests are planned for Phase 4).

## Phase 4: Testing and CI (2026-10-01)

### Backend tests and coverage

```bash
./mvnw clean verify   # tests, JaCoCo report (target/site/jacoco), coverage gate
```

| | Before Phase 4 | After Phase 4 |
|---|---|---|
| Tests | 91 | 107 |
| Line coverage (JaCoCo) | 92.3% (886/960) | 96.1% (931/969) |
| Branch coverage | 81.8% (251/307) | 84.4% (259/307) |
| Instruction coverage | 94.0% | 96.3% |

"Before" was measured by adding JaCoCo to the Phase 3 code and running the same command before writing any new tests. The 16 new tests cover the gaps that measurement showed: the Gemini adapter (0/15 lines before), the ETL downloader's HTTP path (19/34), the ETL job runner (7/12), the Gemini bean condition, and the OpenAPI spec export.

Gate: `mvn verify` fails below 95% lines or 83% branches. Checked by temporarily setting the line minimum to 0.97, which failed with "lines covered ratio is 0.96, but expected minimum is 0.97".

### Finding: the Gemini SDK retried silently

`GeminiProjectionModelTest` runs the real SDK against a local HTTP server. With the SDK's default settings, one HTTP 503 produced **3 requests** and the test took about 20 s. That stacked with ProjectionService's own validation retry. `GeminiConfig` now sets one attempt; the same test makes exactly 1 request and the class runs in 2.7 s.

### Frontend tests and coverage

```bash
cd frontend && npx vitest run --coverage
```

| | Value |
|---|---|
| Tests | 54 in 14 files (unit 18, components 20, pages and shell 16) |
| Line coverage (V8) | 89.06% (456/512) |
| Branch coverage | 81.01% (401/495) |
| Statements / functions | 86.4% / 83.66% |
| axe-core violations (shell, atlas, country, compare) | 0 (color-contrast rule skipped in jsdom) |

The suite passed 4 consecutive local runs. Gate in `vite.config.ts`: 88% lines, 80% branches, 85% statements, 82% functions. Checked by temporarily setting lines to 95, which failed with "Coverage for lines (89.06%) does not meet global threshold (95%)". Lowest file: `LineChart.tsx` at 79.6% lines, because jsdom gives Recharts' ResponsiveContainer zero width, so plot lines are not rendered in tests (legend and table logic are covered).

### API contract check

```bash
./mvnw verify && cd frontend && npm run api:check
```

Proven to catch drift: removing `@Schema(nullable = true)` from `CountryMetrics.co2PerCapita` made `api:check` exit 1 with the diff `co2PerCapita: number | null` to `co2PerCapita: number`; after reverting it exited 0. (A first attempt that added a field broke backend compilation, so the export never ran; in CI a compile failure fails the backend job and the contract job, which depends on it, does not run.)

### CI (GitHub Actions)

First run of `.github/workflows/ci.yml`, run 36947640150 on branch `phase-4-testing-ci`, commit 332966e: **all three jobs passed** on the first attempt.

```bash
gh run view 36947640150 --json jobs -q '.jobs[] | "\(.name): \(.conclusion) \(.startedAt) -> \(.completedAt)"'
```

| Job | Result | Duration |
|---|---|---|
| Backend (build, tests, coverage) | success | 72 s (00:46:09 to 00:47:21 UTC) |
| Frontend (typecheck, lint, tests, build) | success | 47 s (00:46:09 to 00:46:56 UTC) |
| API contract | success | 12 s (00:47:23 to 00:47:35 UTC) |
| Whole run, push to finish | success | 90 s (00:46:06 to 00:47:36 UTC) |

The CI job summaries reported the same numbers as the local runs: backend 107 tests, 96.1% lines, 84.4% branches (33 s of test time); frontend 54 tests, 89.06% lines, 81.01% branches. Single run; GitHub-hosted runner times vary.

## Phase 5: Deployment (2026-10-01)

### API startup under Render's free-tier limits

Render's free web service has 0.1 CPU and 512 MB. Simulated locally with Docker's hard limits, against the local Postgres (Phase 1 data), timing from `docker run` to the first `200` on `/actuator/health`:

```bash
scripts/measure-startup.sh <name> <port> <image> [docker run args]   # e.g. -e JAVA_TOOL_OPTIONS=... for variants
```

First, five configurations in parallel (one run each, each container capped separately):

| Configuration | Healthy after | Spring "Started in" | Memory after start |
|---|---|---|---|
| A. Baseline (layered jar, SerialGC) | 253.3 s | 239.8 s | 282.7 MiB |
| B. A + C1 JIT only (`-XX:TieredStopAtLevel=1`) | 80.4 s | 73.1 s | 265.3 MiB |
| C. A + CDS archive | 142.2 s | 131.9 s | 239.6 MiB |
| D. CDS + C1 only | 44.8 s | 40.1 s | 213.8 MiB |
| E. A + lazy bean initialization | 223.5 s | 204.9 s | 271.2 MiB |

Then A and D three times each (in parallel):

| | Run 1 | Run 2 | Run 3 | Median |
|---|---|---|---|---|
| A. Baseline | 274.0 s | 305.7 s | 287.7 s | **287.7 s** |
| D. CDS + C1 only (shipped) | 43.2 s | 46.6 s | 43.6 s | **43.6 s** |

D is 6.6x faster to healthy than A by median, and uses about 25% less memory (211 to 213 MiB vs 276 to 283 MiB). An earlier serial run of A took 361.3 s, so absolute times vary with host load; the ratio held across both batches. Every configuration answered `GET /api/countries/USA` with 200 after starting. With the full laptop CPU the same stack is healthy 8 s after `docker compose up -d --wait`.

Why it works: on a tenth of a CPU, the optimizing C2 JIT compiler competes with startup itself, so limiting the JVM to C1 alone gave the largest single gain (B). CDS avoids re-parsing and verifying about 100 MB of classes (archive: 97.6 MB), and the two combine. The CDS archive is created during `docker build` by starting the full Spring context with the `cds` profile, which needs no database.

### Image sizes

```bash
docker compose build && docker image ls
```

| Image | Size |
|---|---|
| API without CDS (baseline) | 446 MB |
| API with CDS archive (shipped) | 567 MB |
| Frontend (nginx + static build) | 93.6 MB |

The CDS archive costs 121 MB of image size for the startup gain above.

### Local stack checks

- nginx serves `index.html` with `Cache-Control: no-cache` for `/`, `/country/USA` and `/compare?countries=USA,CHN`, and fingerprinted assets with `public, max-age=31536000, immutable`.
- A CORS preflight from `http://localhost:8080` gets `Access-Control-Allow-Origin: http://localhost:8080`.
- `docker compose run --rm etl` exited 0; all three sources were skipped because their SHA-256 matched the last ingest (upstream unchanged since the Phase 1 load).

### Production (live, 2026-10-01 and 2026-10-02 UTC)

Frontend https://pariscompass-r743.vercel.app (Vercel), API https://paris-compass-api-p7m4.onrender.com (Render free web service, Ohio), database on Neon (free plan, AWS us-east-2, PostgreSQL 18.6).

**Initial load into Neon**, from the developer laptop over the internet:

```bash
SPRING_PROFILES_ACTIVE=etl DATABASE_URL=... java -jar target/paris-compass-0.0.1-SNAPSHOT.jar
```

Flyway applied V1 and V2 in 0.662 s. ETL run 1 succeeded in 8,049 ms (15.2 s including JVM startup) with the same counts as the local load: 92,106 rows read, 153,633 values inserted, 60 rows and 36 values rejected. A second run skipped all three sources by SHA-256 (1,342 ms). The password did not appear in the log (checked with grep).

**PostgreSQL 18 compatibility:** Spring Boot 3.5's managed Flyway (11.7.2) logged "PostgreSQL 18.6 is newer than this version of Flyway and support has not been tested". Flyway was pinned to 11.20.3 (no warning against Neon), and Testcontainers and compose moved to PostgreSQL 18; all 107 backend tests pass on 18.

**Smoke test of every endpoint** against the live API (curl): countries 233, metrics 8, USA latest values matching local data, DEU series, SWE alignment 77.0 HIGH, rankings (Qatar, Kuwait, Brunei), compare, unknown country 404 `COUNTRY_NOT_FOUND`, `/v3/api-docs` with 9 paths. One live projection for USA: `VALID`, `gemini-2.5-flash`, `latencyMs` 18,310; the same request again returned `cached: true`. CORS: a preflight from the Vercel origin returned 403 while the placeholder origin was configured and 200 after `CORS_ALLOWED_ORIGINS` was set.

**Frontend on Vercel:** `/`, `/country/USA` and `/compare?countries=USA,CHN` return the app (200), the bundle contains the Render API URL, hashed assets are served with `public, max-age=31536000, immutable`. Checked in Chrome: atlas, USA profile and a 4-country comparison load data; no console errors.

**Cold start on Render**, after 17 minutes without traffic (single measurement):

```bash
sleep 1020; curl -s -o /dev/null -w "%{http_code} %{time_total}s connect %{time_connect}s first byte %{time_starttransfer}s" $API/actuator/health
```

| Request | Result |
|---|---|
| First request after 17 idle minutes (`/actuator/health`) | 200 in **52.3 s** (connect 0.04 s, first byte 52.26 s) |
| Next `/actuator/health` | 200 in 1.3 s |
| Next `/api/countries` | 200 in 2.2 s |

The 52.3 s is Render waking the instance plus JVM startup on 0.1 CPU; it is consistent with the 43.6 s local median plus platform wake time. The warm request times above are single samples from one location and are not latency figures; Phase 6 measures p50/p95.
