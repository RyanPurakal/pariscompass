# etl: Data Ingestion

Downloads, validates and loads the source datasets into Postgres. Runs as a one-shot job:

```bash
SPRING_PROFILES_ACTIVE=dev,etl ./mvnw spring-boot:run          # skip sources whose file is unchanged
SPRING_PROFILES_ACTIVE=dev,etl APP_ETL_FORCE=true ./mvnw spring-boot:run   # re-ingest everything
```

## Files

| File | Role |
|------|------|
| `DataSource` | Upstream datasets and the columns that identify a row (ISO3, name, year) |
| `MetricDefinition` | Every stored metric: source column, unit, plausibility bounds. Single source of truth, synced to the `metric` table each run |
| `SourceFetcher` | Copies an https://, file: or classpath: location to a temp file, computing SHA-256 in the same pass |
| `SourceParser` | Pure parsing and validation; no Spring, no database |
| `EtlRepository` | All writes (JDBC): catalog sync, COPY + upsert, run and rejection records |
| `EtlService` | Orchestrates one run across all sources |
| `EtlRunner` | Starts the run when `app.etl.run-on-startup` is set and exits with 0 or 1 |

## Guarantees

- **Idempotent:** rerunning on the same data inserts and updates nothing (`WHERE value IS DISTINCT FROM`).
- **Atomic per source:** a source's countries, values and rejections commit together. One failing source does not block the others; the run is marked `FAILED`.
- **Serialized:** a Postgres advisory lock prevents two runs writing at once.
- **Auditable:** every rejection is stored with its line number, raw value and reason (up to 10,000 per source per run; counts are always exact). Each observation records the run that last changed it.

## Known limitations

- Values removed upstream are not deleted; the last ingested value stays.
- A run killed mid-way stays `RUNNING` in `etl_run`.
- Plausibility bounds reject true extremes as well as errors (e.g. Kuwait 1991, the Gulf War oil fires).
