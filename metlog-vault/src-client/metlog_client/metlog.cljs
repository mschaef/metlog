(ns metlog-client.metlog
  (:require [reagent.core :as reagent]
            [reagent.debug :as debug]
            [cljs-time.core :as time]
            [cljs-time.coerce :as time-coerce]
            [metlog-client.logger :as log]            
            [metlog-client.server :as server]
            [metlog-client.tsplot :as tsplot]
            [metlog-client.components :as components]
            [metlog-client.autocomplete :as autocomplete]))

(def update-interval-ms 5000)

(defn parse-query-window [ text ]
  (let [ text (.trim text) ]
    (and (> (.-length text) 0)
         (let [window-unit-char (.charAt text (- (.-length text) 1))
               window-value (js/parseInt (.substring text 0 (- (.-length text) 1)))]
           (case window-unit-char
             "S" window-value
             "m" (* 60 window-value)
             "h" (* 3600 window-value)
             "d" (* 86400 window-value)
             "w" (* 604800 window-value)
             false)))))

(defonce query-window
  (reagent/atom "1d"))

(defonce current-time
  (reagent/atom (time/now)))

(defonce dashboard-state
  (reagent/atom { :displayed-series [] }))

(defonce window-width
  (reagent/atom nil))

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
                       (:series-points series-data)
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

(defn range-ending-at [ end-t window-seconds ]
  (let [begin-t (time/minus end-t (time/seconds window-seconds))]
    {:begin-t (time-coerce/to-long begin-t)
     :end-t (time-coerce/to-long end-t)}))

(defn series-tsplot [ series-name plot-end-time query-window ]
  (let [series-state (reagent/atom {})]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series-name)

      :component-did-mount
      (fn [ this ]
        (let [loop-control (server/subscribe-plot-data series-name #(swap! series-state assoc :series-data %))]
          (swap! series-state assoc :data-loop-control loop-control)
          (server/update-plot-data-range loop-control (range-ending-at plot-end-time query-window))))
      
      :component-will-unmount
      (fn [ this ]
        (server/unsubscribe-plot-data (:data-loop-control @series-state)))

      :component-will-update
      (fn [ this [ _ _ plot-end-time query-window ]]
        (server/update-plot-data-range (:data-loop-control @series-state) (range-ending-at plot-end-time query-window)))
      
      :reagent-render
      (fn [ series-name plot-end-time query-window ]
        [series-tsplot-view series-name (:series-data @series-state) (range-ending-at plot-end-time query-window)])})))

(defn- set-displayed-series! [ series-names ]
  (swap! dashboard-state merge {:displayed-series series-names})
  (server/set-dashboard-series series-names))

(defn- remove-series [ series-name ]
  (set-displayed-series! (vec (remove #(= % series-name) (:displayed-series @dashboard-state)))))

(defn- add-series [ new-series-name ]
  (when (not (some #(= % new-series-name) (:displayed-series @dashboard-state)))
    (set-displayed-series! (conj (:displayed-series @dashboard-state) new-series-name))))

(defn series-pane [ series-name current-time query-window ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series-name]

    [:span.close-button {:onClick #(remove-series series-name) }
     "close"]]
   [series-tsplot series-name current-time query-window]])

(defn series-list [ ]
  [:div
   (doall
    (for [ series (:displayed-series @dashboard-state) ]
      ^{ :key series } [series-pane series @current-time (parse-query-window @query-window)]))])

(defn header [ ]
  [:div.header
   [:span#app-name "Metlog"]

   [:div#add-series.header-element
    [autocomplete/input-field {:get-completions #(:all-series @dashboard-state)
                               :placeholder "Add series..."
                               :on-enter #(do (add-series %) "")}]]

   [:div#query-window.header-element
    [components/input-field {:text @query-window
                             :text-valid? parse-query-window
                             :on-enter #(reset! query-window %)}]]])

(defn dashboard [ ]
  [:div
   [header]
   [:div.content
    [series-list]]])

(defn on-window-resize [ evt ]
  (reset! window-width (.-innerWidth js/window)))

(log/set-configuration! {"" :debug })

(defn update-current-time []
  (log/debug "update-current-time")
  (reset! current-time (time/now)))

(defn ^:export run []
  (reagent/render [dashboard]
                  (js/document.getElementById "metlog"))
  (.addEventListener js/window "resize" on-window-resize)
  (let [update-interval-id
        (or (:update-interval-id @dashboard-state)
            (js/setInterval update-current-time update-interval-ms))]
    (swap! dashboard-state merge {:update-interval-id update-interval-id})

    (server/fetch-series-names #(swap! dashboard-state merge {:all-series %}))
    (server/fetch-dashboard-series #(swap! dashboard-state merge {:displayed-series %}))
    (log/debug "init complete.")))

(run)

