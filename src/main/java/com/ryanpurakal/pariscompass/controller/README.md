# controller: HTTP Layer

One controller, `CountryController`, handles every public-facing API route under `/api`.

## Responsibility

Translate HTTP requests into service calls and HTTP responses. No business logic lives here. The controller only:
1. Validates path/body inputs (e.g. blank ISO3 → 400, unknown country → 404)
2. Delegates to `CountryMetricsService` and/or `GeminiService`
3. Returns the response DTO; errors are thrown and rendered by `exception/GlobalExceptionHandler`

## Routes

| Method | Path | What it does |
|--------|------|-------------|
| `GET`  | `/api/countries` | List all countries (ISO3 + name) |
| `GET`  | `/api/countries/{iso3}` | Current climate metrics for one country |
| `POST` | `/api/countries/{iso3}/projection` | Metrics + Gemini 5-year projection (main endpoint) |

## What passes through here

Inbound: `String iso3` (path variable)  
Outbound: `CountryMetrics`, `ProjectionResponse`, or `CountryProjectionResponse`, all from the `model/` package
