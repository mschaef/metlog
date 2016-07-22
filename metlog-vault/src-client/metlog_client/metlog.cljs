(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go go-loop ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent]
            [ajax.core :as ajax]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! close! chan <! dropping-buffer alts! pipeline pub sub]]
            [cljs-time.core :as time]
            [cljs-time.coerce :as time-coerce]
            [metlog-client.tsplot :as tsplot]
            [metlog-client.autocomplete :as autocomplete]))

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

(defn periodic-event-channel [ interval-ms ]
  (let [channel (chan)
        put-now #(put! channel {:msg-type :timestamp :t (time/now)})]
    (put-now)
    (js/setInterval put-now interval-ms)
    channel))

(defonce time-event-channel (periodic-event-channel 15000))

(defn range-ending-at [ end-t window-seconds ]
  (let [begin-t (time/minus end-t (time/seconds window-seconds))]
    {:msg-type :range
     :begin-t (time-coerce/to-long begin-t)
     :end-t (time-coerce/to-long end-t)}))

(defn query-range-channel-2 [ time-event-channel ]
  (let [ output (chan)
        output-pub (pub output :msg-type)]
    (go-loop [ time (time/now) qws @query-window ]
      (>! output (range-ending-at time (parse-query-window qws)))
      (when-let [ msg (<! time-event-channel)]
        (case (:msg-type msg)
          :timestamp (recur (:t msg) qws)
          :query-window (recur time (:window-spec msg)))))
    output-pub))

(defonce update-pub (query-range-channel-2 time-event-channel))

(defonce dashboard-state (reagent/atom {:series [] :text ""}))

(defonce window-width (reagent/atom nil))

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


(defn range-ending-at-current-window [ end-t ]
  (range-ending-at end-t (parse-query-window @query-window)))

(defn series-data-channel [ series-name query-range-channel ]
  (let [channel (chan)]
    (go-loop []
      (when-let [ query-range  (<! query-range-channel)]
        (ajax-get (str "/data/" series-name) query-range #(put! channel %))
        (recur)))
    channel))

(defn subscribe-plot-data [ series series-state ]
  (let [update-out-chan (chan)
        channel (series-data-channel series update-out-chan) ]
    (sub update-pub :range update-out-chan)    
    (go-loop []
      (when-let [ data (<! channel) ]
        (swap! series-state assoc :series-data data)
        (recur)))))

(defn series-tsplot [ series qws-arg ]
  (let [dom-node (reagent/atom nil)
        series-state (reagent/atom {})]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series)
      :component-did-update
      (fn [ this old-argv ]
        (let [canvas (.-firstChild @dom-node)
              ctx (.getContext canvas "2d")
              range (range-ending-at-current-window (time/now))]
          (tsplot/draw ctx (.-clientWidth canvas) (.-clientHeight canvas)
                       (:series-data @series-state)
                       (:begin-t range)
                       (:end-t range))))

      :component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this))
        (subscribe-plot-data series series-state))
      
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
   (doall
    (for [ series (:series @dashboard-state) ]
      ^{ :key series } [series-pane series @query-window]))])

(defn end-edit [ text state ]
  (when (parse-query-window text)
    (put! time-event-channel {:msg-type :query-window :window-spec text})
    (reset! query-window text)))

(defn input-field [ initial-text text-valid? on-enter ]
  (let [ state (reagent/atom { :text initial-text }) ]
    (fn []
      (let [valid? (text-valid? (:text @state))]
        [:input {:value (:text @state)
                 :class (if (not valid?) "invalid")
                 :onChange #(swap! state assoc :text (.. % -target -value))
                 :onKeyDown #(when (and valid?
                                        (= (.-key %) "Enter"))
                               (on-enter (:text @state)))}]))))

(defn- add-series [ new-series-name ]
  (swap! dashboard-state merge {:series (conj (:series @dashboard-state) new-series-name)}))

(defn header [ ]
  [:div.header
   [:span#app-name "Metlog"]

   [:div#add-series.header-element
    [autocomplete/input-field {:get-completions #(:all-series @dashboard-state)
                               :placeholder "Add series..."
                               :on-enter #(add-series %)}]]

   [:div#query-window.header-element
    [input-field @query-window parse-query-window #(end-edit % dashboard-state)]]])

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
    (let [ all-series (<! (<<< fetch-series-names))]
      (swap! dashboard-state merge {:all-series all-series
                                    :series [ ]}))))

(run)

