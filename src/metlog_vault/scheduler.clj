(ns metlog-vault.scheduler
  (:use playbook.core)
  (:require [taoensso.timbre :as log]
            [playbook.config :as config]))

(defn start [ ]
  (log/info "Starting Scheduler")
  (doto (it.sauronsoftware.cron4j.Scheduler.)
    (.setDaemon true)
    (.start)))

(defn wrap-with-current-config [ f ]
  (let [ config (config/cval) ]
    #(config/with-config config
       (f))))

(defn schedule-job [ scheduler job-name job-fn ]
  (if-let [ cron (config/cval :job-schedule job-name)]
    (let [job-lock (java.util.concurrent.locks.ReentrantLock.)]
      (log/info "Background job scheduled (cron:" cron  "):" job-name )
      (.schedule scheduler cron
                 (wrap-with-current-config
                   #(if (.tryLock job-lock)
                      (try
                        (with-exception-barrier (str "scheduled job:" job-name)
                          (log/info "Scheduled job:" job-name)
                          (job-fn))
                        (finally
                          (.unlock job-lock)))
                      (log/info "Cannot run scheduled job reentrantly:" job-name)))))
    (log/warn "Background job not scheduled, no job-schedule entry:" job-name))
  scheduler)
