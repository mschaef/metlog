 (ns metlog-vault.core
   (:use playbook.core
         metlog-vault.util
         compojure.core
         sql-file.middleware)
   (:require [taoensso.timbre :as log]
             [metlog-vault.data :as data]
             [metlog-vault.data-service :as data-service]
             [metlog-vault.scheduler :as scheduler]
             [sql-file.core :as sql-file]
             [playbook.config :as config]
             [metlog-vault.data :as data]
             [metlog-vault.web :as web]
             [metlog-vault.routes :as routes]
             [metlog-vault.archiver :as archiver]))

(def jvm-runtime (java.lang.Runtime/getRuntime))

(defn wrap-with-current-config [ f ]
  (let [ config (config/cval) ]
    #(config/with-config config
       (f))))

(defn- queued-data-sink [ scheduler db-pool ]
  (let [ sample-queue (java.util.concurrent.LinkedBlockingQueue.) ]
    (scheduler/schedule-job
     scheduler :sample-ingress-queue
     #(doto sample-queue
        (.add (data-service/make-sample "vault-ingress-queue-size" (.size sample-queue)))
        (.add (data-service/make-sample "vault-free-memory" (.freeMemory jvm-runtime)))))

    (scheduler/schedule-job
     scheduler :sample-jvm-stats
     #(doto sample-queue
        (.add (data-service/make-sample "vault-total-memory" (.totalMemory jvm-runtime)))
        (.add (data-service/make-sample "vault-max-memory" (.maxMemory jvm-runtime)))))

    (scheduler/schedule-job
     scheduler :store-ingress-queue
     #(let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
        (locking sample-queue
          (.drainTo sample-queue snapshot))
        (when (> (count snapshot) 0)
          (log/debug "Storing " (count snapshot) " samples.")
          (with-db-connection db-pool
            (data/store-data-samples-monotonic (seq snapshot))))))

    (fn [ samples ]
      (log/debug "Enqueuing " (count samples) " samples for later storage.")
      (doseq [ sample samples ]
        (.add sample-queue sample)))))

(defn- db-conn-spec [ config ]
  {:name (or (config/property "db.subname")
             (get-in config [:vault :db :subname] "metlog-vault"))
   :schema-path [ "sql/" ]
   :schemas [[ "metlog" 3 ]]})

(defn start-app [ ]
  (with-daemon-thread 'vault-webserver
    (log/info "Starting vault with config: " (config/cval :vault))
    (sql-file/with-pool [db-pool (db-conn-spec (config/cval))]
      (let [scheduler (scheduler/start)
            healthchecks (atom {})]
        (archiver/start scheduler db-pool)
        (web/start-webserver db-pool
                             (routes/all-routes
                              (queued-data-sink scheduler db-pool)
                              healthchecks))))))

