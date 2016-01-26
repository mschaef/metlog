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

(defn parse-query-window [ text ]
  (let [ text (.trim text) ]
    (and (> (.-length text) 0)
         (let [window-unit-char (.charAt text (- (.-length text) 1))
               window-value (.substring text 0 (- (.-length text) 1))]
           (case window-unit-char
             "S" (js/parseInt window-value)
             "m" (* 60 (js/parseInt window-value))
             "h" (* 3600 (js/parseInt window-value))
             "d" (* 86400 (js/parseInt window-value))
             "w" (* 604800 (js/parseInt window-value))
             false)))))


(defonce query-window (reagent/atom "1d"))

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
  (let [end-t (time/now)
        begin-t (time/minus end-t (time/seconds query-window-secs))]
    (ajax-get (str "/data/" series-name)
              {:begin-t (time-coerce/to-long begin-t)
               :end-t (time-coerce/to-long end-t)}
              cb)))

(defn periodic-event-channel [ interval-ms ]
  (let [channel (chan (dropping-buffer 1))
        put-now #(put! channel (time/now))]
    (put-now)
    (js/setInterval put-now interval-ms)
    channel))

(defn range-ending-at [ end-t ]
  (let [begin-t (time/minus end-t (time/seconds (parse-query-window @query-window)))]
    {:begin-t (time-coerce/to-long begin-t)
     :end-t (time-coerce/to-long end-t)}))

(defn query-range-channel [ periodic-event-channel ]
  (let [ channel (chan (dropping-buffer 1)) ]
    (pipeline 1 channel (map range-ending-at) periodic-event-channel)
    channel))

(defn series-data-channel [ series-name query-range-channel ]
  (let [channel (chan)]
    (go-loop []
      (ajax-get (str "/data/" series-name) (<! query-range-channel) #(put! channel %))
      (recur))
    channel))

(defn series-tsplot [ series qws-arg ]
  (let [dom-node (reagent/atom nil)
        series-state (reagent/atom {})]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series)
      :component-did-update
      (fn [ this old-argv ]
        (let [canvas (.-firstChild @dom-node)
              ctx (.getContext canvas "2d")
              range (range-ending-at (time/now))]
          (tsplot/draw ctx (.-clientWidth canvas) (.-clientHeight canvas)
                       (:series-data @series-state)
                       (:begin-t range)
                       (:end-t range))))

      :component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this))
        (let [ channel (series-data-channel series (query-range-channel (periodic-event-channel 15000))) ]
          (go-loop []
            (let [ data (<! channel) ]
              (when data
                (swap! series-state assoc :series-data data)
                (recur))))))
      
      :reagent-render
      (fn [ & rest ]
        @window-width
        @series-state
        [:div
         [:canvas (if-let [ node @dom-node ]
                    {:width (.-clientWidth node)
                     :height (.-clientHeight node)})]])})))

(defn series-pane [ series-name query-window ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series-name]]
   [series-tsplot series-name query-window]])

(defn series-list [ ]
  [:div
   (for [ series (:series @dashboard-state) ]
     ^{ :key series } [series-pane series @query-window])])

(defn end-edit [ text state ]
  (when (parse-query-window text)
    (reset! query-window text)))

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
   [input-field @query-window #(end-edit % dashboard-state)]])

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

