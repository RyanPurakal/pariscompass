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
- **Google GenAI SDK** for Gemini projections
- **Testcontainers** for integration tests against a real Postgres

### Frontend
- **React 19** + **Vite**
- **Leaflet** / **React Leaflet** for the map

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

# 4. Run the frontend on http://localhost:5173 (new terminal)
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

### `GET /api/countries`
Returns list of all supported countries.

**Response:**
```json
[
  {
    "iso3": "USA",
    "name": "United States"
  },
  {
    "iso3": "IND",
    "name": "India"
  }
]
```

### `GET /api/countries/{iso3}`
Returns climate metrics for a specific country.

**Example:** `GET /api/countries/USA`

**Response:**
```json
{
  "iso3": "USA",
  "name": "United States",
  "co2PerCapita": 14.197,
  "co2TotalMt": 4904.12,
  "temperatureAnomalyC": 0.83337736,
  "renewablesSharePct": 25.638,
  "years": {
    "co2PerCapita": 2024,
    "co2TotalMt": 2024,
    "temperatureAnomalyC": 2025,
    "renewablesSharePct": 2025
  },
  "source": {
    "co2": "Our World in Data: CO2 and Greenhouse Gas Emissions",
    "temp": "Our World in Data: Annual temperature anomalies (Copernicus ERA5)",
    "renewables": "Our World in Data: Energy"
  }
}
```

`years` gives the year of each value: sources end in different years, so there is no single snapshot year. `renewablesSharePct` is the renewables share of electricity generation.

### `POST /api/countries/{iso3}/projection`
Generates AI projection for a country (combines metrics + projection).

**Example:** `POST /api/countries/USA/projection`

**Response:**
```json
{
  "metrics": { "...": "same shape as GET /api/countries/{iso3}" },
  "projection": {
    "country": "United States",
    "projection": "Based on current trends, the United States is projected to...",
    "model": "gemini-2.5-flash",
    "generatedAt": "2024-01-15T10:30:00Z"
  }
}
```

### `GET /actuator/health`
Spring Boot Actuator health endpoint.

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
├── frontend/          # React 19 + Vite SPA
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
  CountryMetricsService ◄── CountryController ◄── React (App.jsx)
                                   │
                                   └──► GeminiService ──► Google Gemini API
```

**Key design choices:**
- **One row per (country, metric, year)** rather than a column per metric: new metrics need no migration and every metric is queried the same way. The primary key `(iso3, metric_code, year)` serves time-series reads; a covering index on `(metric_code, year)` serves "all countries in a year" reads.
- **Flyway owns the schema**; Hibernate runs with `ddl-auto=validate` and only checks that entities match it.
- **JDBC for bulk writes, JPA for reads**: about 150k values per load is set-based work where per-entity persistence adds overhead for no benefit.
- **The ETL is a profile of the API artifact**, not a separate service: one build, shared schema and config.
- Gemini projections are cached per country for 1 hour to avoid redundant API calls.
- CORS origins come from `CORS_ALLOWED_ORIGINS` (dev default `http://localhost:5173`).

## Configuration

Settings live in `src/main/resources/application.yml` with per-profile overrides (`application-dev.yml`, `application-test.yml`, `application-prod.yml`). Every secret or deployment-specific value is an environment variable; see `.env.example` and `frontend/.env.example`.

| Variable | Default | Notes |
|----------|---------|-------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`, `test` or `prod` |
| `GEMINI_API_KEY` | none | Optional in dev (projections return 503), required in prod |
| `GEMINI_MODEL` | `gemini-2.5-flash` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` in dev | Comma-separated; required in prod, `*` rejected |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | local compose DB in dev | Required in prod |
| `APP_ETL_FORCE` | `false` | Re-ingest sources even if their file hash is unchanged |
| `PORT` | `8081` | |
| `VITE_API_BASE_URL` (frontend) | `http://localhost:8081/api` | Baked into the JS bundle at build time |

Prod refuses to start if a required variable is missing, and reports all of them at once.

## Testing

```bash
./mvnw clean test   # needs Docker running: integration tests start a Postgres container
```

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
| 503 | `PROJECTION_UNAVAILABLE` | Gemini is not configured or the upstream call failed |
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
- Verify `VITE_API_BASE_URL` points at the backend (default `http://localhost:8081/api`)

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
- Mapping: Leaflet and OpenStreetMap
- AI: Google Gemini

