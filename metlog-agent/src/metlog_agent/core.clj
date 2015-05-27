(ns metlog-agent.core
  (:gen-class)
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]
            [overtone.at-at :as at-at]
            [clj-http.client :as client]))

(def my-pool (at-at/mk-pool))

(def start-t (System/currentTimeMillis))

(def sensor-result-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn sensor-reading [ series-name val ]
  {:t (java.util.Date.)
   :series_name series-name
   :val val})

(defn enqueue-sensor-reading [ reading ]
  (log/info "enquing sensor reading" reading)
  (.add sensor-result-queue reading))

(defn poll-sensor-1 []
  (enqueue-sensor-reading
   (sensor-reading "sine" (Math/sin (/ (- (System/currentTimeMillis) start-t) 60000.0)))))

(defn poll-sensor-2 []
  (enqueue-sensor-reading
   (sensor-reading "cosine" (Math/cos (/ (- (System/currentTimeMillis) start-t) 60000.0)))))

(defn poll-w1-sensor [ path]
  (with-open [rdr (clojure.java.io/reader path)]
  (let [ line (second (line-seq rdr)) ]
    (if (not (nil? line))
      (enqueue-sensor-reading
       (sensor-reading "basement-temp"
        (/ (Double/valueOf (.substring line 29)) 1000.0)))))))

(defn poll-sensors []
  (log/debug "poll-sensors")
  (locking sensor-result-queue
    (poll-sensor-1)
    (poll-sensor-2)
    (if-let [ sensor-path (config-property "sensor.path") ]
      (poll-w1-sensor sensor-path))))

(defn do-update [ snapshot ]
  (let [url (config-property "vault.url" "http://localhost:8080/data")
        data { :body (pr-str snapshot)}]
    (log/info "Posting" data " to " url)
    (client/post url data)))

(defn update-vault []
  (log/debug "update-vault")
  (let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
    (locking sensor-result-queue
      (.drainTo sensor-result-queue snapshot))
    (do-update (seq snapshot))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (at-at/every 15000 (exception-barrier poll-sensors) my-pool)
  (at-at/every 60000 (exception-barrier update-vault) my-pool)
  (println "Running."))


