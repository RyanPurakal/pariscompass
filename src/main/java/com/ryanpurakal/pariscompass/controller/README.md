# controller: HTTP Layer

Two controllers. They validate request shape, delegate to services, and return DTOs. Errors are thrown and rendered by `exception/GlobalExceptionHandler`. Full, interactive documentation is served at `/swagger-ui.html`.

| Controller | Routes |
|------------|--------|
| `CountryController` | `/api/countries`, `/api/countries/{iso3}`, `.../series`, `.../alignment`, `.../projection` (POST), `.../projections` |
| `MetricController` | `/api/metrics`, `/api/rankings`, `/api/compare` |

Path and query parameters are validated with Spring 6.1 method validation (`@Pattern`, `@Min`, `@Max`), which produces 400 problem responses. Semantic checks (unknown metric, wrong number of countries) live in the services.

The projection route is rate limited by `ratelimit/RateLimitInterceptor`, registered in `config/WebConfig`.
