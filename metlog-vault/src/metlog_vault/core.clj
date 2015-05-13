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
    (log/debug "Data" (edn/read-string (slurp (:body req))))
    "post accepted")
  
  (route/resources "/")
  (route/not-found "Resource Not Found"))

(defn wrap-request-logging [ app ]
  (fn [req]
    (log/debug 'REQUEST (:request-method req) (:uri req))
    (let [resp (app req)]
      (log/trace 'RESPONSE (:status resp))
      resp)))

(def handler (-> all-routes
                 (ring-resource/wrap-resource "public")
                 (wrap-request-logging)
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
