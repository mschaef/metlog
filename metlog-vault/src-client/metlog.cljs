(ns metlog.metlog
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [om.dom :as dom]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! close! chan <!]]))

(def dashboard-state (atom {:server-time nil
                            :series-names []}))

(defn <<< [f & args]
  (let [c (chan)]
    (apply f (concat args [(fn [x]
                             (if (or (nil? x)
                                     (undefined? x))
                                   (close! c)
                                   (put! c x)))]))
    c))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(defn ajax-get [ url callback ]
  (GET url {:handler callback
            :error-handler error-handler}))

(defn fetch-series-names [ cb ]
  (ajax-get "/series-names" cb))

(defn fetch-server-time [ cb ]
  (ajax-get "/server-time" cb))

(defn series-pane [ state owner ]
  (om/component
   (dom/div nil state)))

(defn series-list [ state owner ]
  (reify
    om/IWillMount
    (will-mount [ this ]
      (go
        (om/update! state :series-names (<! (<<< fetch-series-names)))))

    om/IRender
    (render [ this ]
      (apply dom/div nil
             (map #(om/build series-pane %)
                  (:series-names state))))))

(defn server-time [ state owner ]
  (reify
    om/IWillMount
    (will-mount [ this ]
      (js/setInterval
       (fn []
         (go
           (om/update! state :server-time (<! (<<< fetch-server-time)))))
       1000))

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
            (om/build series-list state))))

(om/root dashboard dashboard-state
  {:target (. js/document (getElementById "metlog"))})

