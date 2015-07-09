(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! close! chan <!]]
            [metlog-client.tsplot :as tsplot]))

(def dashboard-state (atom {:series [] :query-window-secs (* 3600 24) :text ""}))

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

(defn schedule-data-update [ series-name query-window-secs series-state ]
  (go
    (swap! series-state assoc :data (<! (<<< fetch-series-data
                                             series-name
                                             query-window-secs)))))

(defn series-tsplot [ series ]
  (let [ series-state (atom { :series-name (:name series)} ) ]
    (reagent/create-class
     {:display-name (str "series-tsplot-" (:name series))
      :component-did-mount
      (fn [this]
        (let [canvas (reagent/dom-node this)]
          (swap! series-state assoc :canvas canvas)
          (schedule-data-update (:series-name @series-state) (:query-window-secs @dashboard-state) series-state)))
      
      :component-did-update
      (fn [this]
        (tsplot/draw (.getContext (:canvas @series-state) "2d")
                     1024
                     180
                     (:data @series-state)))


      :reagent-render
      (fn []
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
    (swap! state assoc :query-window-secs qws)))

(defn input-field [ on-enter ]
  (let [ state (atom { :text "" }) ]
    (fn []
      [:input {:value (:text @state)
               :onChange #(swap! state assoc :text (.. % -target -value))
               :onKeyDown #(when (= (.-key %) "Enter")
                             (on-enter (:text @state)))} ])))

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
