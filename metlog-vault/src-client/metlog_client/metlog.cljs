(ns metlog-client.metlog
  (:require-macros [cljs.core.async.macros :refer [ go ]])
  (:require [om.core :as om]
            [om.dom :as dom]
            [metlog.tsplot :as tsplot]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
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
  (ajax-get (str "/data/" series-name "?query-window-secs=" query-window-secs) cb))

(defn schedule-tsplot-for-data [ owner query-window-secs ]
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
      (let [dom-element (om/get-node owner)
            resize-func (fn []
                          (om/set-state! owner :width (.-offsetWidth (.-parentNode dom-element))))]
        (resize-func)
        (aset js/window "onresize" resize-func)
        (schedule-tsplot-for-data owner (or (om/get-state owner :query-window-secs) 86400))))
    
    om/IWillReceiveProps
    (will-receive-props [ this next-props ]
      (schedule-tsplot-for-data owner (:query-window-secs (:app-cursor next-props))))
    
    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (tsplot/draw (.getContext (om/get-node owner) "2d")
                   (om/get-state owner :width) (om/get-state owner :height)
                   (om/get-state owner :data)))
    
    om/IRenderState
    (render-state [ this state ]
      (dom/canvas #js {:width (str (om/get-state owner :width) "px")
                       :height (str (om/get-state owner :height) "px")}))))

(defn series-pane [ { state :series-cursor app :app-cursor } owner ]
  (om/component
   (dom/div #js { :className "series-pane"}
            (dom/div #js { :className "series-pane-header "}
                     (dom/span #js { :className "series-name"} (:name state)))
            (om/build series-tsplot { :series-cursor state :app-cursor app }))))

(defn series-list [ state owner ]
  (reify    
    om/IRender
    (render [ this ]
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
      (dom/div #js { :className "header"}
               (dom/span #js { :className "left" }
                         "Metlog"
                         (dom/input #js {:value (:text state)
                                         :onChange #(handle-change % owner state app-state)
                                         :onKeyDown #(when (= (.-key %) "Enter")
                                                       (end-edit (:text state) app-state))}))))))

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
