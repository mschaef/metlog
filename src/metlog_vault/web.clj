(ns metlog-vault.web
  (:use playbook.core
        compojure.core
        sql-file.middleware
        [ring.middleware resource
                         not-modified
                         content-type
                         browser-caching])
  (:require [taoensso.timbre :as log]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :as ring-reload]
            [ring.middleware.json :as ring-json]
            [compojure.handler :as handler]
            [metlog-vault.data :as data]))

(defn wrap-request-logging [ app ]
  (fn [req]
    (log/trace 'REQ (:request-method req) (:uri req) (:params req))
    (let [begin-t (. System (nanoTime))
          resp (app req)]
      (log/debug 'RESP (:status resp) (:request-method req) (:uri req)
                 "-" (/ (- (. System (nanoTime)) begin-t) 1000000.0))
      resp)))

(defn- wrap-dev-support [ handler dev-mode ]
  (cond-> (wrap-request-logging handler)
    dev-mode (ring-reload/wrap-reload)))

(defn wrap-request-thread-naming [ app ]
  (fn [ req ]
    (let [thread (Thread/currentThread)
          initial-thread-name (.getName thread)]
      (try
        (.setName thread (str initial-thread-name " " (:request-method req) " " (:uri req)))
        (app req)
        (finally
          (.setName thread initial-thread-name))))))

(defn start-webserver [ config db-pool routes ]
  (let [http-port (:http-port (:vault config))
        http-thread-count (:http-thread-count (:vault config))]
    (log/info "Starting Vault Webserver on port" http-port
              (str "(HTTP threads=" http-thread-count ")"))
    (let [server (jetty/run-jetty (-> routes
                                      (wrap-content-type)
                                      (wrap-browser-caching {"text/javascript" 360000
                                                             "text/css" 360000})
                                      (wrap-db-connection db-pool)
                                      (wrap-not-modified)
                                      (wrap-dev-support (:development-mode config))
                                      (ring-json/wrap-json-response)
                                      (handler/site)
                                      (wrap-request-thread-naming))
                                  {:port http-port
                                   :join? false
                                   :min-threads http-thread-count
                                   :max-threads http-thread-count})]
      (add-shutdown-hook
       (fn []
         (log/info "Shutting down webserver")
         (.stop server)))
      (.join server))))
