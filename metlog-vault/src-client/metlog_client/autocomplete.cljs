(ns metlog-client.autocomplete
  (:require [reagent.core :as reagent]))

(defn- filter-completions [ completions filter-string ]
  (filter #(> (.indexOf % filter-string) -1)
          completions))

(defn- number-elements [ seq ]
  (map list (range) seq))

(defn- autocomplete-entry [ content selected? ]
  [:div.autocomplete-completion-entry (if selected? ">> ") content])

(defn- modular-inc [ number delta ]
  #(mod (+ % delta) number))

(defn- autocomplete-handle-keypress [completions state key-event ]
  (.error js/console (.-key key-event) (pr-str @state))
  
  (case (.-key key-event)
    "ArrowUp" (swap! state update :selected-index (modular-inc (count completions) -1))
    "ArrowDown" (swap! state update :selected-index (modular-inc (count completions) 1))
    nil
    ))

(defn input-field [ get-series-state ]
  (let [ state (reagent/atom {:current-text ""
                              :filter-text "19096"
                              :selected-index 0}) ]
    (fn []
      (let [ completions (filter-completions (get-series-state) (:filter-text @state))]
        [:div.header-element
         [:div.autocomplete
          [:input {:value (:current-text @state)
                   :onKeyDown #(autocomplete-handle-keypress completions state %)
                   :onChange #(swap! state assoc :current-text (.. % -target -value))}]
          [:div.autocomplete-completions
           (doall
            (for [ [ii completion] (number-elements completions)]
              #^{:key completion} [autocomplete-entry completion (= ii (:selected-index @state))]))]]]))))
