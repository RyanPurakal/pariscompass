# Backend — `com.ryanpurakal.pariscompass`

Spring Boot 3.5 application serving climate metrics and AI projections.

## Package layout

| Package | Responsibility |
|---------|---------------|
| `config/` | Spring bean definitions: Gemini API client, CORS filter |
| `controller/` | REST layer — one controller handles all `/api` routes |
| `exception/` | Typed exceptions and `GlobalExceptionHandler` (RFC 9457 problem+json for every error) |
| `model/` | DTOs (request/response objects) shared across layers |
| `service/` | Business logic: CSV data loading, metric aggregation, Gemini calls |

## Entry point

`ParisCompassApplication.java` — standard `@SpringBootApplication` bootstrap. No custom startup logic here; `DataLoader` runs its own `@PostConstruct` to load CSVs.

## Request lifecycle

1. HTTP request arrives → `CountryController`
2. Controller delegates to `CountryMetricsService` (data) and/or `GeminiService` (AI)
3. Services use `DataLoader` (in-memory maps) and the Gemini `Client` bean (external)
4. Controller assembles a response DTO and returns it
5. Any exception is rendered by `GlobalExceptionHandler` as `application/problem+json`

## Configuration

Settings live in `src/main/resources/application.yml` plus one file per profile (`dev`, `test`, `prod`), bound to the typed `config/AppProperties`. See `config/README.md` for the profile matrix and `/.env.example` for every environment variable.
