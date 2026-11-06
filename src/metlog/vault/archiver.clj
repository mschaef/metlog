(ns metlog.vault.archiver
  (:use playbook.core
        metlog.vault.util
        sql-file.middleware)
  (:require [taoensso.timbre :as log]
            [playbook.config :as config]
            [metlog.vault.data :as data]))

(defn- archive-chunk [series-id chunk-end]
  (with-db-transaction
    (data/archive-old-samples series-id chunk-end)
    (data/delete-old-samples series-id chunk-end)))

;;; A series is archived in chunks, oldest first, each in its own short
;;; transaction. The database runs in HSQLDB's LOCKS mode, so a single
;;; transaction covering a series' whole backlog would lock the sample
;;; table for minutes, stalling the store job and every dashboard read
;;; queued behind it. Each chunk starts at the oldest remaining sample
;;; (so gaps in the data cost nothing) and covers at most chunk-msec.
;;; An interrupted run (e.g. a vault restart) only loses the chunk in
;;; progress; the next run picks up where it left off.
(defn- archive-series [series-id archive-cutoff-date chunk-msec]
  (let [cutoff-msec (.getTime archive-cutoff-date)]
    (loop [chunk-count 0]
      (if-let [earliest (data/query-series-earliest-sample-before series-id archive-cutoff-date)]
        (let [chunk-end (java.sql.Timestamp. (min cutoff-msec (+ (.getTime earliest) chunk-msec)))]
          (archive-chunk series-id chunk-end)
          (recur (inc chunk-count)))
        chunk-count))))

(defn archive-job
  "The :data-archiver job. Runs with the vault's database connection
  (see playbook.db/job-wrapper)."
  []
  (let [archive-cutoff-date (add-days (current-time) (- (config/cval :vault :hot-storage-days)))
        chunk-msec (* 3600 1000 (config/cval :vault :archive-chunk-hours))]
    (log/info "Archive job running with cutoff:" archive-cutoff-date)
    (doseq [series (data/get-all-series)]
      (let [chunk-count (archive-series (:series_id series) archive-cutoff-date chunk-msec)]
        (when (> chunk-count 0)
          (log/info "Archived series" (:series_name series) "in" chunk-count "chunk(s)"))))
    (log/info "Archive job complete")))
