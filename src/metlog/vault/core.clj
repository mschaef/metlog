(ns metlog.vault.core
  (:use playbook.core
        sql-file.middleware)
  (:require [taoensso.timbre :as log]
            [playbook.config :as config]
            [playbook.db :as db]
            [playbook.service :as service]
            [sql-file.core :as sql-file]
            [metlog.vault.data :as data]
            [metlog.vault.data-service :as data-service]
            [metlog.vault.web :as web]
            [metlog.vault.routes :as routes]
            [metlog.vault.archiver :as archiver]))

(def jvm-runtime (java.lang.Runtime/getRuntime))

(defn- ingress-queue-jobs
  "The jobs that sample the vault's own state into sample-queue, and
  the job that stores sample-queue's contents."
  [sample-queue]
  {:sample-ingress-queue
   #(doto sample-queue
      (.add (data-service/make-sample "vault-ingress-queue-size" (.size sample-queue)))
      (.add (data-service/make-sample "vault-free-memory" (.freeMemory jvm-runtime))))

   :sample-jvm-stats
   #(doto sample-queue
      (.add (data-service/make-sample "vault-total-memory" (.totalMemory jvm-runtime)))
      (.add (data-service/make-sample "vault-max-memory" (.maxMemory jvm-runtime))))

   :store-ingress-queue
   ;; Samples stay in sample-queue until they've been stored. The
   ;; job copies the queue, stores the copy, and only then removes
   ;; that many samples from the head. If the store fails (e.g. no
   ;; database connection available during CHECKPOINT DEFRAG), the
   ;; samples are still queued, in order, for the next run. This is
   ;; safe because this job is the queue's only consumer and
   ;; schedule-job never runs a job reentrantly; producers only ever
   ;; add to the tail.
   #(let [snapshot (vec (iterator-seq (.iterator sample-queue)))]
      (when (seq snapshot)
        (log/debug "Storing " (count snapshot) " samples.")
        (data/store-data-samples-monotonic snapshot)
        (dotimes [_ (count snapshot)]
          (.poll sample-queue))))})

(defn- queued-data-sink
  "The function the web side calls with incoming samples: it adds them
  to sample-queue, to be stored by the :store-ingress-queue job."
  [sample-queue]
  (fn [samples]
    (log/debug "Enqueuing " (count samples) " samples for later storage.")
    (doseq [sample samples]
      (.add sample-queue sample))))

(defn start-app
  "Run the vault (its database, scheduled jobs and web server) until
  the web server stops."
  []
  (log/info "Starting vault with config: " (config/cval :vault))
  (db/with-database {:schemas [["metlog" 3]]}
    ;; Healthcheck state is intentionally in-memory only. Agents post frequently,
    ;; so a missing entry after vault restart means the agent hasn't checked in yet
    ;; — which is itself useful signal. Persistence would only matter with a larger
    ;; number of agents where the baseline population needs to be remembered.
    (let [healthchecks (atom {})
          sample-queue (java.util.concurrent.LinkedBlockingQueue.)]
      (service/with-service
        {:jobs (merge (ingress-queue-jobs sample-queue)
                      {:defragment-database #(sql-file/checkpoint-defragment (current-db-connection))
                       :data-archiver archiver/archive-job}
                      (db/backup-jobs {}))
         :job-wrapper db/job-wrapper}
        (web/start-webserver (routes/all-routes (queued-data-sink sample-queue)
                                                healthchecks))))))
