(ns metlog-vault.archiver
  (:use playbook.core
        metlog-vault.util
        sql-file.middleware)
  (:require [taoensso.timbre :as log]
            [metlog-vault.data :as data]
            [metlog-vault.scheduler :as scheduler]))

(defn- archive-series [ series-id archive-cutoff-date ]
  (with-db-transaction
    (log/debug "Archiving series:" series-id)
    (data/archive-old-samples series-id archive-cutoff-date)
    (data/delete-old-samples series-id archive-cutoff-date)))

(defn- archive-job [ config db-pool ]
  (let [ archive-cutoff-date (add-days (current-time) (- (:hot-storage-days config)))]
    (log/info "Archive job running with cutoff:" archive-cutoff-date)
    (with-db-connection db-pool
      (doseq [ series (data/get-all-series) ]
        (archive-series (:series_id series) archive-cutoff-date)))))

(defn start [ config scheduler db-pool  ]
  (scheduler/schedule-job scheduler "data-archiver"
                          "10 * * * *"
                          (partial archive-job config db-pool)))
