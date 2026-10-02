# 🌍 Paris Compass

A full-stack web application that provides country-specific climate metrics and AI-powered 5-year projections using Google Gemini. Visualize climate data on an interactive world map and get insights into CO2 emissions, renewable energy adoption, temperature anomalies, and Paris Agreement alignment risks.

## Features

- **Interactive World Map**: Click on countries or select from dropdown to view climate metrics
- **Real Climate Data**: CO2 emissions, renewable energy share, temperature anomalies from real datasets
- **AI Projections**: 5-year climate projections using Google Gemini AI
- **Clean UI**: Modern, responsive interface with intuitive layout

## Tech Stack

### Backend
- **Spring Boot 3.5** on Java 21
- **PostgreSQL 17** with **Flyway** migrations and **Spring Data JPA** (reads) / **JDBC + COPY** (bulk ETL writes)
- **Google GenAI SDK** (Gemini, JSON-schema output), **networknt json-schema-validator**, **Bucket4j** rate limiting, **springdoc** OpenAPI
- **Testcontainers** for integration tests against a real Postgres

### Frontend
- **React 19** + **TypeScript** (strict) + **Vite**, **React Router**
- **TanStack Query** for server state; API types generated from the OpenAPI spec (`openapi-typescript` + `openapi-fetch`)
- **d3-geo** SVG choropleth (Equal Earth projection, Natural Earth shapes) and **Recharts** time series
- Details and design decisions: [frontend/README.md](frontend/README.md)

## Prerequisites

- Java 21
- Node.js 18+ and npm
- Docker (for the local Postgres and for integration tests)
- Optional: a Google Gemini API key. Without one, everything except projections works.

## Quick Start

```bash
# 1. Start Postgres (host port 5433)
docker compose up -d

# 2. Load the data: downloads OWID CO2, energy and ERA5 temperature data, validates, upserts, exits
SPRING_PROFILES_ACTIVE=dev,etl ./mvnw spring-boot:run

# 3. Run the API on http://localhost:8081 (optionally export GEMINI_API_KEY first)
./run-backend.sh

# 4. Run the frontend on http://localhost:5173 (new terminal; VITE_API_URL defaults to http://localhost:8081)
cd frontend && npm install && npm run dev
```

Copy `.env.example` to `.env` to set variables; `run-backend.sh` loads it.

## Data Pipeline (ETL)

The ETL job is the same Spring Boot artifact started with the `etl` profile: no web server, runs every source, exits `0` on success or `1` if any source failed. It is safe to run repeatedly.

For each source it:
1. **Downloads** the file and computes its SHA-256 in the same pass. If the hash matches the last successful ingest, the source is skipped (`APP_ETL_FORCE=true` overrides).
2. **Validates** every row (`etl/SourceParser`):
   - Regional aggregates (blank ISO code, e.g. "Africa (GCP)") and OWID's own non-ISO entities (`OWID_KOS`, `OWID_WRL`) are **skipped** and counted separately, so they do not inflate the rejection rate.
   - Rows are **rejected** for an ISO3 code not in ISO 3166-1 (e.g. `ANT`, the dissolved Netherlands Antilles), a non-integer year, a year outside 1750 to the current year, or a repeated (country, year).
   - Single values are **rejected** if not a finite number or outside the metric's plausibility bounds (see below).
3. **Upserts** in one transaction per source: `COPY` into a temp staging table, then a single `INSERT ... ON CONFLICT DO UPDATE ... WHERE value IS DISTINCT FROM`, so unchanged values are never rewritten. Inserted, updated and unchanged counts are reported separately.
4. **Records** the run in `etl_run`, `etl_source_result` and `etl_rejection` (with line number, raw value and reason), and logs a per-source summary.

Run counts and timings from real runs are in [MEASUREMENTS.md](MEASUREMENTS.md).

### Metrics

| Code | Source | Unit | Accepted range |
|------|--------|------|----------------|
| `co2_total_mt` | OWID CO2 (`co2`) | Mt CO2 | 0 to 50,000 |
| `co2_per_capita_t` | OWID CO2 (`co2_per_capita`) | t CO2/person | 0 to 100 |
| `ghg_total_mt` | OWID CO2 (`total_ghg`) | Mt CO2e | -5,000 to 50,000 (negative for net sinks) |
| `population` | OWID CO2 | people | 0 to 10 billion |
| `renewables_share_elec_pct` | OWID Energy | % of electricity | 0 to 100 |
| `low_carbon_share_elec_pct` | OWID Energy | % of electricity | 0 to 100 |
| `renewables_share_energy_pct` | OWID Energy | % of primary energy | 0 to 100 (79 countries only) |
| `temperature_anomaly_c` | OWID / Copernicus ERA5 | °C vs 1991-2020 mean | -10 to 10 |

Bounds are physical plausibility limits chosen before looking at rejections, not fitted to the data. They are deliberately strict: on the real data the 100 t/person bound rejects refinery-dominated microstates (Sint Maarten 1950-1977 reaches 783 t) and also Kuwait 1991 (365 t), which is a real value caused by the Gulf War oil fires. A "flag but keep" status would preserve true extremes; that tradeoff is noted for later.

### Why ERA5 and not Berkeley Earth for temperature

Berkeley Earth's per-country files stopped updating (the US file ends in May 2016; China, Germany and India end in December 2020) and are monthly text files keyed by country name. Copernicus ERA5, as published per country by Our World in Data, covers 193 countries from 1940 to 2025 in one CSV. Its anomalies are relative to the 1991-2020 mean, not pre-industrial levels.

## API Endpoints

Interactive docs: **Swagger UI at `/swagger-ui.html`**, OpenAPI spec at `/v3/api-docs`.

| Method | Path | Returns |
|--------|------|---------|
| `GET` | `/api/countries` | Every country with data, sorted by name |
| `GET` | `/api/countries/{iso3}` | Latest value of each headline metric, with the year of each value |
| `GET` | `/api/countries/{iso3}/series?metrics=&from=&to=` | Time series per metric (default: all metrics, all years) |
| `GET` | `/api/countries/{iso3}/alignment` | Deterministic Paris alignment score with every component ([SCORING.md](SCORING.md)) |
| `POST` | `/api/countries/{iso3}/projection` | Five-year CO2 projection (rate limited; see below) |
| `GET` | `/api/countries/{iso3}/projections?limit=` | Stored projections, newest first |
| `GET` | `/api/metrics` | Metric catalog: unit, source, coverage, default ranking year |
| `GET` | `/api/rankings?metric=&year=&order=&limit=` | Countries ranked by one metric in one year |
| `GET` | `/api/compare?countries=USA,CHN,IND&metric=&from=&to=` | One metric for 2 to 4 countries |
| `GET` | `/actuator/health` | Liveness, including the database |

**Rankings default year.** The newest year is often only partly reported (2025 electricity data covers 90 of 212 countries), so `year` defaults to the latest year with at least 90% of the metric's best coverage. The response states the year and `countriesWithData`.

### Projections

`POST /api/countries/{iso3}/projection` works like this:

1. Builds a prompt (version `p1`) from the country's last 30 years of CO2, per-capita, electricity-mix and temperature data, plus the alignment score as fixed context.
2. Returns a stored projection if one exists for the same input hash (SHA-256 of prompt version + prompt) and model, younger than `PROJECTION_CACHE_TTL` (default 30 days). New data or a new prompt changes the hash, so stale projections are never served.
3. Otherwise asks Gemini for JSON constrained by [`projection-schema.json`](src/main/resources/projection/projection-schema.json), then validates the reply against the same schema plus semantic checks: years follow the last observed year, values within 50-150% of it, and the stated direction matches the numbers.
4. Retries once with the validation errors. If that fails, or no model is configured, returns a trend extrapolation labeled `generatedBy: "trend-extrapolation"`, `status: "FALLBACK"`.
5. Stores every result in the `projection` table with model, prompt version, status, attempts, latency and the output.

Concurrent requests for the same inputs share one model call. Limits: 5 requests per client IP per minute and 100 per hour in total; over the limit returns 429 with `Retry-After`.

```json
{
  "id": 1, "iso3": "USA", "generatedBy": "model", "model": "gemini-2.5-flash", "promptVersion": "p1",
  "status": "VALID", "attempts": 1, "cached": false, "baseYear": 2024, "baseYearCo2Mt": 4904.12,
  "alignmentScore": 30.6, "alignmentBand": "LOW",
  "projection": {
    "summary": "The United States has demonstrated a consistent long-term decline ...",
    "co2Direction": "decreasing",
    "projectedCo2Mt": [{"year": 2025, "co2Mt": 4854.12}, "... 5 entries"],
    "keyDrivers": ["..."], "risks": ["..."], "confidence": "medium"
  }
}
```

## Data Sources

- **CO2 and greenhouse gases, population:** [Our World in Data CO2 dataset](https://github.com/owid/co2-data) (Global Carbon Project, Jones et al., Energy Institute)
- **Electricity and energy mix:** [Our World in Data energy dataset](https://github.com/owid/energy-data) (Ember, Energy Institute)
- **Temperature anomalies:** [Our World in Data, Copernicus ERA5](https://ourworldindata.org/grapher/annual-temperature-anomalies). Contains modified Copernicus Climate Change Service information.

## Project Structure

```
pariscompass/
├── src/main/java/com/ryanpurakal/pariscompass/
│   ├── config/        # AppProperties (typed env config), Gemini client, CORS
│   ├── controller/    # HTTP layer
│   ├── domain/        # Read-only JPA entities (Country, Observation)
│   ├── etl/           # Ingestion job: fetch, validate, upsert, record
│   ├── exception/     # Typed exceptions + RFC 9457 GlobalExceptionHandler
│   ├── model/         # Response DTOs
│   ├── repository/    # Spring Data JPA repositories
│   └── service/       # Metrics and Gemini services
├── src/main/resources/
│   ├── db/migration/  # Flyway migrations (schema owner)
│   └── application*.yml
├── src/test/          # Unit tests + Testcontainers integration tests, ETL fixture CSVs
├── frontend/          # React 19 + TypeScript SPA (see frontend/README.md)
├── docker-compose.yml # Local Postgres
└── MEASUREMENTS.md    # Every reported number and the command that produced it
```

## Architecture & Data Flow

```
 OWID CO2 / Energy / ERA5 CSVs
        │  (etl profile: download, validate, COPY + upsert)
        ▼
   PostgreSQL ── country, metric, observation(iso3, metric_code, year, value)
        ▲
        │  JPA reads
  Analytics / Alignment / Metrics services ◄── controllers ◄── React SPA (TanStack Query)
                                                    │
               ProjectionService ◄──────────────────┘ (rate limited)
                 │  prompt from history + alignment score
                 ├──► projection table (cache by input hash, history)
                 └──► ProjectionModel ──► Google Gemini API (JSON schema output)
```

**Key design choices:**
- **One row per (country, metric, year)** rather than a column per metric: new metrics need no migration and every metric is queried the same way. The primary key `(iso3, metric_code, year)` serves time-series reads; a covering index on `(metric_code, year)` serves "all countries in a year" reads.
- **Flyway owns the schema**; Hibernate runs with `ddl-auto=validate` and only checks that entities match it.
- **JDBC for bulk writes, JPA for reads**: about 150k values per load is set-based work where per-entity persistence adds overhead for no benefit.
- **The ETL is a profile of the API artifact**, not a separate service: one build, shared schema and config.
- **The alignment score is computed in Java, never by the LLM**; the model only sees it as context.
- **The model is not trusted**: its JSON is validated against the same schema it was constrained by, plus semantic checks, with one retry and a labeled statistical fallback.
- **Projections are cached by a hash of their inputs**, not just by country, and stored permanently so outputs can be compared across models and prompt versions.
- CORS origins come from `CORS_ALLOWED_ORIGINS` (dev default `http://localhost:5173`).

## Configuration

Settings live in `src/main/resources/application.yml` with per-profile overrides (`application-dev.yml`, `application-test.yml`, `application-prod.yml`). Every secret or deployment-specific value is an environment variable; see `.env.example` and `frontend/.env.example`.

| Variable | Default | Notes |
|----------|---------|-------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`, `test` or `prod` |
| `GEMINI_API_KEY` | none | Optional in dev (projections use the statistical fallback), required in prod |
| `GEMINI_MODEL` | `gemini-2.5-flash` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` in dev | Comma-separated; required in prod, `*` rejected |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | local compose DB in dev | Required in prod |
| `APP_ETL_FORCE` | `false` | Re-ingest sources even if their file hash is unchanged |
| `PROJECTION_CACHE_TTL` | `30d` | How long one model output is reused for unchanged inputs |
| `RATE_LIMIT_PER_CLIENT_PER_MINUTE` | `5` | Projection requests per client IP |
| `RATE_LIMIT_GLOBAL_PER_HOUR` | `100` | Projection requests across all clients |
| `PORT` | `8081` | |
| `VITE_API_URL` (frontend) | `http://localhost:8081` | Backend origin, baked into the JS bundle at build time |

Prod refuses to start if a required variable is missing, and reports all of them at once.

## Testing

```bash
./mvnw verify                      # backend: unit + Testcontainers integration tests, JaCoCo report, coverage gate (Docker required)
cd frontend && npm test            # frontend: Vitest + React Testing Library + MSW
cd frontend && npm run test:coverage
cd frontend && npm run api:check   # after ./mvnw verify: fails if the generated API types are stale
```

| Layer | What runs | How external systems are handled |
|---|---|---|
| Backend unit | Parser and validator, alignment scorer, projection validator, prompt builder, trend fallback, rate limiter, config validation | Pure functions; no Spring |
| Backend integration | ETL end to end, repositories and native queries, every controller through MockMvc, projection caching and request coalescing, OpenAPI contract | Real PostgreSQL 17 in Testcontainers; Gemini replaced by a scripted fake |
| Backend HTTP adapters | Gemini SDK adapter, ETL downloader | Local HTTP server (JDK `HttpServer`) standing in for Gemini and for the data hosts |
| Frontend unit | Map classes, formatting, color slots, API client error mapping | None needed |
| Frontend components and pages | Combobox, map keyboard navigation, ranking sort, alignment and projection panels, charts, all three pages | MSW intercepts `fetch` with fixtures typed by the generated API schema; unhandled requests fail the test |
| Accessibility | axe-core on the shell and every page | jsdom (color contrast is checked separately) |

Coverage gates sit just below the measured values (backend in `pom.xml`, frontend in `vite.config.ts`) and only move up. Counts and percentages are in [MEASUREMENTS.md](MEASUREMENTS.md).

### Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs on every push and pull request:

- **backend**: `./mvnw verify` on JDK 21 (tests, coverage report, coverage gate)
- **frontend**: typecheck, lint, tests with coverage, production build on Node 24
- **contract**: regenerates the frontend's OpenAPI types from the spec the backend tests exported, and fails on any difference

Each job writes its test counts and coverage to the run summary and uploads its reports as artifacts.

## Development Notes

### Adding a metric

Add an entry to `etl/MetricDefinition` (source, source column, unit, bounds) and re-run the ETL with `APP_ETL_FORCE=true`. The `metric` table is synced from the enum on every run; no migration is needed.

### CORS Configuration

Allowed origins come from `CORS_ALLOWED_ORIGINS` (comma-separated). Dev defaults to `http://localhost:5173`.

### Error Handling

Every error uses the RFC 9457 `application/problem+json` shape:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No country found with ISO3 code 'XXX'",
  "instance": "/api/countries/XXX",
  "code": "COUNTRY_NOT_FOUND",
  "timestamp": "2026-10-01T19:27:51Z"
}
```

| Status | `code` | When |
|--------|--------|------|
| 400 | `BAD_REQUEST` | ISO3 path variable is not three letters |
| 404 | `COUNTRY_NOT_FOUND` | ISO3 is well formed but not in the dataset |
| 404 / 405 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` | Unknown route or wrong HTTP method |
| 400 | `UNKNOWN_METRIC`, `INVALID_YEAR_RANGE`, `INVALID_COUNTRY_LIST`, `INVALID_ORDER` | Semantically invalid query |
| 422 | `INSUFFICIENT_DATA` | Projection requested for a country with fewer than 6 of the last 10 years of CO2 data |
| 429 | `RATE_LIMITED` | Projection rate limit exceeded; see `Retry-After` |
| 500 | `INTERNAL_ERROR` | Anything unexpected; details are logged, never returned |

Missing data fields return `null` in JSON responses.

## Troubleshooting

### Backend won't start
- Ensure Java 21 is installed: `java -version`
- Check if port 8081 is available
- Ensure Postgres is running: `docker compose ps`
- In prod, `GEMINI_API_KEY` and `CORS_ALLOWED_ORIGINS` must be set (dev runs without them)

### Frontend can't connect to backend
- Ensure backend is running on port 8081
- Check CORS configuration matches frontend URL
- Verify `VITE_API_URL` points at the backend origin (default `http://localhost:8081`)

### No data showing
- Run the ETL: `SPRING_PROFILES_ACTIVE=dev,etl ./mvnw spring-boot:run`
- Check the last run: `SELECT * FROM etl_source_result ORDER BY run_id DESC LIMIT 3;`

### Port 5432 or 5433 already in use
- The compose Postgres listens on host port 5433. Set `POSTGRES_HOST_PORT` and `DATABASE_URL` to use another.

### Gemini API errors
- Verify `GEMINI_API_KEY` is set correctly
- Check API key is valid and has quota
- Review application logs for detailed error messages

## License

This project is open source and available for educational purposes.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Acknowledgments

- Climate data: Our World in Data, Global Carbon Project, Ember, Energy Institute, Copernicus Climate Change Service (ERA5)
- Map boundaries: Natural Earth (public domain)
- AI: Google Gemini

