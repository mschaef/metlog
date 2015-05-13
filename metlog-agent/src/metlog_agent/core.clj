(ns metlog-agent.core
  (:gen-class)
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]
            [overtone.at-at :as at-at]
            [clj-http.client :as client]))

(def my-pool (at-at/mk-pool))

(defn poll-sensor []
  {:t (java.util.Date.)
   :val 42})

(def sensor-result-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn poll-sensors []
  (log/info "poll-sensors")
  (locking sensor-result-queue
    (.add sensor-result-queue (poll-sensor))))

(defn do-update [ snapshot ]
  (log/info "Starting update.")
  (let [ snapshot (seq snapshot) ]
    (doseq [ item  snapshot ]
      (log/info item))
    (client/post "http://localhost:8080/data"
                 { :body (pr-str snapshot)}))
  (log/info "Done with update."))

(defn update-vault []
  (log/info "update-vault")
  (let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
    (locking sensor-result-queue
      (.drainTo sensor-result-queue snapshot))
    (do-update snapshot)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (at-at/every 1000 (exception-barrier poll-sensors) my-pool)
  (at-at/every 5000 (exception-barrier update-vault) my-pool)
  (println "Running."))


