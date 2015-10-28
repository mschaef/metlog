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

(defn <<< [f & args]
  (let [c (chan)]
    (apply f (concat args [(fn [x]
                             (if (or (nil? x)
                                     (undefined? x))
                                   (close! c)
                                   (put! c x)))]))
    c))

(defn ajax-get
  ([ url params callback ]
   (ajax/GET url {:handler callback
                  :params params
                  :error-handler #(.log js/console (str "HTTP error, url: " url " resp: " %))}))
  ( [ url callback ]
    (ajax-get url {} callback)))

(defn fetch-series-names [ cb ]
  (ajax-get "/series-names" cb))

(defn fetch-series-data [ series-name query-window-secs cb ]
  (ajax-get (str "/data/" series-name)
            {:query-window-secs query-window-secs}
            cb))

(defn tsplot-draw [ canvas series-data ]
  (let [ ctx (.getContext canvas "2d")]
    (tsplot/draw ctx (.-clientWidth canvas) (.-clientHeight canvas) series-data)))

(defn series-tsplot [ series qws-arg]
  (let [dom-node (reagent/atom nil)
        series-state (reagent/atom {})]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series)
      :component-did-update
      (fn [ this old-argv ]
        (tsplot-draw (.-firstChild @dom-node) (:series-data @series-state)))

      
      :component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this))
        (go
          (swap! series-state assoc :series-data (<! (<<< fetch-series-data series @query-window-secs)))))

      :reagent-render
      (fn [ ]
        @window-width
        @series-state
        [:div
         [:canvas (if-let [ node @dom-node ]
                    {:width (.-clientWidth node)
                     :height (.-clientHeight node)})]])})))

(defn series-pane [ series-name ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series-name]]
   [series-tsplot series-name @query-window-secs]])

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
               :onKeyDown #(when (= (.-key %) "Enter")
                             (on-enter (:text @state)))}])))

(defn header [ ]
  [:div.header
   [:span#app-name
    "Metlog"]
   [input-field #(end-edit % dashboard-state)]])

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
  (go
    (swap! dashboard-state assoc :series (<! (<<< fetch-series-names)))))

(run)
