(ns metlog.util
  (:use compojure.core)
  (:require [taoensso.timbre :as log]
            [cognitect.transit :as transit]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [ring.util.response :as ring]))

(defmacro get-version []
  ;; Capture compile-time property definition from Lein
  (or (System/getProperty "metlog.version")
      "dev"))

(defn pr-transit [ val ]
  (let [out (java.io.ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer val)
    (.toString out)))

(defn- read-transit [ string ]
  (let [in (java.io.ByteArrayInputStream. (.getBytes string))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn read-request-body [ req ]
  (let [{content-type :content-type} req]
    (log/debug "Incoming data, content-type:" content-type)
    (let [req-body (slurp (:body req))]
      (case content-type
        "application/json" (json/read-str req-body :key-fn keyword)
        "application/transit+json" (read-transit req-body)
        (edn/read-string req-body)))))
