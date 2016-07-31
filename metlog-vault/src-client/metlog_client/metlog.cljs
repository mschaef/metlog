(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go go-loop ]]
                   [metlog-client.macros :refer [ watch ]])
  (:require [reagent.core :as reagent]
            [reagent.debug :as debug]
            [cljs.core.async :refer [put! close! chan <!]]
            [cljs-time.core :as time]
            [cljs-time.coerce :as time-coerce]
            [metlog-client.server :as server]
            [metlog-client.tsplot :as tsplot]
            [metlog-client.components :as components]
            [metlog-client.autocomplete :as autocomplete]))

(def update-interval-ms 5000)

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

(defonce current-time (reagent/atom (time/now)))

(defn range-ending-at [ end-t window-seconds ]
  (let [begin-t (time/minus end-t (time/seconds window-seconds))]
    {:msg-type :range
     :begin-t (time-coerce/to-long begin-t)
     :end-t (time-coerce/to-long end-t)}))

(defonce dashboard-state
  (reagent/atom { :displayed-series [] }))

(defonce window-width (reagent/atom nil))

(defn <<< [f & args]
  (let [c (chan)]
    (apply f (concat args [(fn [x]
                             (if (or (nil? x)
                                     (undefined? x))
                                   (close! c)
                                   (put! c x)))]))
    c))

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
                 (<! (<<< server/fetch-series-data series-name query-range))))
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
         {:displayed-series (vec (remove #(= % series-name) (:displayed-series @dashboard-state)))}))

(defn- add-series [ new-series-name ]
  (when (not (some #(= % new-series-name) (:displayed-series @dashboard-state)))
    (swap! dashboard-state merge
           {:displayed-series (conj (:displayed-series @dashboard-state) new-series-name)})))

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
    (for [ series (:displayed-series @dashboard-state) ]
      ^{ :key series } [series-pane series @query-window]))])

(defn end-edit [ text state ]
  (when (parse-query-window text)
    (reset! query-window text)))


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
    [components/input-field {:initial-text @query-window
                             :text-valid? parse-query-window
                             :on-enter #(end-edit % dashboard-state)}]]])

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
  (let [update-interval-id
        (or (:update-interval-id @dashboard-state)
            (js/setInterval #(reset! current-time (time/now)) update-interval-ms))]
    (go
      (let [ all-series (<! (<<< server/fetch-series-names))]
        (swap! dashboard-state merge {:all-series all-series
                                      :update-interval-id update-interval-id})))))

(run)

