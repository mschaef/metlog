(ns metlog-client.server
  (:require-macros [cljs.core.async.macros :refer [ go go-loop ]])
  (:require [ajax.core :as ajax]
            [cljs-time.core :as time]
            [cljs.core.async :refer [put! close! chan <! >! sliding-buffer pub sub unsub]]
            [cljs-time.coerce :as time-coerce]            
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
  (if query-range
    (let [ request-t (time/now) ]
      (log/debug :fetch-series-data series-name request-t (:end-t query-range) (- (:end-t query-range) (:begin-t query-range)))
      (ajax-get (str "/data/" series-name) query-range
                #(then (merge {:series-points %
                               :series-name series-name
                               :request-t request-t
                               :response-t (time/now)}
                              query-range))))
    (then nil)))

(defn- normalize-points [ points ]
  (vec (distinct (sort-by :t points))))

(defn- points-range [ data ]
  (and data
       (let [ series-points (:series-points data) ]
         {:begin-t (:t (get series-points 0))
          :end-t (:t (get series-points (- (count series-points) 1)))})))

(defn- historical-query [ current-data desired-data-range ]
  (and (< (:begin-t desired-data-range)
          (:begin-t current-data))
       {:begin-t (:begin-t desired-data-range)
        :end-t (:begin-t current-data)}))

(defn- update-query [ current-data desired-data-range ]
  (if-let [ current-data-range (points-range current-data)]
    (and (> (:end-t desired-data-range)
            (:end-t current-data-range))
         {:begin-t (:end-t current-data-range)
          :end-t (:end-t desired-data-range)})
    desired-data-range))

(defn merge-series-data [ current-data new-data ]
  (if new-data
    {:series-points (normalize-points (concat (or (:series-points current-data) [])
                                              (:series-points new-data)))
     :begin-t (min (or (:begin-t current-data) (.-MAX_VALUE js/Number))
                   (:begin-t new-data))
     :end-t (max (or (:end-t current-data) (.-MIN_VALUE js/Number))
                 (:end-t new-data))}
    current-data))

;;;; Subscription

(defonce current-time-chan (chan))

(defonce current-time-pub (pub current-time-chan :event))

(defn update-subscriptions []
  (log/debug :update-subscriptions)
  (go (>! current-time-chan { :event :current-time :t (time/now)})))

(defonce subscription-update-interval (js/setInterval #(update-subscriptions) 5000))

(defn update-plot-query-window [ control-channel query-window ]
  (put! control-channel {:event :query-window :query-window query-window}))

(defn range-ending-at [ end-t window-seconds ]
  (let [begin-t (time/minus end-t (time/seconds window-seconds))]
    {:begin-t (time-coerce/to-long begin-t)
     :end-t (time-coerce/to-long end-t)}))

(defn start-subscription-loop [ control-channel series-name update-fn ]
  (go-loop [current-time (time/now)
            query-window nil
            current-data nil ]
    (when-let [ message (<! control-channel) ]
      (case (:event message)
        :query-window
        (let [ range (range-ending-at current-time (:query-window message)) ]
          (let [ updated-data
                (-> current-data
                    (merge-series-data (<! (<<< fetch-series-data series-name (historical-query current-data range))))
                    (merge-series-data (<! (<<< fetch-series-data series-name (update-query current-data range)))))]
            (when updated-data
              (update-fn updated-data))
            (recur current-time (:query-window message) updated-data )))

        :current-time
        (do
          (update-plot-query-window control-channel query-window)
          (recur (:t message) query-window current-data))

        (recur current-time query-window current-data)))))

(defn snap-and-subscribe-plot-data [ series-name query-window update-fn ]
  (let [control-channel (chan (sliding-buffer 1))]
    (update-plot-query-window control-channel query-window)
    (start-subscription-loop control-channel series-name update-fn)
    (sub current-time-pub :current-time control-channel)
    control-channel))

(defn unsubscribe-plot-data [ control-channel ]
  (unsub current-time-pub :current-time control-channel)
  (close! control-channel))


