(ns metlog-vault.data-service
  (:use compojure.core
        playbook.core
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [compojure.route :as route]
            [clojure.edn :as edn]
            [metlog-vault.data :as data]))

(defn get-series-data [ params ]
  {:body
   (vec
    (data/get-data-for-series-name (:series-name params)
                                   (try-parse-long (:begin-t params))
                                   (try-parse-long (:end-t params))))})

(defn store-series-data [ store-samples req ]
  (log/debug "Incoming data, content-type:" (:content-type req))
  (let [ samples (if (= "application/transit+json" (:content-type req))
                   (read-transit (slurp (:body req)))
                   (edn/read-string (slurp (:body req))))]
    (store-samples samples))
  "Incoming data accepted.")

(defn all-routes [ store-samples ]
  (routes


   (POST "/data" req
     (store-series-data store-samples req))))
