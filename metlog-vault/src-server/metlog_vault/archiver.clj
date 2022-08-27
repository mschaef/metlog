(ns metlog-vault.archiver
  (:use metlog-common.core
        metlog-vault.util
        sql-file.middleware)
  (:require [taoensso.timbre :as log]
            [metlog-vault.data :as data]
            [metlog-vault.scheduler :as scheduler]))

(defn- archive-series [ series-id archive-cutoff-date ]
  (with-db-transaction
    (log/info "Archiving series: " series-id "cutoff: " archive-cutoff-date)
    (data/archive-old-samples series-id archive-cutoff-date)
    (data/delete-old-samples series-id archive-cutoff-date)))

(defn- archive-job [ config db-pool ]
  (let [ archive-cutoff-date (add-days (current-time) (- (:hot-storage-days config)))]
    (with-db-connection db-pool
      (doseq [ series (data/get-all-series) ]
        (archive-series (:series_id series) archive-cutoff-date)))))

(defn start [ config scheduler db-pool  ]
  (scheduler/schedule-job scheduler "data-archiver"
                          ;; "0 1 * * *"
                          "*/1 * * * *"
                          (partial archive-job config db-pool)))
