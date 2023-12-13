(ns metlog-vault.healthcheck-service
  (:use compojure.core
        playbook.core
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [compojure.route :as route]
            [clojure.edn :as edn]
            [metlog-vault.data :as data]))


(defn notice-healthcheck [ req ]
  (log/debug "Incoming healthcheck, content-type:" (:content-type req))
  (let [ healthcheck-data (if (= "application/transit+json" (:content-type req))
                   (read-transit (slurp (:body req)))
                   (edn/read-string (slurp (:body req))))]
    (log/info "Healthcheck" healthcheck-data))
  "Incoming healthcheck accepted.")

(defn all-routes [ ]
  (routes
   (POST "/healthcheck" req
     (notice-healthcheck req))))
