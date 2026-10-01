# service: Business Logic

Read-side services over Postgres (data is written by the `etl` package), plus projection orchestration.

| Service | Job |
|---------|-----|
| `CountryMetricsService` | Latest value of each headline metric for a country, each with its own year (`DISTINCT ON`) |
| `AnalyticsService` | Time series, comparison (2 to 4 countries), rankings, metric catalog; semantic request validation |
| `AlignmentService` | Loads a country's history and calls the pure `scoring/AlignmentScorer` (see `SCORING.md`) |
| `ProjectionService` | Prompt, cache lookup by input hash, in-flight coalescing, model call, validation, one retry, fallback, storage |

## Projection flow

```
POST /api/countries/{iso3}/projection
  -> RateLimitInterceptor (429 if over limit)
  -> ProjectionService.prepare: history + alignment score -> prompt -> input hash   (short read transaction)
  -> stored projection for (iso3, hash, model) within TTL?  -> return it (cached=true)
  -> same inputs already in flight?                          -> wait for that result
  -> ProjectionModel.generate (Gemini, JSON schema)          (no DB connection held)
  -> ProjectionValidator: JSON, schema, semantics -> retry once with errors -> TrendExtrapolator fallback
  -> save to projection table                                (short write transaction)
```
