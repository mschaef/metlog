(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! close! chan <!]]
            [metlog-client.tsplot :as tsplot]))

(defonce query-window-secs (reagent/atom (* 3600 24)))

(def dashboard-state (reagent/atom {:series [] :text ""}))

(def window-width (reagent/atom nil))

(def default-tsplot-width 1024)
(def default-tsplot-height 180)

(defn <<< [f & args]
  (let [c (chan)]
    (apply f (concat args [(fn [x]
                             (if (or (nil? x)
                                     (undefined? x))
                                   (close! c)
                                   (put! c x)))]))
    c))

(defn ajax-get [ url callback ]
  (ajax/GET url {:handler callback
                 :error-handler (fn [ resp ]
                                  (.log js/console (str "HTTP error, url: " url " resp: " resp)))}))

(defn fetch-series-names [ cb ]
  (ajax-get "/series-names" cb))

(defn fetch-series-data [ series-name query-window-secs cb ]
  (ajax-get (str "/data/" series-name "?query-window-secs=" query-window-secs) cb))

(defn dom-node-width
  ([ node default-width ]
   (if node
     (.-clientWidth node)
     default-width))
  ([ node ]
   (dom-node-width node nil)))

(defn dom-node-height
  ([ node default-height ]
   (if node
     (.-clientHeight node)
     default-height))
  ([ node ]
   (dom-node-height node nil)))

(defn tsplot-draw [ canvas series-data ]
  (let [ ctx (.getContext canvas "2d")]
    (tsplot/draw ctx (dom-node-width canvas) (dom-node-height canvas) series-data)))

(defn series-tsplot-plot-canvas [ series ]
  (let [ series-state (reagent/atom { }) ]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series)
      :component-did-update
      (fn [ this ]
        (tsplot-draw (reagent/dom-node this) (:series-data @series-state)))
      
      :component-did-mount
      (fn [ this ]
        (go
          (swap! series-state assoc :series-data (<! (<<< fetch-series-data series @query-window-secs)))))

      :reagent-render
      (fn [ series width height ]
        @series-state
        [:canvas {:width width :height height}])})))

(defn series-tsplot [ series ]
  (let [ dom-node (reagent/atom nil)]
    (reagent/create-class
     {:component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this)))

      :reagent-render
      (fn [ ]
        @window-width
        [:div
         [series-tsplot-plot-canvas series
          (dom-node-width @dom-node default-tsplot-width)
          (dom-node-height @dom-node default-tsplot-height)]])})))

(defn series-pane [ series ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series]]
   [series-tsplot series]])

(defn series-list [ ]
  [:div
   (for [ series (:series @dashboard-state) ]
     ^{ :key series } [series-pane series])])

(defn end-edit [ text state ]
  (let [ qws (js/parseInt text) ]
    (reset! query-window-secs qws)))

(defn input-field [ on-enter ]
  (let [ state (reagent/atom { :text "" }) ]
    (fn []
      [:input {:value (:text @state)
               :onChange #(swap! state assoc :text (.. % -target -value))
               :onKeyDown #(do
                             (when (= (.-key %) "Enter")
                                 (on-enter (:text @state))))} ])))

(defn header [ ]
  [:div.header
   [:span.left
    "Metlog"
    [input-field #(end-edit % dashboard-state)]]])

(defn dashboard [ ]
  [:div
   [header]
   [:div.content
    [series-list]]])

(defn on-window-resize [ evt ]
  (reset! window-width (.-innerWidth js/window)))

(defn ^:export run []
  (reagent/render [dashboard]
                  (js/document.getElementById "metlog"))
  (.addEventListener js/window "resize" on-window-resize)
  (on-window-resize nil)
  (go
    (swap! dashboard-state assoc :series (<! (<<< fetch-series-names)))))

(run)
