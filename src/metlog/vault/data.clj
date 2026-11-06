(ns metlog.vault.data
  (:use playbook.core
        sql-file.middleware)
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [playbook.config :as config]
            [metlog.vault.queries :as query]))

(defn get-series-id [series-name]
  (query/get-series-id {:series_name series-name}))

(defn add-series-name [series-name]
  (:series_id (query/add-series<! {:series_name series-name})))

(def intern-series-name
  (memoize
   (fn [series-name]
     (let [series-name (.trim (name (or series-name "")))]
       (if (= 0 (.length series-name))
         nil
         (with-db-transaction
           (or (get-series-id series-name)
               (add-series-name series-name))))))))

(defn lookup-series-id [series-name]
  (let [series-name (.trim (name (or series-name "")))]
    (if (= 0 (.length series-name))
      nil
      (get-series-id series-name))))

(def latest-sample-times (atom {}))

(defn delete-old-samples [series-id archive-time]
  (query/delete-old-samples! {:series_id series-id
                              :archive_time archive-time}))

(defn archive-old-samples [series-id archive-time]
  (query/archive-old-samples! {:series_id series-id
                               :archive_time archive-time}))

(defn query-series-latest-sample-time [series-id]
  (query/get-series-latest-sample-time {:series_id series-id}))

(defn query-series-earliest-sample-before [series-id archive-time]
  (query/get-series-earliest-sample-before {:series_id series-id
                                            :archive_time archive-time}))

(defn get-series-latest-sample-time [series-name]
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

(defn- insert-sample-batch [sample-batch]
  (log/info "Storing batch of" (count sample-batch) "samples.")
  ;; Stays on jdbc rather than yesql: insert-multi! sends the whole
  ;; batch as one batched prepared statement, in a single transaction,
  ;; so a batch is stored completely or not at all.
  (jdbc/insert-multi! (current-db-connection) :sample
                      [:series_id :t :val]
                      (map (fn [sample]
                             [(intern-series-name (:series_name sample))
                              (.toInstant (:t sample))
                              (:val sample)])
                           sample-batch)))

(defn store-data-samples [samples]
  (doseq [sample-batch (partition-all (config/cval :vault :store-sample-batch-size) samples)]
    (insert-sample-batch sample-batch)))

;;; Monotonic store ensures that only new samples are stored. Existing
;;; samples, those older than the latest currently known in the
;;; database, are ignored. Useful for the USGS data, which is queried
;;; in 24 hours blocks, rather than streams of the latest samples, and
;;; for agent batches that are resent after a lost response.
;;;
;;; Each sample is checked against the latest time *including* the
;;; samples accepted earlier in the same call, so the filter is an
;;; eager, strictly sequential reduce over a local map. (A lazy filter
;;; evaluates its predicate a 32-element chunk at a time, ahead of the
;;; latest-time updates, which let duplicates within a batch through.)
;;;
;;; The shared latest-sample-times cache only advances after each
;;; batch has been committed. If an insert fails, the cache still
;;; matches the database, so a retry of the same samples stores
;;; whatever wasn't committed and skips whatever was.
(defn store-data-samples-monotonic [samples]
  (let [[new-samples _]
        (reduce (fn [[new-samples latest-t] sample]
                  (let [series-name (:series_name sample)
                        t (:t sample)
                        prev-t (or (latest-t series-name)
                                   (get-series-latest-sample-time series-name))]
                    (if (.after t prev-t)
                      [(conj new-samples sample) (assoc latest-t series-name t)]
                      [new-samples latest-t])))
                [[] {}]
                samples)]
    (doseq [sample-batch (partition-all (config/cval :vault :store-sample-batch-size) new-samples)]
      (insert-sample-batch sample-batch)
      (doseq [sample sample-batch]
        (check-latest-series-time (:series_name sample) (:t sample))))))

(defn get-all-series []
  (query/get-all-series))

(defn get-series-names []
  (map :series_name (get-all-series)))

(defn get-data-for-series-name [series-name begin-t end-t]
  (map #(assoc % :t (.getTime (:t %)))
       (query/get-data-for-series {:series_id (lookup-series-id series-name)
                                   :begin_t (java.sql.Timestamp. begin-t)
                                   :end_t (java.sql.Timestamp. end-t)})))

;;;; Dashboards

(defn get-dashboard-names []
  (query/get-dashboard-names))

(defn get-dashboard-by-name [dashboard-name]
  (query/get-dashboard-by-name {:name dashboard-name}))

(defn get-dashboard-by-id [dashboard-id]
  (query/get-dashboard-by-id {:id dashboard-id}))

(defn insert-dashboard-definition [name definition]
  (:dashboard_id (query/add-dashboard<! {:name name
                                         :definition (json/write-str definition)})))

(defn update-dashboard-definition [id definition]
  (query/update-dashboard-definition! {:id id
                                       :definition (json/write-str definition)}))

(defn delete-dashboard-by-id [id]
  (query/delete-dashboard! {:id id}))

