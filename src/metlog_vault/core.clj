 (ns metlog-vault.core
   (:use playbook.core
         metlog-vault.util
         compojure.core
         sql-file.middleware)
   (:require [taoensso.timbre :as log]
             [metlog-vault.data :as data]
             [metlog-vault.scheduler :as scheduler]
             [sql-file.core :as sql-file]
             [metlog-vault.data :as data]
             [metlog-vault.web :as web]
             [metlog-vault.routes :as routes]
             [metlog-vault.archiver :as archiver]))

(def jvm-runtime (java.lang.Runtime/getRuntime))

(defn- make-sample [ series-name value ]
  {:t (java.util.Date.)
   :series_name series-name
   :val value})

(defn- queued-data-sink [ scheduler db-pool ]
  (let [ sample-queue (java.util.concurrent.LinkedBlockingQueue.) ]
    (scheduler/schedule-job
     scheduler "sample-ingress-queue" "*/1 * * * *"
     #(doto sample-queue
        (.add (make-sample "vault-ingress-queue-size" (.size sample-queue)))
        (.add (make-sample "vault-free-memory" (.freeMemory jvm-runtime)))))

    (scheduler/schedule-job
     scheduler "sample-jvm-stats" "0 * * * *"
     #(doto sample-queue
        (.add (make-sample "vault-total-memory" (.totalMemory jvm-runtime)))
        (.add (make-sample "vault-max-memory" (.maxMemory jvm-runtime)))))

    (scheduler/schedule-job
     scheduler "store-ingress-queue" "*/1 * * * *"
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
  {:name (or (config-property "db.subname")
             (get-in config [:vault :db :subname] "metlog-vault"))
   :schema-path [ "sql/" ]
   :schemas [[ "metlog" 3 ]]})

(defn start-app [ config ]
  (with-daemon-thread 'vault-webserver
    (log/info "Starting vault with config: " (:vault config))
    (sql-file/with-pool [db-pool (db-conn-spec config)]
      (let [scheduler (scheduler/start)
            healthchecks (atom {})]
        (archiver/start config scheduler db-pool)
        (web/start-webserver config
                             db-pool
                             (routes/all-routes
                              (queued-data-sink scheduler db-pool)
                              healthchecks))))))

