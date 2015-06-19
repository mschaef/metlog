(ns metlog-agent.core
  (:gen-class)
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]
            [overtone.at-at :as at-at]
            [clj-http.client :as client]))

(def my-pool (at-at/mk-pool))

(def start-t (System/currentTimeMillis))

(def sensor-result-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn enqueue-sensor-reading [ series-name val ]
  (let [ reading {:t (java.util.Date.)
                  :series_name series-name
                  :val val}]
    (log/info "enquing sensor reading" reading)
    (.add sensor-result-queue reading)))

(def sensor-defs (atom {}))

(defn add-sensor-def [ sensor-name sensor-fn ]
  (swap! sensor-defs assoc sensor-name sensor-fn))

(defmacro defsensor [ name & body ]
  `(add-sensor-def ~name (fn [] ~@body)))

(defsensor "sine" (Math/sin (/ (- (System/currentTimeMillis) start-t) 60000.0)))
(defsensor "cosine" (Math/cos (/ (- (System/currentTimeMillis) start-t) 60000.0)))

(defn read-w1-sensor-at-path [ sensor-path ]
  (with-open [rdr (clojure.java.io/reader sensor-path)]
    (let [ line (second (line-seq rdr)) ]
      (and (not (nil? line))
           (/ (Double/valueOf (.substring line 29)) 1000.0)))))

(if-let [ sensor-path (config-property "sensor.path") ]
  (defsensor "basement-temp"
    (read-w1-sensor-at-path sensor-path)))

(defn poll-sensors []
  (log/debug "poll-sensors")
  (let [ current-sensor-defs @sensor-defs ]
    (doseq [ sensor-name (keys current-sensor-defs) ]
      (if-let [ sensor-value ((get current-sensor-defs sensor-name)) ]
        (enqueue-sensor-reading sensor-name sensor-value)))))

(defn take-result-queue-snapshot []
  (let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
    (locking sensor-result-queue
      (.drainTo sensor-result-queue snapshot))
    (seq snapshot)))

(defn update-vault []
  (log/debug "update-vault")
  (let [url (config-property "vault.url" "http://localhost:8080/data")
        data { :body (pr-str (take-result-queue-snapshot))}]
    (log/info "Posting" data " to " url)
    (client/post url data)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (at-at/every 15000 (exception-barrier poll-sensors) my-pool)
  (at-at/every 60000 (exception-barrier update-vault) my-pool)
  (println "Running."))


