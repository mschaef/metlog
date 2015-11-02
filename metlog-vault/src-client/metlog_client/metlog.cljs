(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go go-loop ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! close! chan <! dropping-buffer alts! pipeline]]
            [cljs-time.core :as time]
            [cljs-time.coerce :as time-coerce]
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
  (let [end-t (time/time-now)
        begin-t (time/minus end-t (time/seconds query-window-secs))]
    (ajax-get (str "/data/" series-name)
              {:begin-t (time-coerce/to-long begin-t)
               :end-t (time-coerce/to-long end-t)}
              cb)))

(defn periodic-event-channel [ interval-ms ]
  (let [ channel (chan (dropping-buffer 1)) ]
    (js/setInterval #(put! channel (time/now))
                    interval-ms)
    channel))

(defn range-ending-at [ end-t ]
  (let [begin-t (time/minus end-t (time/seconds @query-window-secs))]
    {:begin-t begin-t :end-t end-t}))

(defn query-range-channel [ periodic-event-channel ]
  (let [ channel (chan) ]
    (pipeline 1 channel (map range-ending-at) periodic-event-channel)
    channel))

#_(let [ channel (query-range-channel (periodic-event-channel 5000))]
    (go-loop []
      (let [range (<! channel)]
        (when range
          (watch range)
          (recur)))))

(defn series-tsplot [ series qws-arg ]
  (let [dom-node (reagent/atom nil)
        series-state (reagent/atom {})]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series)
      :component-did-update
      (fn [ this old-argv ]
        (let [canvas (.-firstChild @dom-node)
              ctx (.getContext canvas "2d")]
          (tsplot/draw ctx (.-clientWidth canvas) (.-clientHeight canvas)
                       (:series-data @series-state)
                       (time-coerce/to-long (time/minus (time/time-now) (time/seconds @query-window-secs))) 
                       (time-coerce/to-long (time/time-now)))))

      :component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this))
        (go
          (swap! series-state assoc :series-data
                 (<! (<<< fetch-series-data series @query-window-secs)))))

      :reagent-render
      (fn [ & rest ]
        @window-width
        @series-state
        [:div
         [:canvas (if-let [ node @dom-node ]
                    {:width (.-clientWidth node)
                     :height (.-clientHeight node)})]])})))

(defn series-pane [ series-name query-window-secs ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series-name]]
   [series-tsplot series-name query-window-secs]])

(defn series-list [ ]
  (let [ query-window-secs @query-window-secs] 
    [:div
     (for [ series (:series @dashboard-state) ]
       ^{ :key series } [series-pane series query-window-secs])]))

(defn end-edit [ text state ]
  (let [ qws (js/parseInt text) ]
    (reset! query-window-secs qws)))

(defn input-field [ initial-text on-enter ]
  (let [ state (reagent/atom { :text initial-text }) ]
    (fn []
      [:input {:value (:text @state)
               :onChange #(swap! state assoc :text (.. % -target -value))
               :onKeyDown #(when (= (.-key %) "Enter")
                             (on-enter (:text @state)))}])))

(defn header [ ]
  [:div.header
   [:span#app-name
    "Metlog"]
   [input-field @query-window-secs #(end-edit % dashboard-state)]])

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
