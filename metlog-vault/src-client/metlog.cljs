(ns metlog.metlog
  (:require [om.core :as om]
            [om.dom :as dom]
            [ajax.core :refer [GET]]))

(defn handler [response]
  (.log js/console (str response)))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(defn series-names []
  (GET "/series-names" {:handler handler
                        :error-handler error-handler}))

(def dashboard-state (atom {:text "Hello World!"}))

(defn dashboard [state owner]
  (reify
    om/IRender
    (render [this]
      (dom/h1 nil (:text state)))))

(om/root dashboard dashboard-state
  {:target (. js/document (getElementById "metlog"))})



