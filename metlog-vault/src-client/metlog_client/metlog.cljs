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

(defn- setup-canvas [ canvas width height ]
  (let [dpr (or  (.-devicePixelRatio js/window) 1)
        ctx (.getContext canvas "2d")]

    (aset canvas "width" (* dpr width))
    (aset canvas "height" (* dpr height))
    (aset (.-style canvas) "width" (str width "px"))
    (aset (.-style canvas) "height" (str height "px"))
    (.scale ctx dpr dpr)))

(defn series-tsplot-view [ series series-data series-range ]
  (let [ dom-node (reagent/atom nil)]
    (reagent/create-class
     {:display-name (str "series-tsplot-view-" series)

      :component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this)))

      :component-did-update
      (fn [ this _ ]
        (let [[ _ _ series-data series-range ] (reagent/argv this)
              canvas (.-firstChild @dom-node)
              ctx (.getContext canvas "2d")]
          (setup-canvas canvas (.-clientWidth @dom-node) (.-clientHeight @dom-node))
          (when series-data
            (try
              (tsplot/draw ctx (.-clientWidth canvas) (.-clientHeight canvas)
                           (:series-points series-data)
                           (:begin-t series-range)
                           (:end-t series-range))
              (catch :default e
                (log/error "Uncaught exception rendering plot for " series " (" e ")"))))))

      :render
      (fn [ this]
        (let  [[ series series-data series-range ] (reagent/argv this)]
          @window-width
          [:div
           [:canvas (if-let [ node @dom-node]
                      {:width (.-clientWidth node)
                       :height (.-clientHeight node)})]]))})))

(defn series-tsplot [ series-name current-time query-window ]
  (let [series-data (reagent/atom nil)
        subscription (server/snap-and-subscribe-plot-data series-name query-window #(reset! series-data %))]
    (reagent/create-class
     {:display-name (str "series-tsplot-" series-name)

      :component-will-unmount
      (fn [ this ]
        (server/unsubscribe-plot-data subscription))

      :component-will-update
      (fn [ this new-argv ]
        (let [[ _ _ _ old-query-window ] (reagent/argv this)
              [ _ _ _ query-window] new-argv ]
          (when (not (= old-query-window query-window))
            (server/update-plot-query-window subscription query-window))))

      :reagent-render
      (fn [ series-name current-time query-window ]
        [series-tsplot-view series-name @series-data (server/range-ending-at current-time query-window)])})))

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
  [:div.series-list
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
  [:div.dashboard
   [header]
   [series-list]])

(defn on-window-resize [ evt ]
  (reset! window-width (.-innerWidth js/window)))

(log/set-configuration! {"" :info})

(defn update-current-time []
  (let [t (time/now)]
    (log/debug "update-current-time: " t)
    (reset! current-time t)))

(defonce update-interval-id
  (js/setInterval update-current-time update-interval-ms))

(defn ^:export run []
  (reagent/render [dashboard] (js/document.getElementById "metlog"))
  (.addEventListener js/window "resize" on-window-resize)
  (server/fetch-series-names #(swap! dashboard-state merge {:all-series %}))
  (server/fetch-dashboard-series #(swap! dashboard-state merge {:displayed-series %}))
  (log/debug "init complete. update-interval-id:" update-interval-id))

(run)
