(ns metlog-vault.routes
  (:use compojure.core
        playbook.core
        metlog-vault.util)
  (:require [compojure.route :as route]
            [metlog-vault.data-service :as data-service]
            [metlog-vault.healthcheck-service :as healthcheck-service]
            [metlog-vault.dashboard :as dashboard]))

(defn all-routes [ store-samples healthchecks ]
  (routes
   (context "/agent" []
     (routes
      (data-service/all-routes store-samples)
      (healthcheck-service/all-routes healthchecks)))

   (context "/dashboard" []
     (dashboard/all-routes))

   (route/resources (str "/" (get-version)))

   (GET "/" []
     (dashboard/redirect-to-default-dashboard))

   (route/not-found "Resource Not Found")))
