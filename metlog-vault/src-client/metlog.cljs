(ns metlog.metlog
  (:require-macros [cljs.core.async.macros :refer [ go ]]
                   [metlog.macros :refer [ with-preserved-ctx unless ]])
  (:require [om.core :as om]
            [om.dom :as dom]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [cljs-time.coerce :as time-coerce]
            [cljs.core.async :refer [put! close! chan <!]]))

(def dashboard-state (atom {:series [] :query-window-secs (* 3600 4)}))

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
  (.log js/console "fsd: " (pr-str [ series-name query-window-secs ]))
  (ajax-get (str "/data/" series-name "?query-window-secs=" query-window-secs) cb))

(defn s-yrange [ samples ]
  (let [ vals (map :val samples) ]
    {:max (apply Math/max vals)
     :min (apply Math/min vals)}))

(defn s-xrange [ samples ]
  (let [ ts (map :t samples) ]
    {:max (apply Math/max ts)
     :min (apply Math/min ts)}))

(defn rescale-range [ range factor ]
  (let [ scaled-delta (* (/ (- factor 1) 2) (- (:max range) (:min range) )) ]
    {:max (+ (:max range) scaled-delta)
     :min (- (:min range) scaled-delta)}))

(defn range-scale [ range val ]
  (/ (- val (:min range))
     (- (:max range) (:min range))))

(defn draw-tsplot-xlabel [ ctx text x y left? ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (if left? x (- x w)) (+ 12 y))))

(defn draw-tsplot-ylabel [ ctx text x y ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (- x w) (+ y 8))))

(def dtf-axis-label (time-format/formatter "MM-dd HH:mm"))

(def dtf-header (time-format/formatter "yyyy-MM-dd HH:mm"))

(defn translate-point [ pt x-range y-range w h ]
  [(* w (range-scale x-range (:t pt)))
   (- h (* h (range-scale y-range (:val pt))))])

(defn clip-rect [ ctx x y w h ]
  (.beginPath ctx)
  (.rect ctx x y w h)
  (.clip ctx))

(defn draw-tsplot-series-line [ ctx data x-range y-range w h ]
  (.beginPath ctx)
  (let [ data-scaled (map #(translate-point % x-range y-range w h) data) ]
    (let [ [ pt-x pt-y ] (first data-scaled) ]
      (.moveTo ctx pt-x pt-y))
    (doseq [ [ pt-x pt-y ] (rest data-scaled) ]
      (.lineTo ctx pt-x pt-y)))
  (.stroke ctx))

(defn format-xlabel [ val ]
  (time-format/unparse dtf-axis-label (time-coerce/from-long val)))

(defn format-ylabel [ val ]
  (.toFixed val 2))

(defn draw-tsplot-series [ ctx w h data ]
  (with-preserved-ctx ctx
    (aset ctx "lineWidth" 0)
    (aset ctx "strokeStyle" "#0000FF")
    (aset ctx "font" "12px Arial")
    (let [x-range (s-xrange data)
          y-range (rescale-range (s-yrange data) 1.2)]
      (unless (empty? data)
        (with-preserved-ctx ctx
          (clip-rect ctx 0 0 w h)
          (draw-tsplot-series-line ctx data x-range y-range w h))
        (draw-tsplot-ylabel ctx (format-ylabel (:min y-range)) -2 (- h 8))
        (draw-tsplot-ylabel ctx (format-ylabel (:max y-range)) -2 8)
        (draw-tsplot-xlabel ctx (format-xlabel (:min x-range)) 0 h true)
        (draw-tsplot-xlabel ctx (format-xlabel (:max x-range)) w h false)))))

(defn draw-tsplot-bg [ ctx w h ]
  (with-preserved-ctx ctx
    (aset ctx "fillStyle" "#FFFFFF")
    (.fillRect ctx 0 0 w h)))

(defn draw-tsplot-frame [ ctx w h ]
  (with-preserved-ctx ctx
    (.beginPath ctx)
    (aset ctx "lineWidth" 1)
    (aset ctx "strokeStyle" "#000000")
    (.moveTo ctx 0.5 0.5)
    (.lineTo ctx 0.5 (- h 0.5))
    (.lineTo ctx (- w 0.5) (- h 0.5))
    (.stroke ctx)))

(def x-axis-space 20)
(def tsplot-right-margin 5)
(def y-axis-space 40)

(defn draw-tsplot [ ctx w h sinfo ]
  (let [w (- w y-axis-space tsplot-right-margin)
        h (- h x-axis-space)]
    (with-preserved-ctx ctx
      (.translate ctx y-axis-space 0)
      (draw-tsplot-bg ctx w h)
      (draw-tsplot-series ctx w h (:data sinfo))
      (draw-tsplot-frame ctx w h))))

(defn schedule-tsplot-for-data [ owner query-window-secs ]
  (.log js/console "stfd: " (pr-str query-window-secs))
  (go
    (om/set-state! owner :data (<! (<<< fetch-series-data
                                        (om/get-state owner :name)
                                        query-window-secs)))))

(defn series-tsplot [ { state :series-cursor app :app-cursor } owner ]
  (reify
    om/IInitState
    (init-state [_]
      {:width 1024 :height 180 :data nil :name (:name state)})
    
    om/IDidMount
    (did-mount [ state ]
      (.log js/console "series-tsplot-did-mount")
      (let [dom-element (om/get-node owner)
            resize-func (fn []
                          (om/set-state! owner :width (.-offsetWidth (.-parentNode dom-element))))]
        (resize-func)
        (aset js/window "onresize" resize-func)
        (schedule-tsplot-for-data owner (or (om/get-state owner :query-window-secs) 86400))))
    
    om/IWillReceiveProps
    (will-receive-props [ this next-props ]
      (.log js/console "np: " (pr-str next-props))
      (schedule-tsplot-for-data owner (:query-window-secs (:app-cursor next-props))))
    
    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (.log js/console "series-tsplot-did-update")

      (draw-tsplot (.getContext (om/get-node owner) "2d")
                   (om/get-state owner :width) (om/get-state owner :height)
                   (om/get-state owner :data)))
    
    om/IRenderState
    (render-state [ this state ]
      (.log js/console "series-tsplot-render-state")
      (dom/canvas #js {:width (str (om/get-state owner :width) "px")
                       :height (str (om/get-state owner :height) "px")}))))

(defn series-pane [ { state :series-cursor app :app-cursor } owner ]
  (om/component
   (.log js/console "re-render series-pane")
   (dom/div #js { :className "series-pane"}
            (dom/div #js { :className "series-pane-header "}
                     (dom/span #js { :className "series-name"} (:name state)))
            (om/build series-tsplot { :series-cursor state :app-cursor app }))))

(defn series-list [ state owner ]
  (reify    
    om/IRender
    (render [ this ]
      (.log js/console "re-render series-list")
      (apply dom/div nil
             (map #(om/build series-pane { :series-cursor % :app-cursor state}) (:series state))))))

(defn handle-change [ evt owner state app-state ]
  (om/set-state! owner :text (.. evt -target -value)))

(defn end-edit [ text app-state ]
  (let [ qws (js/parseInt text) ]
    (om/update! app-state [:query-window-secs] qws)))

(defn header [ app-state owner ]
  (reify
    om/IRenderState
    (render-state [ this state ]
      (.log js/console "re-render header")
      (dom/div #js { :className "header"}
               (dom/span #js { :className "left" }
                         "Metlog"
                         (dom/input #js {:value (:text state)
                                         :onChange #(handle-change % owner state app-state)
                                         :onKeyDown #(when (= (.-key %) "Enter")
                                                       (end-edit (:text state) app-state))})
               (dom/span #js { :className "right" }
                         "&nbsp;"))))))

(defn dashboard [ state owner ]
  (om/component
   (.log js/console "re-render dashboard")
      (dom/div nil
               (om/build header state)
               (dom/div #js { :className "content" }
                (om/build series-list state)))))

(om/root dashboard dashboard-state
  {:target (. js/document (getElementById "metlog"))})



(defn load-series-names []
  (go
    (swap! dashboard-state assoc :series
           (vec (map (fn [ series-name ] {:name series-name})
                     (<! (<<< fetch-series-names)))))))

(load-series-names)
