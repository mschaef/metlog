(ns metlog-vault.healthcheck-service
  (:use compojure.core
        playbook.core
        metlog.util
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [compojure.route :as route]
            [clojure.edn :as edn]
            [metlog-vault.data :as data]))

(defn notice-healthcheck [ healthcheck-data healthchecks ]
  (swap! healthchecks assoc (:name healthcheck-data) healthcheck-data))

(defn all-routes [ healthchecks ]
  (routes
   (POST "/healthcheck" req
     (notice-healthcheck (read-request-body req) healthchecks)
     (respond-success))))
