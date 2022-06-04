(ns metlog-vault.util
  (:require [cognitect.transit :as transit]))

(defmacro get-version []
  ;; Capture compile-time property definition from Lein
  (or (System/getProperty "metlog-vault.version")
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

(defn transit-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/transit+json"}
   :body (pr-transit data)})

