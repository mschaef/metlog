CREATE CACHED TABLE cold_sample (
       series_id INTEGER NOT NULL REFERENCES series(series_id),
       t TIMESTAMP NOT NULL,
       val DOUBLE NOT NULL
);

CREATE INDEX idx_cold_sample_t ON cold_sample(t);
