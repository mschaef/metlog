(ns metlog-vault.main
  (:gen-class :main true)
  (:use metlog-common.core
        metlog-vault.util)
  (:require [clojure.tools.logging :as log]
            [metlog-vault.data :as data]
            [metlog-vault.core :as core]
            [metlog-vault.web :as web]
            [metlog-vault.routes :as routes]
            [metlog-vault.scheduler :as scheduler]))

(defn -main
  []
  (log/info "Starting Vault -" (get-version))
  (let [db-pool (data/open-db-pool)
        scheduler (scheduler/start)
        data-sink (core/queued-data-sink scheduler db-pool)]
    (web/start-webserver (config-property "http.port" 8080)
                         db-pool
                         (routes/all-routes data-sink)))
  (log/info "end run."))
