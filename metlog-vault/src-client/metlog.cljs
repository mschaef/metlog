(ns metlog.metlog
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [om.dom :as dom]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]
            [cljs.core.async :refer [put! chan <!]]))

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

(defn update-series-names [ response ]
  (let [ series-names (reader/read-string (str response))]
    (.log js/console series-names)
    (swap! dashboard-state assoc :series-names series-names)))

(defn ajax-get [ url callback ]
  (GET url {:handler callback
            :error-handler error-handler}))

(defn fetch-series-names []
  (ajax-get "/series-names" update-series-names))

(defn fetch-server-time [ cb ]
  (ajax-get "/server-time" cb))

(def dashboard-state (atom {:count 0
                            :series-names []}))


(defn item-list [ items owner ]
  (om/component
   (apply dom/ul nil
          (map (fn [text] (dom/li nil text))
               items))))

(defn counter [ state owner ]
  (reify
    om/IWillMount
    (will-mount [ this ]
      (js/setInterval
       (fn []
         (om/transact! state :count #(+ 1 %)))
       1000))

    om/IRender
    (render [ this ]
      (dom/div nil
               (dom/h1 nil (:count state))))))

(defn dashboard [ state owner ]
  (om/component
      (dom/div nil
            (om/build counter state)
            (om/build item-list (:series-names state)))))

(om/root dashboard dashboard-state
  {:target (. js/document (getElementById "metlog"))})

(fetch-series-names)

(go
  (js/console.log (<! (<<< fetch-server-time))))