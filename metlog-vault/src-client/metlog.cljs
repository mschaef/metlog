(ns metlog.metlog
  (:require-macros [cljs.core.async.macros :refer [ go ]]
                   [metlog.macros :refer [ with-preserved-ctx ]])
  (:require [om.core :as om]
            [om.dom :as dom]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [cljs-time.coerce :as time-coerce]
            [cljs.core.async :refer [put! close! chan <!]]))

(def dashboard-state (atom {:server-time nil
                            :series []}))

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

(defn fetch-server-time [ cb ]
  (ajax-get "/server-time" cb))

(defn fetch-latest-series-data [ series-name cb ]
  (ajax-get (str "/latest/" series-name) cb))

(defn fetch-series-data [ series-name cb ]
  (ajax-get (str "/data/" series-name) cb))

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

(defn draw-tsplot-xlabel [ ctx text x y ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (- x (/ w 2)) (+ 12 y))))

(defn draw-tsplot-ylabel [ ctx text x y ]
  (let [mt (.measureText ctx text)
        w (.-width mt)]
    (.fillText ctx text (- x w) (+ y 8))))

(def dtf-axis-label (time-format/formatter "MM-dd HH"))

(def dtf-header (time-format/formatter "yyyy-MM-dd HH:mm"))

(defn translate-point [ pt x-range y-range w h ]
  [(* w (range-scale x-range (:t pt)))
   (- h (* h (range-scale y-range (:val pt))))])

(defn move-to [ ctx [ x y ] ] (.moveTo ctx x y))
(defn line-to [ ctx [ x y ] ] (.lineTo ctx x y))

(defn clip-rect [ ctx x y w h ]
  (.beginPath ctx)
  (.rect ctx x y w h)
  (.clip ctx))

(defn draw-tsplot-series [ ctx w h data ]
  (with-preserved-ctx ctx
    (aset ctx "strokeStyle" "#0000FF")
    (aset ctx "font" "12px Arial")
    (let [x-range (s-xrange data)
          y-range (rescale-range (s-yrange data) 1.2)]
      (when (> (count data) 0)
        (with-preserved-ctx ctx
          (clip-rect ctx 0 0 w h)
          (.beginPath ctx)
          (move-to ctx (translate-point (first data) x-range y-range w h))
          (doseq [ pt (rest data) ]
            (line-to ctx (translate-point pt x-range y-range w h)))
          (.stroke ctx))
        (draw-tsplot-ylabel ctx (.toFixed (:max y-range) 2) -2 8)
        (draw-tsplot-ylabel ctx (.toFixed (:min y-range) 2) -2 (- h 8))
        (draw-tsplot-xlabel ctx (time-format/unparse dtf-axis-label (time-coerce/from-long (:min x-range))) 0 h)
        (draw-tsplot-xlabel ctx (time-format/unparse dtf-axis-label (time-coerce/from-long (:max x-range))) w h)))))

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

(defn draw-tsplot [ ctx w h sinfo ]
  (let [ w (- w 30) ]
    (with-preserved-ctx ctx
      (.translate ctx 50 0)
      (draw-tsplot-bg ctx (- w 50) (- h 30))
      (draw-tsplot-series ctx (- w 50) (- h 30) (:data sinfo))
      (draw-tsplot-frame ctx (- w 50) (- h 30)))))

(defn series-tsplot [ app-state owner ]
  (reify
    om/IInitState
    (init-state [_]
      
      {:width 1024 :height 150})
    
    om/IDidMount
    (did-mount [_]
      (let [dom-element (om/get-node owner)
            resize-func (fn []
                          (om/set-state! owner :width (.-offsetWidth (.-parentNode dom-element))))]
 ;     (resize-func)
      (aset js/window "onresize" resize-func)

      (go
        (draw-tsplot (.getContext dom-element "2d")
                     1024 150
                     (<! (<<< fetch-series-data (:name app-state)))))))
    
    om/IRenderState
    (render-state [ this state ]
      (dom/canvas #js {:width (str (:width state) "px")
                       :height (str (:height state) "px")}))))

(defn series-pane [ state owner ]
  (reify
    om/IWillMount
    (will-mount [ this ]
      (js/setInterval
       (fn []
         (go
           (let [ resp (<! (<<< fetch-latest-series-data (:name state)))]
             (om/update! state :val (:val resp)))))
       15000))
    
    om/IRender
    (render [ this ]
      (dom/div #js { :className "series-pane"}
               (dom/div #js { :className "series-pane-header "}
                        (dom/span #js { :className "series-name"} (:name state))
                        (dom/span #js { :className "series-value" } (:val state)))
               (om/build series-tsplot state)))))

(defn series-list [ state owner ]
  (reify    
    om/IRender
    (render [ this ]
      (apply dom/div nil
             (om/build-all series-pane (:series state))))))


(defn server-time [ state owner ]
  (reify
    om/IWillMount
    (will-mount [ this ]
      (js/setInterval
       (fn []
         (go
           (om/update! state :server-time (<! (<<< fetch-server-time)))))
       15000))

    om/IRender
    (render [ this ]
      (dom/span nil
                (let [ server-time (:server-time state)]
                  (if (nil? server-time)
                    ""
                    (time-format/unparse dtf-header (time-coerce/to-date-time server-time)) ))))))

(defn header [ state owner ]
  (om/component
   (dom/div #js { :className "header"}
            (dom/span #js { :className "left" } "Metlog")
            (dom/span #js { :className "right" }
                      (om/build server-time state)))))

(defn dashboard [ state owner ]
  (om/component
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
