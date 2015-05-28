(ns metlog.metlog
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [om.dom :as dom]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
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


(defn draw-tsplot [ ctx ]
;  (aset ctx "fillStyle" "rgb(255,255,255)")
  (.fillRect ctx 10 10 100 100))

(defn series-tsplot [ app-state owner ]
  (reify
    om/IInitState
    (init-state [_]
      
      {:width 800 :height 150})
    
    om/IDidMount
    (did-mount [_]
      (let [dom-element (om/get-node owner)
            resize-func (fn []
                          (om/set-state! owner :width (.-offsetWidth (.-parentNode dom-element))))]
 ;     (resize-func)
      (aset js/window "onresize" resize-func)
      (draw-tsplot (.getContext dom-element "2d"))))
    
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
                    (str (.toLocaleDateString server-time) " "
                         (.toLocaleTimeString server-time))))))))

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
