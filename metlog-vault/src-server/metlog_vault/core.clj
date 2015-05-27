(ns metlog-vault.core
  (:gen-class :main true)
  (:use metlog-common.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as ring-resource]
            [ring.util.response :as ring]
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
                   :t (:t sample)
                   :val (:val sample)})))

(defn edn-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn get-series-id [ series-name ]
  (query-scalar *db* [(str "SELECT series_id"
                           " FROM series"
                           " WHERE series_name=?")
                      series-name]))

(defn get-latest-data-for-series-name [ series-name ]
  (let [ series-id (get-series-id series-name) ]
    (if-let [ val (query-first *db* [(str "SELECT sample.series_id, sample.t, sample.val"
                                          " FROM sample"
                                          " WHERE sample.series_id = ?"
                                          "   AND sample.t = (SELECT MAX(t) FROM sample WHERE series_id=?)")
                                     series-id series-id])]
      (edn-response val))))

(defn get-data-for-series-name [ series-name ]
  (edn-response
   (query-all *db* [(str "SELECT sample.t, sample.val"
                         " FROM sample, series"
                         " WHERE sample.series_id = series.series_id"
                         "   AND series.series_name=?"
                         " ORDER BY t")
                    series-name])))

(defn get-series-names [ ]
  (edn-response
   (map :series_name
        (query-all *db* [(str "SELECT series_name"
                              " FROM series")]))))

(defroutes all-routes
  (GET "/server-time" [ ]
    (edn-response (java.util.Date.)))

  (GET "/series-names" []
    (get-series-names))

  (GET "/data/:series-name" [ series-name ]
    (get-data-for-series-name series-name))

  (GET "/latest/:series-name" [ series-name ]
    (get-latest-data-for-series-name series-name))
  
  (POST "/data" req
    (store-data-samples (edn/read-string (slurp (:body req))))
    "Incoming data accepted.")
  
  (route/resources "/")
  (GET "/" [] (ring/redirect "/index.html"))
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
