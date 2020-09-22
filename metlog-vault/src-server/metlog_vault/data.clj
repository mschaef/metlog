(ns metlog-vault.data
  (:use metlog-common.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def db-connection
  (delay (-> (sql-file/open-pool {:name (config-property "db.subname" "metlog-vault")
                                  :schema-path [ "sql/" ]})
             (sql-file/ensure-schema [ "metlog" 1 ]))))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ & body ]
  `(binding [ *db* @db-connection ]
     ~@body))

(defn wrap-db-connection [ app ]
  (fn [ req ]
    (with-db-connection
      (app req))))

(defn call-with-query-logging [ name actual-args fn ]
  (log/trace "query" name actual-args)
  (let [begin-t (. System (nanoTime))
        result (fn)]
    (log/debug "query time" name "-" (/ (- (. System (nanoTime)) begin-t) 1000000.0))
    result))

(defmacro defquery [ name lambda-list & body ]
  `(defn ~name ~lambda-list
     (call-with-query-logging '~name ~lambda-list (fn [] ~@body))))

(defmacro with-transaction [ & body ]
  `(jdbc/with-db-transaction [ db-trans# *db* ]
     (binding [ *db* db-trans# ]
       ~@body)))

(defquery get-series-id [ series-name ]
  (query-scalar *db* [(str "SELECT series_id"
                           " FROM series"
                           " WHERE series_name=?")
                      (str series-name)]))

(defquery add-series-name [ series-name ]
  (:series_id (first 
               (jdbc/insert! *db* :series
                             {:series_name series-name}))))

(def intern-series-name
  (memoize
   (fn [ series-name ]
     (let [ series-name (.trim (name (or series-name ""))) ]
       (if (= 0 (.length series-name))
         nil
         (with-transaction
           (or (get-series-id series-name)
               (add-series-name series-name))))))))

(def latest-sample-times (atom {}))

(defquery query-series-latest-sample-time [ series-name ]
  (if-let [ t (query-scalar *db* [(str "SELECT MAX(t) FROM sample"
                                       " WHERE series_id = ?")
                                  (intern-series-name series-name)])]
    t))

(defn get-series-latest-sample-time [ series-name ]
  (let [series-id (intern-series-name series-name)]
    (if-let [latest-t (@latest-sample-times series-id)]
      latest-t
      (let [query-latest-t (query-series-latest-sample-time series-name)]
        (swap! latest-sample-times assoc series-id query-latest-t)
        (log/info "Latest sample times from DB" @latest-sample-times)
        query-latest-t))))

(defn restrict-to-series [ samples series-name ]
  (filter #(= series-name (:series_name %)) samples))

(defn check-latest-series-time [series-id t]
  (if-let [cached-latest-t (@latest-sample-times series-id)]
    (if (.after t cached-latest-t)
      (swap! latest-sample-times assoc series-id t))))

(defquery store-data-samples [ samples ]
  (doseq [ sample samples ]
    (log/trace "Inserting sample: " sample)
    (let [series-id (intern-series-name (:series_name sample))]
      (check-latest-series-time series-id (:t sample))
      (jdbc/insert! *db* :sample
                    {:series_id series-id
                     :t (:t sample)
                     :val (:val sample)}))))

(defn store-data-samples-monotonic [ samples ]
  (doseq [series-samples (map #(restrict-to-series samples %) (set (map :series_name samples))) ]
    (let [series-name (:series_name (first series-samples))
          latest-t (get-series-latest-sample-time series-name)
          samples (if latest-t
                    (filter #(.after (:t %) latest-t) series-samples)
                    series-samples)]
      (store-data-samples samples))))

(defquery get-series-names []
  (map :series_name
       (query-all *db* [(str "SELECT series_name"
                             " FROM series")])))

(defquery get-data-for-series-name [ series-name begin-t end-t]
  (map #(assoc % :t (.getTime (:t %)))
   (query-all *db* [(str "SELECT sample.t, sample.val"
                         " FROM sample"
                         " WHERE series_id = ?"
                         "   AND UNIX_MILLIS(t-session_timezone()) > ?"
                         "   AND UNIX_MILLIS(t-session_timezone()) < ?"
                         " ORDER BY t")
                    (intern-series-name series-name)
                    begin-t
                    end-t])))

(defquery get-dashboard-definition [ name ]
  (query-scalar *db* [(str "SELECT definition"
                           " FROM dashboard"
                           " WHERE name=?")
                      name]))

(defquery store-dashboard-definition [ name definition ]
  (with-transaction
    (if (get-dashboard-definition name)
      (jdbc/update! *db* :dashboard
                    {:definition definition }
                    ["name = ?" name])
      (jdbc/insert! *db* :dashboard
                    {:name name
                     :definition definition}))))

