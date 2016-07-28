(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go go-loop ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent]
            [reagent.debug :as debug]
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

(defn time-atom [ interval-ms ]
  (let [ new-atom (reagent/atom (time/now)) ]
    (js/setInterval #(reset! new-atom (time/now)) interval-ms)
    new-atom))

(defonce current-time (time-atom 5000))

(defn range-ending-at [ end-t window-seconds ]
  (let [begin-t (time/minus end-t (time/seconds window-seconds))]
    {:msg-type :range
     :begin-t (time-coerce/to-long begin-t)
     :end-t (time-coerce/to-long end-t)}))

(defonce dashboard-state (reagent/atom {:series [] }))

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
                  :error-handler #(debug/log "HTTP error, url: " url " resp: " %)}))
  ( [ url callback ]
    (ajax-get url {} callback)))

(defn fetch-series-names [ then ]
  (ajax-get "/series-names" then))

(defn fetch-series-data [ series-name query-range then ]
  (.error js/console "Query: " series-name (pr-str query-range))
  (let [ request-t (time/now) ]
    (ajax-get (str "/data/" series-name) query-range
              #(then (merge {:series-data %
                             :series-name series-name
                             :request-t request-t
                             :response-t (time/now)}
                            query-range)))))

(defn series-tsplot-view [ series series-data series-range ]
  (let [dom-node (reagent/atom nil)]
    (reagent/create-class
     {:display-name (str "series-tsplot-view-" series)
      
      :component-did-update
      (fn [ this _ ]
        (let [[ _ _ series-data series-range ] (reagent/argv this)
              canvas (.-firstChild @dom-node)
              ctx (.getContext canvas "2d")]
          (tsplot/draw ctx (.-clientWidth canvas) (.-clientHeight canvas)
                       (:series-data series-data)
                       (:begin-t series-range)
                       (:end-t series-range))))

      :component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this)))
      
      :reagent-render
      (fn [ series series-data series-range ]
        @window-width
        [:div
         [:canvas (if-let [ node @dom-node ]
                    {:width (.-clientWidth node)
                     :height (.-clientHeight node)})]])})))

(defn range-ending-at-current-window [ end-t ]
  (range-ending-at end-t (parse-query-window @query-window)))

(defn subscribe-plot-data [ series-name series-state-atom ]
  (let [control-channel (chan)]
    (go-loop [ last-query-range nil ]
      (when-let [ query-range (<! control-channel) ]
        (when (not (= query-range last-query-range))
          (swap! series-state-atom assoc :series-data
                 (<! (<<< fetch-series-data series-name query-range))))
        (recur query-range)))
    control-channel))

(defn series-tsplot [ series-name ]
  (let [dom-node (reagent/atom nil)
        series-state (reagent/atom {})
        series-data-loop (reagent/atom nil)]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series-name)

      :component-did-mount
      (fn [ this ]
        (reset! series-data-loop (subscribe-plot-data series-name series-state))
        (put! @series-data-loop (range-ending-at-current-window @current-time)))

      :component-will-update
      (fn [ this new-argv ]
        (when @series-data-loop
          (put! @series-data-loop (range-ending-at-current-window @current-time))))
      
      :reagent-render
      (fn [ & rest ]
        [series-tsplot-view series-name (:series-data @series-state)
         (range-ending-at-current-window @current-time)])})))

(defn- remove-series [ series-name ]
  (swap! dashboard-state merge
         {:series (vec (remove #(= % series-name) (:series @dashboard-state)))}))

(defn- add-series [ new-series-name ]
  (when (not (some #(= % new-series-name) (:series @dashboard-state)))
    (swap! dashboard-state merge
           {:series (conj (:series @dashboard-state) new-series-name)})))

(defn series-pane [ series-name query-window ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series-name]

    [:span.close-button {:onClick #(remove-series series-name) }
     "close"]]
   [series-tsplot series-name]])

(defn series-list [ ]
  [:div
   (doall
    (for [ series (:series @dashboard-state) ]
      ^{ :key series } [series-pane series @query-window]))])

(defn end-edit [ text state ]
  (when (parse-query-window text)
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

(defn header [ ]
  [:div.header
   [:span#app-name "Metlog"]

   [:div#add-series.header-element
    [autocomplete/input-field {:get-completions #(:all-series @dashboard-state)
                               :placeholder "Add series..."
                               :on-enter #(do
                                            (add-series %)
                                            "")}]]

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

