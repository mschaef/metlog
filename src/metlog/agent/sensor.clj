(ns metlog.agent.sensor
  (:use playbook.core
        metlog.agent.core)
  (:require [taoensso.timbre :as log]
            [clojure.java.io :as jio]
            [clj-http.client :as http]
            [clj-time.format :as time-format]
            [clj-time.coerce :as time-coerce]))

(defn ensure-number [val]
  (if (number? val)
    val
    (try-parse-double val)))

(defn constrain-sensor-range [sensor-val low high]
  (and sensor-val
       (>= sensor-val low)
       (<= sensor-val high)
       sensor-val))

(defn http-request-json
  "GET url and parse the body as JSON. opts are passed through to
  clj-http, e.g. {:connection-timeout 10000 :socket-timeout 30000}."
  ([url] (http-request-json url {}))
  ([url opts]
   (let [response (http/get url opts)]
     (and (= 200 (:status response))
          (try-parse-json (:body response))))))

(defn http-request-text
  "GET url and return the body as text. opts are passed through to
  clj-http, e.g. {:connection-timeout 10000 :socket-timeout 30000}."
  ([url] (http-request-text url {}))
  ([url opts]
   (let [response (http/get url opts)]
     (and (= 200 (:status response))
          (:body response)))))

(defn directory-space-used [dir]
  (apply + (map #(.length %) (file-seq (clojure.java.io/file dir)))))
