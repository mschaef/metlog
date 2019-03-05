(ns metlog-client.server
  (:require-macros [cljs.core.async.macros :refer [ go go-loop ]])
  (:require [ajax.core :as ajax]
            [cljs-time.core :as time]
            [cljs.core.async :refer [put! close! chan <!]]
            [metlog-client.util :refer [ <<< ]]            
            [metlog-client.logger :as log]))

(defn ajax-get
  ([ url params callback ]
   (ajax/GET url {:handler callback
                  :params params
                  :error-handler #(log/error "HTTP error, url: " url " resp: " %)}))
  ( [ url callback ]
    (ajax-get url {} callback)))

(defn ajax-post [ url params ]
  (ajax/POST url {:params params}))

(defn set-dashboard-series [ series-names ]
  (ajax-post "/dashboard-defn/default" series-names)) 

(defn fetch-dashboard-series [ callback ]
  (ajax-get "/dashboard-defn/default" callback))

(defn fetch-series-names [ then ]
  (ajax-get "/series-names" then))

(defn fetch-series-data [ series-name query-range then ]
  (let [ request-t (time/now) ]
    (log/debug :fetch-series-data series-name request-t (:end-t query-range) (- (:end-t query-range) (:begin-t query-range)))
    (ajax-get (str "/data/" series-name) query-range
              #(then (merge {:series-points %
                             :series-name series-name
                             :request-t request-t
                             :response-t (time/now)}
                            query-range)))))

(defn- normalize-points [ points ]
  (vec (distinct (sort-by :t points))))

(defn- points-range [ data ]
  (and data
       (let [ series-points (:series-points data) ]
         {:begin-t (:t (get series-points 0))
          :end-t (:t (get series-points (- (count series-points) 1)))})))

(defn- find-historical-query-range [ current-data desired-data-range ]
  (and (< (:begin-t desired-data-range)
          (:begin-t current-data))
       {:begin-t (:begin-t desired-data-range)
        :end-t (:begin-t current-data)}))

(defn- find-update-query-range [ current-data desired-data-range ]
  (if-let [ current-data-range (points-range current-data)]
    (and (> (:end-t desired-data-range)
            (:end-t current-data-range))
         {:begin-t (:end-t current-data-range)
          :end-t (:end-t desired-data-range)})
    desired-data-range))

(defn merge-series-data [ current-data new-data ]
  {:series-points (normalize-points (concat (or (:series-points current-data) [])
                                            (:series-points new-data)))
   :begin-t (min (or (:begin-t current-data)
                     (.-MAX_VALUE js/Number))
                 (:begin-t new-data))
   :end-t (max (or (:end-t current-data)
                   (.-MIN_VALUE js/Number))
               (:end-t new-data))})

(defn subscribe-plot-data [ series-name update-fn ]
  (let [control-channel (chan 1 (dedupe))]
    (go-loop [ current-data nil ]
      (when-let [ desired-data-range (<! control-channel) ]
        (let [ updated-data
              (let [ data (if-let [ historical-query-range (find-historical-query-range current-data desired-data-range) ]
                            (merge-series-data current-data (<! (<<< fetch-series-data series-name historical-query-range)))
                            current-data)]
                (if-let [ update-query-range (find-update-query-range data desired-data-range )]
                  (merge-series-data data (<! (<<< fetch-series-data series-name update-query-range)))
                  data))]
          (when updated-data
            (update-fn updated-data))
          (recur updated-data))))
    control-channel))

(defn unsubscribe-plot-data [ control-channel ]
  (close! control-channel))

(defn update-plot-data-range [ control-channel range ]
  (put! control-channel range))

