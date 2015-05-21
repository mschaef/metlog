(ns metlog.metlog
  (:require [om.core :as om]
            [om.dom :as dom]
            [ajax.core :refer [GET]]
            [cljs.reader :as reader]))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(def dashboard-state (atom {:count 0
                            :title "Hello World!"
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
            (dom/h1 nil (:title state))
            (om/build counter state)
            (om/build item-list (:series-names state)))))

(om/root dashboard dashboard-state
  {:target (. js/document (getElementById "metlog"))})

(defn update-series-names [ response ]
  (let [ series-names (reader/read-string (str response))]
    (.log js/console series-names)
    (swap! dashboard-state assoc :series-names series-names)))

(defn fetch-series-names []
  (GET "/series-names" {:handler update-series-names
                        :error-handler error-handler}))


(fetch-series-names)
