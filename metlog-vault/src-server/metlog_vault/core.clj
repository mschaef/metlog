 (ns metlog-vault.core
   (:use metlog-common.core
         metlog-vault.util
         compojure.core)
   (:require [clojure.tools.logging :as log]
             [overtone.at-at :as at-at]
             [metlog-vault.data :as data]))

(def my-pool (at-at/mk-pool))

(def jvm-runtime (java.lang.Runtime/getRuntime))

(defn- make-sample [ series-name value ]
  {:t (java.util.Date.)
   :series_name series-name
   :val value})

(defn queued-data-sink [ db-pool ]
  (let [ sample-queue (java.util.concurrent.LinkedBlockingQueue.) ]
    (at-at/every 60000
                 (exception-barrier
                  #(doto sample-queue
                     (.add (make-sample "vault-ingress-queue-size" (.size sample-queue)))
                     (.add (make-sample "vault-free-memory" (.freeMemory jvm-runtime))))
                  "Record per-minute JVM stats")
                 my-pool)

    (at-at/every (* 60 60000) ; Every hour
                 (exception-barrier
                  #(doto sample-queue
                     (.add (make-sample "vault-total-memory" (.totalMemory jvm-runtime)))
                     (.add (make-sample "vault-max-memory" (.maxMemory jvm-runtime))))
                  "Record hourly JVM stats")
                 my-pool)

    (at-at/interspaced 15000
                       (exception-barrier
                        #(let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
                           (locking sample-queue
                             (.drainTo sample-queue snapshot))
                           (when (> (count snapshot) 0)
                             (log/info "Storing " (count snapshot) " samples.")
                             (data/with-db-connection db-pool
                               (data/store-data-samples-monotonic (seq snapshot)))))
                        "Store ingress queue contents")
                       my-pool)
    (fn [ samples ]
      (log/info "Enqueuing " (count samples) " samples for later storage.")
      (doseq [ sample samples ]
        (.add sample-queue sample)))))

