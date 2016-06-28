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

(defn- autocomplete-navigate [ state completions direction ]
  (let [ next-index (mod (+ (:selected-index @state) direction) (count completions))]
    (swap! state merge {:selected-index next-index
                        :current-text (nth completions next-index)
                        :display-completions true})))

(defn- autocomplete-handle-keypress [completions state key-event ]
  (.error js/console (.-key key-event) (pr-str @state))
  
  (case (.-key key-event)
    "ArrowUp" (autocomplete-navigate state completions -1)
    "ArrowDown" (autocomplete-navigate state completions 1)
    "Escape" (swap! state merge {:display-completions false
                                 :selected-index -1})
    (swap! state assoc :display-completions true)))

(defn autocomplete-completions [ completions selected-index ]
  [:div.autocomplete-completions
   (doall
    (for [ [ii completion] (number-elements completions)]
      #^{:key completion} [autocomplete-entry completion (= ii selected-index)]))])

(defn input-field [ get-series-state ]
  (let [ state (reagent/atom {:display-completions false
                              :current-text ""
                              :filter-text "19096"
                              :selected-index -1}) ]
    (fn []
      (let [ completions (filter-completions (get-series-state) (:filter-text @state))]
        [:div.header-element
         [:div.autocomplete
          [:input {:value (:current-text @state)
                   :onKeyDown #(autocomplete-handle-keypress completions state %)
                   :onChange #(do
                                (swap! state assoc :current-text (.. % -target -value))
                                (when (= -1 (:selected-index @state))
                                  (swap! state assoc :filter-text (.. % -target -value))))}]
          (when (:display-completions @state)
            [autocomplete-completions completions (:selected-index @state)])]]))))

(( 1) 2)
