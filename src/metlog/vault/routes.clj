(ns metlog.vault.routes
  (:use compojure.core
        playbook.core
        metlog.util
        metlog.vault.util)
  (:require [compojure.route :as route]
            [metlog.vault.data-service :as data-service]
            [metlog.vault.healthcheck-service :as healthcheck-service]
            [metlog.vault.dashboard :as dashboard]))

(defn all-routes [ store-samples healthchecks ]
  (routes
   (context "/agent" []
     (routes
      (data-service/all-routes store-samples healthchecks)
      (healthcheck-service/all-routes healthchecks)))

   ;; For backward compatability with unmigrated agents
   (data-service/all-routes store-samples healthchecks)

   (context "/dashboard" []
     (dashboard/all-routes healthchecks))

   (GET "/" []
     (dashboard/redirect-to-default-dashboard))

   (route/resources (str "/" (get-version)))
   (route/resources "/")

   (route/not-found "Resource Not Found")))
