(ns metlog-vault.routes
  (:use compojure.core
        playbook.core
        metlog-vault.util)
  (:require [compojure.route :as route]
            [metlog-vault.data-service :as data-service]
            [metlog-vault.dashboard :as dashboard]))

(defn all-routes [ store-samples ]
  (routes
   (data-service/all-routes store-samples)

   (context "/dashboard/:dashboard-id" [ dashboard-id ]
     (dashboard/all-routes dashboard-id))

   (route/resources (str "/" (get-version)))

   (GET "/" []
     (dashboard/redirect-to-default-dashboard))

   (route/not-found "Resource Not Found")))
