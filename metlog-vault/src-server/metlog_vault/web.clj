(ns metlog-vault.web
  (:use metlog-common.core
        compojure.core
        [ring.middleware resource
                         not-modified
                         content-type
                         browser-caching])
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [compojure.handler :as handler]
            [metlog-vault.data :as data]))

(def http-thread-count 8)

(defn wrap-request-logging [ app ]
  (fn [req]
    (log/trace 'REQ (:request-method req) (:uri req) (:params req))
    (let [begin-t (. System (nanoTime))
          resp (app req)]
      (log/debug 'RESP (:status resp) (:request-method req) (:uri req)
                 "-" (/ (- (. System (nanoTime)) begin-t) 1000000.0))
      resp)))

(defn wrap-request-thread-naming [ app ]
  (fn [ req ]
    (let [thread (Thread/currentThread)
          initial-thread-name (.getName thread)]
      (try
        (.setName thread (str initial-thread-name " " (:request-method req) " " (:uri req)))
        (app req)
        (finally
          (.setName thread initial-thread-name))))))

(defn start-webserver [ http-port routes ]
  (log/info "Starting Vault Webserver on port" http-port
            (str "(HTTP threads=" http-thread-count ")"))
  (let [server (jetty/run-jetty (-> routes
                                    (data/wrap-db-connection)
                                    (wrap-content-type)
                                    (wrap-browser-caching {"text/javascript" 360000
                                                           "text/css" 360000})
                                    (wrap-not-modified)
                                    (handler/site)
                                    (wrap-request-logging)
                                    (wrap-request-thread-naming))
                                {:port http-port
                                 :join? false
                                 :min-threads http-thread-count
                                 :max-threads http-thread-count})]
    (add-shutdown-hook
     (fn []
       (log/info "Shutting down webserver")
       (.stop server)))
    (.join server)))

