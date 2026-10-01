-- Time-series schema: one row per (country, metric, year) observation.
-- A narrow table instead of one column per metric means new metrics need no migration
-- and every metric is queried the same way.

CREATE TABLE country (
    iso3 CHAR(3)      PRIMARY KEY CHECK (iso3 ~ '^[A-Z]{3}$'),
    name VARCHAR(100) NOT NULL
);

CREATE TABLE data_source (
    code     VARCHAR(32)  PRIMARY KEY,
    name     VARCHAR(200) NOT NULL,
    homepage VARCHAR(500) NOT NULL,
    citation TEXT         NOT NULL
);

CREATE TABLE metric (
    code        VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    unit        VARCHAR(32)  NOT NULL,
    source_code VARCHAR(32)  NOT NULL REFERENCES data_source (code),
    description TEXT         NOT NULL
);

CREATE TABLE etl_run (
    id          BIGSERIAL   PRIMARY KEY,
    started_at  TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status      VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    forced      BOOLEAN     NOT NULL,
    error       TEXT
);

CREATE TABLE observation (
    iso3        CHAR(3)          NOT NULL REFERENCES country (iso3),
    metric_code VARCHAR(64)      NOT NULL REFERENCES metric (code),
    year        SMALLINT         NOT NULL CHECK (year BETWEEN 1750 AND 2100),
    value       DOUBLE PRECISION NOT NULL,
    etl_run_id  BIGINT           NOT NULL REFERENCES etl_run (id),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    -- Serves "one country's series for a metric over a year range" as a single index range scan.
    PRIMARY KEY (iso3, metric_code, year)
);

-- Serves "every country for metric X in year Y" (rankings, choropleth map).
-- INCLUDE makes it covering, so those queries can be answered by an index-only scan.
CREATE INDEX idx_observation_metric_year ON observation (metric_code, year) INCLUDE (iso3, value);

CREATE TABLE etl_source_result (
    run_id                 BIGINT      NOT NULL REFERENCES etl_run (id),
    source_code            VARCHAR(32) NOT NULL REFERENCES data_source (code),
    location               TEXT        NOT NULL,
    sha256                 CHAR(64),
    bytes                  BIGINT,
    skipped_unchanged      BOOLEAN     NOT NULL DEFAULT FALSE,
    rows_read              INTEGER     NOT NULL DEFAULT 0,
    rows_skipped_aggregate INTEGER     NOT NULL DEFAULT 0,
    rows_skipped_non_iso   INTEGER     NOT NULL DEFAULT 0,
    rows_rejected          INTEGER     NOT NULL DEFAULT 0,
    values_accepted        INTEGER     NOT NULL DEFAULT 0,
    values_rejected        INTEGER     NOT NULL DEFAULT 0,
    values_inserted        INTEGER     NOT NULL DEFAULT 0,
    values_updated         INTEGER     NOT NULL DEFAULT 0,
    values_unchanged       INTEGER     NOT NULL DEFAULT 0,
    name_mismatches        INTEGER     NOT NULL DEFAULT 0,
    duration_ms            BIGINT      NOT NULL DEFAULT 0,
    error                  TEXT,
    PRIMARY KEY (run_id, source_code)
);

CREATE TABLE etl_rejection (
    id          BIGSERIAL   PRIMARY KEY,
    run_id      BIGINT      NOT NULL REFERENCES etl_run (id),
    source_code VARCHAR(32) NOT NULL REFERENCES data_source (code),
    line_number INTEGER     NOT NULL,
    iso3        VARCHAR(16),
    year        VARCHAR(16),
    metric_code VARCHAR(64),
    raw_value   VARCHAR(64),
    reason      VARCHAR(32) NOT NULL,
    detail      TEXT
);

CREATE INDEX idx_etl_rejection_run ON etl_rejection (run_id, reason);
