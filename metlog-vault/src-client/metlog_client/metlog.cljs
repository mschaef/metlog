(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! close! chan <!]]
            [metlog-client.tsplot :as tsplot]))

(defonce query-window-secs (atom (* 3600 24)))

(def dashboard-state (atom {:series []  :text ""}))

(defn <<< [f & args]
  (let [c (chan)]
    (apply f (concat args [(fn [x]
                             (if (or (nil? x)
                                     (undefined? x))
                                   (close! c)
                                   (put! c x)))]))
    c))

(defn ajax-get [ url callback ]
  (GET url {:handler callback
            :error-handler (fn [ resp ]
                             (.log js/console (str "HTTP error, url: " url " resp: " resp)))}))

(defn fetch-series-names [ cb ]
  (ajax-get "/series-names" cb))

(defn fetch-latest-series-data [ series-name cb ]
  (ajax-get (str "/latest/" series-name) cb))

(defn fetch-series-data [ series-name query-window-secs cb ]
  (watch [:fetch series-name])
  (ajax-get (str "/data/" series-name "?query-window-secs=" query-window-secs)
            (fn [ data ]
              (watch [:response series-name])
              (cb data))))

(defn tsplot-fetch-and-draw [ canvas series-name ]
  (let [ ctx (.getContext canvas "2d")]
    (go
      (tsplot/draw ctx 1024 180 [])
      (tsplot/draw ctx 1024 180 (<! (<<< fetch-series-data
                                         series-name
                                         @query-window-secs))))))

(defn series-tsplot [ series ]
  (let [ series-state (atom { :series-name (:name series)} ) ]
    (reagent/create-class
     {:display-name (str "series-tsplot-" (:name series))
      :component-did-update
      (fn [ this ]
        (watch :component-did-update)
        (tsplot-fetch-and-draw (reagent/dom-node this) (:name series)))
      
      :component-did-mount
      (fn [ this ]
        (watch :component-did-mount)
        (tsplot-fetch-and-draw (reagent/dom-node this) (:name series)))

      :reagent-render
      (fn []
        @query-window-secs
        @series-state
        [:canvas { :width 1024 :height 180}])})))

(defn series-pane [ series ]
  (watch :render-series-pane)
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name (:name series)]]
   [series-tsplot series]])

(defn series-list [ ]
  [:div
   (for [ series (:series @dashboard-state)]
     ^{ :key (:name series) } [series-pane series])])

(defn end-edit [ text state ]
  (let [ qws (js/parseInt text) ]
    (watch [:update-qws qws])
    (reset! query-window-secs qws)))

(defn input-field [ on-enter ]
  (let [ state (atom { :text "" }) ]
    (fn []
      [:input {:value (:text @state)
               :onChange #(swap! state assoc :text (.. % -target -value))
               :onKeyDown #(do
                             (watch [:on-key-down (.-key %)])
                             (when (= (.-key %) "Enter")
                                 (on-enter (:text @state))))} ])))

(defn header [ ]
  (watch :render-header)
  [:div.header
   [:span.left
    "Metlog"
    [input-field #(end-edit % dashboard-state)]]])

(defn dashboard [ ]
  (watch :render-dashboard)
  [:div
   [header]
   [:div.content
    [series-list]]])

(defn ^:export run []
  (reagent/render [dashboard]
                  (js/document.getElementById "metlog"))
  (go
    (swap! dashboard-state assoc :series
           (vec (map (fn [ series-name ] {:name series-name})
                     (<! (<<< fetch-series-names)))))))

(run)
