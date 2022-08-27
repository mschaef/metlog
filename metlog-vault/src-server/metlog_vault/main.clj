(ns metlog-vault.main
  (:gen-class :main true)
  (:use metlog-common.core
        metlog-vault.util)
  (:require [metlog-common.logging :as logging]
            [metlog-common.config :as config]
            [taoensso.timbre :as log]
            [figwheel.main.api :as figwheel]
            [sql-file.core :as sql-file]
            [metlog-vault.data :as data]
            [metlog-vault.core :as core]
            [metlog-vault.web :as web]
            [metlog-vault.routes :as routes]
            [metlog-vault.scheduler :as scheduler]
            [metlog-vault.archiver :as archiver]))

(defn db-conn-spec [ config ]
  {:name (or (config-property "db.subname")
             (get-in config [:db :subname] "metlog-vault"))
   :schema-path [ "sql/" ]
   :schemas [[ "metlog" 2 ]]})

(defn app-start [ config ]
  (sql-file/with-pool [db-pool (db-conn-spec config)]
    (let [scheduler (scheduler/start)
          data-sink (core/queued-data-sink scheduler db-pool)]
      (archiver/start config scheduler db-pool)
      (web/start-webserver (config-property "http.port" 8080)
                           db-pool
                           (routes/all-routes data-sink)))))


(defn -main [& args]
  (let [config (config/load-config)]
    (logging/setup-logging config [[#{"hsqldb.*" "com.zaxxer.hikari.*"} :warn]])
    (when (:development-mode config)
      (figwheel/start {:mode :serve
                       :open-url "http://localhost:8080"} "dev"))
    (app-start config)
    (log/info "end run.")))

