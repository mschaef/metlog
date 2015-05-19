(ns metlog-agent.core
  (:gen-class)
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]
            [overtone.at-at :as at-at]
            [clj-http.client :as client]))

(def my-pool (at-at/mk-pool))

(def start-t (System/currentTimeMillis))

(defn poll-sensor-1 []
  {:t (java.util.Date.)
   :series_name "sine"
   :val (Math/sin (/ (- (System/currentTimeMillis) start-t) 250.0))})

(defn poll-sensor-2 []
  {:t (java.util.Date.)
   :series_name "cosine"
   :val (Math/cos (/ (- (System/currentTimeMillis) start-t) 250.0))})

(def sensor-result-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn poll-sensors []
  (log/debug "poll-sensors")
  (locking sensor-result-queue
    (.add sensor-result-queue (poll-sensor-1))
    (.add sensor-result-queue (poll-sensor-2))))

(defn do-update [ snapshot ]
  (client/post "http://localhost:8080/data" { :body (pr-str snapshot)}))

(defn update-vault []
  (log/debug "update-vault")
  (let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
    (locking sensor-result-queue
      (.drainTo sensor-result-queue snapshot))
    (do-update (seq snapshot))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (at-at/every 1000 (exception-barrier poll-sensors) my-pool)
  (at-at/every 5000 (exception-barrier update-vault) my-pool)
  (println "Running."))


