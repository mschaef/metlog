(ns metlog-vault.main
  (:gen-class :main true)
  (:use metlog-common.core
        metlog-vault.util)
  (:require [clojure.tools.logging :as log]
            [figwheel.main.api :as figwheel]
            [sql-file.core :as sql-file]
            [metlog-vault.config :as config]
            [metlog-vault.data :as data]
            [metlog-vault.core :as core]
            [metlog-vault.web :as web]
            [metlog-vault.routes :as routes]
            [metlog-vault.scheduler :as scheduler]))

(defn db-conn-spec [ config ]
  {:name (or (config-property "db.subname")
             (get-in config [:db :subname] "metlog-vault"))
   :schema-path [ "sql/" ]
   :schemas [[ "metlog" 1 ]]})

(defn app-start [ config ]
  (sql-file/with-pool [db-conn (db-conn-spec config)]
    (let [scheduler (scheduler/start)
          data-sink (core/queued-data-sink scheduler db-conn)]
      (web/start-webserver (config-property "http.port" 8080)
                           db-conn
                           (routes/all-routes data-sink)))))


(defn -main [& args]
  (let [config (config/load-config)]
    (log/info "Starting App" (:app config))
    (when (:development-mode config)
      (log/warn "=== DEVELOPMENT MODE ===")
      (figwheel/start {:mode :serve
                       :open-url "http://localhost:8080"} "dev"))
    (app-start config)
    (log/info "end run.")))
