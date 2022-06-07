(ns metlog-vault.data
  (:use metlog-common.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def sample-batch-size 200)

(defn open-db-pool []
  (-> (sql-file/open-pool {:name (config-property "db.subname" "metlog-vault")
                           :schema-path [ "sql/" ]})
      (sql-file/ensure-schema [ "metlog" 1 ])))

(def ^:dynamic *db* nil)

(defmacro with-db-connection [ db-pool & body ]
  `(binding [ *db* ~db-pool]
     ~@body))

(defn wrap-db-connection [ app db-pool ]
  (fn [ req ]
    (with-db-connection db-pool
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
      (let [query-latest-t (or (query-series-latest-sample-time series-name)
                               (java.util.Date. 0))]
        (swap! latest-sample-times assoc series-id query-latest-t)
        (log/debug "Latest sample times from DB" @latest-sample-times)
        query-latest-t))))

(defn check-latest-series-time [series-name t]
  (let [series-id (intern-series-name series-name)]
    (if-let [cached-latest-t (@latest-sample-times series-id)]
      (if (.after t cached-latest-t)
        (swap! latest-sample-times assoc series-id t)))))

(defquery store-data-samples [ samples ]
  (doseq [ sample-batch (partition-all sample-batch-size samples)]
    (log/info "Storing batch of" (count sample-batch) "samples.")
    (jdbc/insert-multi! *db* :sample
                        [:series_id :t :val]
                        (map (fn [ sample ]
                               [(intern-series-name (:series_name sample))
                                (:t sample)
                                (:val sample)])
                             sample-batch))))

;;; Monotonic store ensures that only new samples are stored. Existing
;;; samples, those older than the latest currently known in the
;;; database, are ignored. Useful for the USGS data, which is queried
;;; in 24 hours blocks, rather than streams of the latest samples.
(defn store-data-samples-monotonic [ samples ]
  (let [ new-samples (filter #(.after (:t %) (get-series-latest-sample-time (:series_name %)))
                             samples)]
    (doseq [ sample new-samples ]
      (check-latest-series-time (:series_name sample) (:t sample)))
    (store-data-samples new-samples)))

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

