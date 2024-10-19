(ns metlog-agent.sensor
  (:use playbook.core
        metlog-agent.core)
  (:require [taoensso.timbre :as log]
            [clojure.java.io :as jio]
            [clj-http.client :as http]
            [clj-time.format :as time-format]
            [clj-time.coerce :as time-coerce]))

(defn ensure-number [ val ]
  (if (number? val)
    val
    (try-parse-double val)))

(defn constrain-sensor-range [ sensor-val low high ]
  (and sensor-val
       (>= sensor-val low)
       (<= sensor-val high)
       sensor-val))

(defn http-request-json [ url ]
  (let [response (http/get url)]
    (and (= 200 (:status response))
         (try-parse-json (:body response)))))

(defn http-request-text [ url ]
  (let [response (http/get url)]
    (and (= 200 (:status response))
         (:body response))))

(defn directory-space-used [ dir ]
  (apply + (map #(.length %) (file-seq (clojure.java.io/file dir)))))
