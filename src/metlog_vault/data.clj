(ns metlog-vault.data
  (:use playbook.core
        compojure.core
        sql-file.middleware
        sql-file.sql-util)
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [metlog-vault.queries :as query]))

(def sample-batch-size 200)

(defn get-series-id [ series-name ]
  (scalar-result
   (query/get-series-id { :series_name series-name }
                        { :connection (current-db-connection) })))

(defn add-series-name [ series-name ]
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

(defn delete-old-samples [ series-id archive-time ]
  (query/delete-old-samples! {:seried_id series-id
                              :archive_time archive-time}
                             { :connection (current-db-connection) }))

(defn archive-old-samples [ series-id archive-time ]
  (query/archive-old-samples! {:seried_id series-id
                               :archive_time archive-time}
                              { :connection (current-db-connection) }))

(defn query-series-latest-sample-time [ series-id ]
  (if-let [ t (scalar-result
               (query/get-series-latest-sample-time
                { :series_id series-id }
                { :connection (current-db-connection) }))]
    t))

(defn get-series-latest-sample-time [ series-name ]
  (let [series-id (intern-series-name series-name)]
    (if-let [latest-t (@latest-sample-times series-id)]
      latest-t
      (let [query-latest-t (or (query-series-latest-sample-time series-id)
                               (java.util.Date. 0))]
        (swap! latest-sample-times assoc series-id query-latest-t)
        (log/debug "Latest sample times from DB" @latest-sample-times)
        query-latest-t))))

(defn check-latest-series-time [series-name t]
  (let [series-id (intern-series-name series-name)]
    (if-let [cached-latest-t (@latest-sample-times series-id)]
      (if (.after t cached-latest-t)
        (swap! latest-sample-times assoc series-id t)))))

(defn store-data-samples [ samples ]
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

(defn get-all-series []
  (query/get-all-series {} { :connection (current-db-connection) }))

(defn get-series-names []
  (map :series_name (get-all-series)))

(defn get-data-for-series-name [ series-name begin-t end-t ]
  (map #(assoc % :t (.getTime (:t %)))
       (query/get-data-for-series {:series_id (lookup-series-id series-name)
                                   :begin_t begin-t
                                   :end_t end-t}
                                  { :connection (current-db-connection) })))

;;;; Dashboards

(defn get-dashboard-definition [ name ]
  (scalar-result
   (query/get-dashboard-definition { :name name }
                                   { :connection (current-db-connection) })))

(defn store-dashboard-definition [ name definition ]
  (with-db-transaction
    (if (get-dashboard-definition name)
      (jdbc/update! (current-db-connection) :dashboard
                    {:definition definition }
                    ["name = ?" name])
      (jdbc/insert! (current-db-connection) :dashboard
                    {:name name
                     :definition definition}))))

