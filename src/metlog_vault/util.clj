(ns metlog-vault.util
  (:use compojure.core)
  (:require [cognitect.transit :as transit]
            [clojure.data.json :as json]
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

(defn read-transit [ string ]
  (let [in (java.io.ByteArrayInputStream. (.getBytes string))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn success []
  (ring/response "ok"))
