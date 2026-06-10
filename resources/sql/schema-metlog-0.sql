CREATE CACHED TABLE series (
       series_id INTEGER IDENTITY,
       series_name VARCHAR(128) NOT NULL
);

CREATE CACHED TABLE sample (
       series_id INTEGER NOT NULL,
       t TIMESTAMP NOT NULL,
       val DOUBLE NOT NULL
);

CREATE INDEX idx_sample_t ON sample(series_id, t);

CREATE CACHED TABLE dual (
       x INTEGER
);

INSERT INTO dual(x) values(1);

