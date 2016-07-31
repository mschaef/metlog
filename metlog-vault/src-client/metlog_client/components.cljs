(ns metlog-client.components
  (:require [reagent.core :as reagent]))

(defn input-field [ { :keys [ initial-text text-valid? on-enter] } ]
  (let [ state (reagent/atom { :text initial-text }) ]
    (fn []
      (let [valid? (text-valid? (:text @state))]
        [:input {:value (:text @state)
                 :class (if (not valid?) "invalid")
                 :onChange #(swap! state assoc :text (.. % -target -value))
                 :onKeyDown #(when (and valid?
                                        (= (.-key %) "Enter"))
                               (on-enter (:text @state)))}]))))
