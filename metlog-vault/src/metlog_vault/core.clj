(ns metlog-vault.core
  (:gen-class :main true)
  (:use metlog-common.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as ring-resource]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.edn :as edn]))

(defroutes all-routes
  (GET "/heartbeat" [ ]
    "heartbeat")

  (POST "/data" req 
    (log/debug "Incoming POST: " req)
    (log/debug "Data" (edn/read-string (log/spy :error (slurp (:body req)))))
    "post accepted"))

(def site-routes
  (routes all-routes
          (route/resources "/")
          (route/not-found "Resource Not Found")))

(def handler (-> site-routes
                 (ring-resource/wrap-resource "public")
                 (handler/api)))

(defn start-webserver [ http-port ]
  (log/info "Starting Vault Webserver on port" http-port)
  (let [server (jetty/run-jetty handler  { :port http-port :join? false })]
    (add-shutdown-hook
     (fn []
       (log/info "Shutting down webserver")
       (.stop server)))
    (.join server)))

(defn -main
  []
  (log/info "Starting Vault")
  (start-webserver (config-property "http.port" 8080))
  (log/info "end run."))
