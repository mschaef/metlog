(ns metlog-vault.data
  (:use metlog-common.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))


(def db-connection
  (delay (sql-file/open-hsqldb-file-conn (config-property "db.subname" "metlog-db")  "metlog" 0)))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ & body ]
  `(binding [ *db* @db-connection ]
     ~@body))

(defn wrap-db-connection [ app ]
  (fn [ req ]
    (with-db-connection
      (app req))))


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
  (let [ series-name (.trim (name (or series-name ""))) ]
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

(defn get-series-id [ series-name ]
  (query-scalar *db* [(str "SELECT series_id"
                           " FROM series"
                           " WHERE series_name=?")
                      series-name]))

(defn get-series-names []
  (map :series_name
       (query-all *db* [(str "SELECT series_name"
                             " FROM series")])))

(defn get-data-for-series-name [ series-name begin-t end-t]
  (log/info "Getting series data for " series-name [ begin-t end-t ])
  (map #(assoc % :t (.getTime (:t %)))
   (query-all *db* [(str "SELECT sample.t, sample.val"
                         " FROM sample, series"
                         " WHERE sample.series_id = series.series_id"
                         "   AND series.series_name = ?"
                         "   AND UNIX_MILLIS(t-session_timezone()) > ?"
                         "   AND UNIX_MILLIS(t-session_timezone()) < ?"
                         " ORDER BY t")
                    series-name
                    begin-t
                    end-t])))


