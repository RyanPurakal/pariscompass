# service — Business Logic

Three Spring-managed components. Each has a single, narrow job.

## Files

### `DataLoader.java`
Loads all CSV files from `src/main/resources/data/` into memory at startup (`@PostConstruct`).

Produces four in-memory maps:
- `co2Data`: `iso3 → year → Co2Data`
- `renewablesData`: `iso3 → year → RenewablesData`
- `temperatureData`: `iso3 → year → TemperatureData`
- `countryNames`: `iso3 → display name`

Nothing reads the CSV files after startup. All other services query these maps directly.

### `CountryMetricsService.java`
Aggregates the four raw maps from `DataLoader` into a single `CountryMetrics` object for a given ISO3 code.

Key behaviour:
- Finds the **latest available year** across all three datasets for a given country.
- Returns `null` if the ISO3 is unknown (controller converts this to 404).
- Also provides `getAllCountries()` (sorted by name) and `findIso3ByName()` (case-insensitive lookup).

### `GeminiService.java`
Sends a structured prompt to the Gemini API and returns a `ProjectionResponse`.

Key behaviour:
- Results are **cached per ISO3 for 1 hour** using an in-memory `ConcurrentHashMap`. Repeated requests for the same country within the TTL skip the API call.
- `buildPrompt()` formats the `CountryMetrics` fields into a numbered instruction prompt.
- `askGemini()` is a legacy raw-prompt method kept for the `/api/gemini/ask` endpoint.

## Data flow between services

```
HTTP request
     │
     ▼
GeminiController
     │
     ├─ CountryMetricsService.getLatestMetrics(iso3)
     │       └─ DataLoader.getCo2Data() / getRenewablesData() / getTemperatureData()
     │
     └─ GeminiService.generateProjection(iso3, metrics)
             └─ Gemini API (external, via Client bean from config/)
```
