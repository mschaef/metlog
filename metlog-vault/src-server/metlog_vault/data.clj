(ns metlog-vault.data
  (:use metlog-common.core
        compojure.core
        sql-file.middleware)
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [sql-file.core :as sql-file]))

(def sample-batch-size 200)

(defn call-with-query-logging [ name actual-args fn ]
  (log/trace "query" name actual-args)
  (let [begin-t (. System (nanoTime))
        result (fn)]
    (log/debug "query time" name "-" (/ (- (. System (nanoTime)) begin-t) 1000000.0))
    result))

(defmacro defquery [ name lambda-list & body ]
  `(defn ~name ~lambda-list
     (call-with-query-logging '~name ~lambda-list (fn [] ~@body))))

(defquery get-series-id [ series-name ]
  (query-scalar (current-db-connection) [(str "SELECT series_id"
                                              " FROM series"
                                              " WHERE series_name=?")
                                         (str series-name)]))

(defquery add-series-name [ series-name ]
  (:series_id (first
               (jdbc/insert! (current-db-connection) :series
                             {:series_name series-name}))))

(def intern-series-name
  (memoize
   (fn [ series-name ]
     (let [ series-name (.trim (name (or series-name ""))) ]
       (if (= 0 (.length series-name))
         nil
         (with-db-transaction
           (or (get-series-id series-name)
               (add-series-name series-name))))))))

(defn lookup-series-id [ series-name ]
  (let [ series-name (.trim (name (or series-name ""))) ]
    (if (= 0 (.length series-name))
      nil
      (get-series-id series-name))))

(def latest-sample-times (atom {}))

(defquery query-series-latest-sample-time [ series-name ]
  (if-let [ t (query-scalar (current-db-connection) [(str "SELECT MAX(t) FROM sample"
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
    (jdbc/insert-multi! (current-db-connection) :sample
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
       (query-all (current-db-connection) [(str "SELECT series_name"
                                                " FROM series")])))

(defquery get-data-for-series-name [ series-name begin-t end-t ]
  (map #(assoc % :t (.getTime (:t %)))
   (query-all (current-db-connection) [(str "SELECT sample.t, sample.val"
                         " FROM sample"
                         " WHERE series_id = ?"
                         "   AND UNIX_MILLIS(t-session_timezone()) > ?"
                         "   AND UNIX_MILLIS(t-session_timezone()) < ?"
                         " ORDER BY t")
                    (lookup-series-id series-name)
                    begin-t
                    end-t])))

(defquery get-dashboard-definition [ name ]
  (query-scalar (current-db-connection) [(str "SELECT definition"
                           " FROM dashboard"
                           " WHERE name=?")
                      name]))

(defquery store-dashboard-definition [ name definition ]
  (with-db-transaction
    (if (get-dashboard-definition name)
      (jdbc/update! (current-db-connection) :dashboard
                    {:definition definition }
                    ["name = ?" name])
      (jdbc/insert! (current-db-connection) :dashboard
                    {:name name
                     :definition definition}))))

