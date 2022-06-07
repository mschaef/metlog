(ns metlog-vault.scheduler
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]))

(defn start [ ]
  (doto (it.sauronsoftware.cron4j.Scheduler.)
    (.setDaemon true)
    (.start)))

(defn schedule-job [ scheduler desc cron job-fn ]
  (let [ job-fn (exception-barrier job-fn (str "scheduled job:" desc))]
    (log/info "Background job scheduled (cron:" cron  "):" desc )
    (.schedule scheduler cron
               #(do
                  (log/info "Running scheduled job: " desc)
                  (job-fn)
                  (log/info "End scheduled job: " desc)))))
