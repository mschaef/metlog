-- name: get-all-series
SELECT series_id, series_name
  FROM series
 ORDER BY series_name

-- name: get-series-id
-- info-query-mode: :scalar
SELECT series_id
  FROM series
 WHERE series_name=:series_name

-- name: get-series-latest-sample-time
-- info-query-mode: :scalar
SELECT MAX(t)
  FROM sample
 WHERE series_id=:series_id

-- name: get-series-earliest-sample-before
-- info-query-mode: :scalar
SELECT MIN(t)
  FROM sample
 WHERE series_id=:series_id
   AND t<:archive_time

-- name: get-data-for-series
SELECT sample.t, sample.val
  FROM sample
 WHERE series_id = :series_id
   AND t > :begin_t
   AND t < :end_t
 ORDER BY t

-- name: delete-old-samples!
DELETE FROM sample
  WHERE series_id=:series_id
    AND t<:archive_time

-- name: archive-old-samples!
INSERT INTO cold_sample
   SELECT * FROM sample
    WHERE series_id=:series_id
      AND t<:archive_time

-- name: get-dashboard-names
SELECT name, dashboard_id
  FROM dashboard
 ORDER BY name

-- name: get-dashboard-by-name
-- info-query-mode: :row
SELECT name, dashboard_id, definition
  FROM dashboard
 WHERE name=:name

-- name: get-dashboard-by-id
-- info-query-mode: :row
SELECT name, dashboard_id, definition
  FROM dashboard
 WHERE dashboard_id=:id

-- name: add-series<!
INSERT INTO series(series_name)
  VALUES(:series_name)

-- name: add-dashboard<!
INSERT INTO dashboard(name, definition)
  VALUES(:name, :definition)

-- name: update-dashboard-definition!
UPDATE dashboard
   SET definition=:definition
 WHERE dashboard_id=:id

-- name: delete-dashboard!
DELETE FROM dashboard
 WHERE dashboard_id=:id
