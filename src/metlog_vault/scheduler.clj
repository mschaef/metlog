(ns metlog-vault.scheduler
  (:use playbook.core)
  (:require [taoensso.timbre :as log]))

(defn start [ ]
  (log/info "Starting Scheduler")
  (doto (it.sauronsoftware.cron4j.Scheduler.)
    (.setDaemon true)
    (.start)))

(defn schedule-job [ scheduler desc cron job-fn ]
  (let [job-lock (java.util.concurrent.locks.ReentrantLock.)]
    (log/info "Background job scheduled (cron:" cron  "):" desc )
    (.schedule scheduler cron
               #(if (.tryLock job-lock)
                  (try
                    (with-exception-barrier (str "scheduled job:" desc)
                      (log/info "Scheduled job:" desc)
                      (job-fn))
                    (finally
                      (.unlock job-lock)))
                  (log/info "Cannot run scheduled job reentrantly:" desc)))))
