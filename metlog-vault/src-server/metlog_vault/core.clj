 (ns metlog-vault.core
   (:use metlog-common.core
         metlog-vault.util
         compojure.core
         sql-file.middleware)
   (:require [taoensso.timbre :as log]
             [metlog-vault.data :as data]
             [metlog-vault.scheduler :as scheduler]))

(def jvm-runtime (java.lang.Runtime/getRuntime))

(defn- make-sample [ series-name value ]
  {:t (java.util.Date.)
   :series_name series-name
   :val value})

(defn queued-data-sink [ scheduler db-pool ]
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
          (log/info "Storing " (count snapshot) " samples.")
          (with-db-connection db-pool
            (data/store-data-samples-monotonic (seq snapshot))))))

    (fn [ samples ]
      (log/info "Enqueuing " (count samples) " samples for later storage.")
      (doseq [ sample samples ]
        (.add sample-queue sample)))))

