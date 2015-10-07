(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! close! chan <!]]
            [metlog-client.tsplot :as tsplot]))

(defonce query-window-secs (atom (* 3600 24)))

(def dashboard-state (atom {:series [] :text ""}))

(def window-width (atom 1024))

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
  (ajax-get (str "/data/" series-name "?query-window-secs=" query-window-secs)
            (fn [ data ]
              (cb data))))

(defn tsplot-fetch-and-draw [ canvas series-name width ]
  (let [ ctx (.getContext canvas "2d")]
    (go
      (tsplot/draw ctx width 180  (<! (<<< fetch-series-data
                                           series-name
                                           @query-window-secs))))))

(defn dom-width [ node ]
  (if node
    (.-clientWidth node)
    1024))

(defn series-tsplot [ series initial-width ]
  (let [ series-state (atom { :series-name (:name series)} ) ]
    (reagent/create-class
     {:display-name (str "series-tsplot-" (:name series))
      :component-did-update
      (fn [ this ]
        (tsplot-fetch-and-draw (reagent/dom-node this) (:name series) (dom-width (:dom-node @series-state))))
      
      :component-did-mount
      (fn [ this ]
        (swap! series-state assoc :dom-node (.-parentNode (reagent/dom-node this)))        
        (tsplot-fetch-and-draw (reagent/dom-node this) (:name series) (dom-width (.-parentNode (reagent/dom-node this)))))

      :reagent-render
      (fn [ width ]
        @window-width
        [:canvas { :width (dom-width (:dom-node @series-state)) :height 180}])})))

(defn series-pane [ series ]
  (let [ pane-state (atom {})]
    (reagent/create-class

     {:component-did-mount
      (fn [ this ]
        (swap! pane-state assoc :dom-node (reagent/dom-node this)))

      :reagent-render
      (fn []
        @window-width
        [:div.series-pane
         [:div.series-pane-header
          [:span.series-name (:name series)]]
         [series-tsplot series (dom-width (:dom-node @pane-state))]])})))

(defn series-list [ ]
  [:div
   (for [ series (:series @dashboard-state)]
     ^{ :key (:name series) } [series-pane series])])

(defn end-edit [ text state ]
  (let [ qws (js/parseInt text) ]
    (reset! query-window-secs qws)))

(defn input-field [ on-enter ]
  (let [ state (atom { :text "" }) ]
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
    (swap! dashboard-state assoc :series
           (vec (map (fn [ series-name ] {:name series-name})
                     (<! (<<< fetch-series-names)))))))

(run)
