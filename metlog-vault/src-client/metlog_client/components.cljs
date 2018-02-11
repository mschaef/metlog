(ns metlog-client.components
  (:require [reagent.core :as reagent]
            [metlog-client.logger :as log]))

(defn input-field [ { :keys [ text text-valid? on-enter] } ]
  (defn initial-state [ text ]
    {:initial-text text :current-text text :uncommitted? false})
  (let [ state (reagent/atom (initial-state text)) ]
    (fn [ { :keys [ text text-valid? on-enter] } ]
      (when (not (= text (:initial-text @state)))
        (swap! state merge  (initial-state text)))
      (let [valid? (text-valid? (:current-text @state))]
        [:input {:value (:current-text @state)
                 :class (str (if (not valid?) "invalid")
                             (if (:uncommitted? @state) " uncommitted"))
                 :onChange #(swap! state merge {:current-text (.. % -target -value)
                                                :uncommitted? true})
                 :onKeyDown #(when (and valid?
                                        (= (.-key %) "Enter"))
                               (on-enter (:current-text @state)))}]))))
