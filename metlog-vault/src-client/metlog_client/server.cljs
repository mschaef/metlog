(ns metlog-client.server
  (:require [ajax.core :as ajax]
            [cljs-time.core :as time]
            [metlog-client.logger :as log]))

(defn ajax-get
  ([ url params callback ]
   (ajax/GET url {:handler callback
                  :params params
                  :error-handler #(log/error "HTTP error, url: " url " resp: " %)}))
  ( [ url callback ]
    (ajax-get url {} callback)))

(defn fetch-series-names [ then ]
  (ajax-get "/series-names" then))

(defn fetch-series-data [ series-name query-range then ]
  (let [ request-t (time/now) ]
    (log/debug :fetch-series-data series-name (:begin-t query-range)
               (- (:end-t query-range) (:begin-t query-range)))
    (ajax-get (str "/data/" series-name) query-range
              #(then (merge {:series-points %
                             :series-name series-name
                             :request-t request-t
                             :response-t (time/now)}
                            query-range)))))


