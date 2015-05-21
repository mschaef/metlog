CREATE CACHED TABLE series (
       series_id INTEGER IDENTITY,
       series_name VARCHAR(128) NOT NULL
);

CREATE CACHED TABLE sample (
       series_id INTEGER NOT NULL REFERENCES series(series_id),       
       t TIMESTAMP NOT NULL,
       val DOUBLE NOT NULL
)