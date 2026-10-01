-- Every projection ever produced, kept for caching and for comparing outputs across
-- models and prompt versions. input_hash is the SHA-256 of prompt version + prompt text,
-- and the prompt embeds all the data, so new data or a new prompt means a new hash.

CREATE TABLE projection (
    id                BIGSERIAL        PRIMARY KEY,
    iso3              CHAR(3)          NOT NULL REFERENCES country (iso3),
    created_at        TIMESTAMPTZ      NOT NULL,
    generated_by      VARCHAR(32)      NOT NULL,
    model             VARCHAR(64),
    prompt_version    VARCHAR(16)      NOT NULL,
    input_hash        CHAR(64)         NOT NULL,
    status            VARCHAR(16)      NOT NULL CHECK (status IN ('VALID', 'REPAIRED', 'FALLBACK')),
    attempts          SMALLINT         NOT NULL,
    fallback_reason   TEXT,
    validation_errors JSONB            NOT NULL,
    latency_ms        INTEGER          NOT NULL,
    base_year         SMALLINT         NOT NULL,
    base_year_co2_mt  DOUBLE PRECISION NOT NULL,
    alignment_score   DOUBLE PRECISION,
    alignment_band    VARCHAR(8),
    output            JSONB            NOT NULL
);

-- Cache lookup: latest projection for (country, inputs, model).
CREATE INDEX idx_projection_cache ON projection (iso3, input_hash, model, created_at DESC);
-- History endpoint: a country's projections, newest first.
CREATE INDEX idx_projection_history ON projection (iso3, created_at DESC);
