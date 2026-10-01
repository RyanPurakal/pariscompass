# config: Spring Bean Configuration

Typed settings plus the beans for the two external concerns (Gemini, CORS).

## Files

### `AppProperties.java`
`@ConfigurationProperties(prefix = "app")` record, validated at startup.
- `app.gemini.{model, api-key, required}` and `app.cors.allowed-origins`.
- Values come from `application*.yml`, which read environment variables. No secrets in the repo.
- `required=true` (prod profile) makes a missing `GEMINI_API_KEY` fail startup. Empty or `*` CORS origins always fail startup.
- `Gemini.toString()` masks the key so it can never be logged by accident.

### `GeminiConfig.java`
Creates the `com.google.genai.Client` bean only when an API key is set. Without one the bean is absent and `GeminiService` returns 503 for projections, so dev and tests run without credentials.

### `CorsConfig.java`
Registers a `CorsFilter` for `/api/**` using `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`).
- Only `GET`, `POST`, `OPTIONS`. Credentials are off because the API uses no cookies or auth.

## Profiles

| Profile | Activated by | Gemini key | CORS origins |
|---------|--------------|------------|--------------|
| `dev` | default | optional (503 without it) | defaults to `http://localhost:5173` |
| `test` | `@ActiveProfiles("test")` | forced empty, so tests never call the live API | `http://localhost:5173` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | required, startup fails without it | required, startup fails without it |

Database: `dev` defaults to the docker-compose Postgres on `localhost:5433`; `test` uses a Testcontainers Postgres; `prod` requires `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD`.

`etl` is an add-on profile (e.g. `dev,etl`): no web server, run the ingestion job once, exit 0 or 1.
