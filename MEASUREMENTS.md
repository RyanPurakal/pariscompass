# Measurements

Every number here was produced by running the command shown next to it. Nothing is estimated.
If a number is not in this file, it has not been measured and should not appear on a resume.

Environment for all entries unless noted: macOS (Darwin 27), OpenJDK 21.0.8, Apache Maven 3.9.11, Node 24.8.0.

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

Not yet measured (planned): code coverage (Phase 4, JaCoCo), endpoint latency and cache behavior (Phase 6), ETL row counts and runtime (Phase 1 and Phase 6).
