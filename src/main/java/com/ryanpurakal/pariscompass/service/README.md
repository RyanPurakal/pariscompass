# service: Business Logic

Two services. Each has a single, narrow job. Data is written by the `etl` package and read here.

## Files

### `CountryMetricsService.java`
Builds a `CountryMetrics` snapshot for one ISO3 code from the `observation` table.

Key behaviour:
- Takes the **latest year of each metric independently** (Postgres `DISTINCT ON`), and reports each value's year in `years`.
- Throws `CountryNotFoundException` if the ISO3 is unknown (rendered as 404 by `GlobalExceptionHandler`).
- `getAllCountries()` returns every ingested country, sorted by name.

### `GeminiService.java`
Sends a structured prompt to the Gemini API and returns a `ProjectionResponse`.

Key behaviour:
- Results are **cached per ISO3 for 1 hour** using an in-memory `ConcurrentHashMap`. Repeated requests for the same country within the TTL skip the API call.
- Upstream failures are wrapped in `ProjectionUnavailableException` (rendered as 503); the cause is logged, never returned.
- `buildPrompt()` formats the `CountryMetrics` fields into a numbered instruction prompt.

## Data flow between services

```
HTTP request
     │
     ▼
CountryController
     │
     ├─ CountryMetricsService.getLatestMetrics(iso3)
     │       └─ CountryRepository / ObservationRepository (Postgres)
     │
     └─ GeminiService.generateProjection(iso3, metrics)
             └─ Gemini API (external, via Client bean from config/)
```
