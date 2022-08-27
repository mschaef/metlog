(ns metlog-vault.scheduler
  (:use metlog-common.core)
  (:require [taoensso.timbre :as log]))

(defn start [ ]
  (doto (it.sauronsoftware.cron4j.Scheduler.)
    (.setDaemon true)
    (.start)))

(defn schedule-job [ scheduler desc cron job-fn ]
  (let [job-lock (java.util.concurrent.locks.ReentrantLock.)
        job-fn (exception-barrier job-fn (str "scheduled job:" desc))]
    (log/info "Background job scheduled (cron:" cron  "):" desc )
    (.schedule scheduler cron
               #(if (.tryLock job-lock)
                  (try
                    (log/info "Running scheduled job: " desc)
                    (job-fn)
                    (log/info "End scheduled job: " desc)
                    (finally
                      (.unlock job-lock)))
                  (log/warn "Cannot run scheduled job reentrantly: " desc)))))
