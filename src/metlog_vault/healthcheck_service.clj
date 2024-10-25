(ns metlog-vault.healthcheck-service
  (:use compojure.core
        playbook.core
        metlog.util
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [compojure.route :as route]
            [clojure.edn :as edn]
            [metlog-vault.data :as data]))


(defn notice-healthcheck [ req healthchecks ]
  (log/debug "Incoming healthcheck, content-type:" (:content-type req))
  (let [ healthcheck-data (if (= "application/transit+json" (:content-type req))
                            (read-transit (slurp (:body req)))
                            (edn/read-string (slurp (:body req))))]
    (swap! healthchecks assoc (:name healthcheck-data) healthcheck-data))
  "Incoming healthcheck accepted.")

(defn all-routes [ healthchecks ]
  (routes
   (POST "/healthcheck" req
     (notice-healthcheck req healthchecks))))
