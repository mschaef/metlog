(ns metlog-vault.core
  (:gen-class :main true)
  (:use metlog-common.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as ring-resource]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.edn :as edn]
            [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def db-connection
  (delay (sql-file/open-hsqldb-file-conn (config-property "db.subname" "metlog-db")  "metlog" 0)))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ & body ]
  `(binding [ *db* @db-connection ]
     ~@body))

(defmacro with-transaction [ & body ]
  `(jdbc/with-db-transaction [ db-trans# *db* ]
     (binding [ *db* db-trans# ]
       ~@body)))

(defn series-name-id [ series-name ]
  (query-scalar *db* [(str "SELECT series_id "
                           " FROM series "
                           " WHERE series_name=?")
                      series-name]))

(defn- add-series-name [ series-name ]
  (:series_id (first 
               (jdbc/insert! *db* :series
                             {:series_name series-name}))))


(defn intern-series-name [ series-name ]
  (let [ series-name (.trim (or series-name "")) ]
    (if (= 0 (.length series-name))
      nil
      (with-transaction
        (or (series-name-id series-name)
            (add-series-name series-name))))))

(defn store-data-samples [ samples ]
  (doseq [ sample samples ]
    (log/debug "Inserting sample: " sample)
    (jdbc/insert! *db* :sample
                  {:series_id (intern-series-name (:series_name sample))
                   :t (java.util.Date.)
                   :val (:val sample)})))

(defroutes all-routes
  (GET "/heartbeat" [ ]
    "heartbeat")

  (POST "/data" req
    (store-data-samples (edn/read-string (slurp (:body req))))
    "Incoming data accepted.")
  
  (route/resources "/")
  (route/not-found "Resource Not Found"))

(defn wrap-request-logging [ app ]
  (fn [req]
    (log/debug 'REQUEST (:request-method req) (:uri req))
    (let [resp (app req)]
      (log/trace 'RESPONSE (:status resp))
      resp)))

(defn wrap-db-connection [ app ]
  (fn [ req ]
    (with-db-connection
      (app req))))

(def handler (-> all-routes
                 (wrap-db-connection)
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
