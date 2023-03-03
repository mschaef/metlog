-- name: get-all-series
SELECT series_id, series_name
  FROM series

-- name: get-series-id
SELECT series_id
  FROM series
 WHERE series_name=:series_name

-- name: get-series-latest-sample-time
SELECT MAX(t)
  FROM sample
 WHERE series_id=:series_id

-- name: get-data-for-series
SELECT sample.t, sample.val
  FROM sample
 WHERE series_id = :series_id
   AND UNIX_MILLIS(t-session_timezone()) > :begin_t
   AND UNIX_MILLIS(t-session_timezone()) < :end_t
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
SELECT name, dashboard_id, definition
  FROM dashboard
 WHERE name=:name

-- name: get-dashboard-by-id
SELECT name, dashboard_id, definition
  FROM dashboard
 WHERE dashboard_id=:id
