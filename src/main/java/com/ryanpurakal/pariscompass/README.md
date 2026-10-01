# Backend: `com.ryanpurakal.pariscompass`

Spring Boot 3.5 application serving climate metrics and AI projections.

## Package layout

| Package | Responsibility |
|---------|---------------|
| `config/` | Spring bean definitions: Gemini API client, CORS filter |
| `domain/` | Read-only JPA entities mapped to the Flyway schema |
| `etl/` | Ingestion job: fetch, validate, upsert, record (see `etl/README.md`) |
| `repository/` | Spring Data JPA repositories |
| `controller/` | REST layer: one controller handles all `/api` routes |
| `exception/` | Typed exceptions and `GlobalExceptionHandler` (RFC 9457 problem+json for every error) |
| `projection/` | Prompt builder, model interface + Gemini adapter, JSON schema validation, trend fallback |
| `ratelimit/` | Bucket4j limits on the projection endpoint |
| `scoring/` | Pure, deterministic alignment score (`SCORING.md`) |
| `model/` | DTOs (request/response objects) shared across layers |
| `service/` | Business logic: metric snapshots, Gemini calls |

## Entry point

`ParisCompassApplication.java`: standard `@SpringBootApplication` bootstrap. No custom startup logic here; `etl/EtlRunner` runs the ingestion job only when the `etl` profile is active.

## Request lifecycle

1. HTTP request arrives → `CountryController`
2. Controller delegates to a service (`service/`); projections go through `ProjectionService`
3. Services read Postgres through JPA repositories; projections call Gemini through the `ProjectionModel` interface
4. Controller assembles a response DTO and returns it
5. Any exception is rendered by `GlobalExceptionHandler` as `application/problem+json`

## Configuration

Settings live in `src/main/resources/application.yml` plus one file per profile (`dev`, `test`, `prod`), bound to the typed `config/AppProperties`. See `config/README.md` for the profile matrix and `/.env.example` for every environment variable.
