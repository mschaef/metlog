(ns metlog-client.autocomplete
  (:require [reagent.core :as reagent]))

(defn- filter-completions [ completions filter-string ]
  (filter #(> (.indexOf % filter-string) -1)
          completions))

(defn- number-elements [ seq ]
  (map list (range) seq))

(defn- autocomplete-entry [ content selected? ]
  [:div {:class (str "autocomplete-completion-entry "
                     (if selected? "selected"))}
   content])

(defn- modular-inc [ number delta ]
  #(mod (+ % delta) number))

(defn- autocomplete-navigate [ state completions direction ]
  (let [ next-index (mod (+ (:selected-index @state) direction) (count completions))]
    (swap! state merge {:selected-index next-index
                        :current-text (nth completions next-index)
                        :display-completions true})))

(defn- autocomplete-hide-completions [ state ]
  (swap! state merge {:display-completions false
                      :selected-index -1}))

(defn- autocomplete-enter [ state on-enter ]
  (when on-enter
    (on-enter (:current-text @state))
    (autocomplete-hide-completions state)))

(defn- autocomplete-handle-keypress [ completions state on-enter key-event ]
  (case (.-key key-event)
    "ArrowUp" (autocomplete-navigate state completions -1)
    "ArrowDown" (autocomplete-navigate state completions 1)
    "Escape" (autocomplete-hide-completions state)
    "Enter" (autocomplete-enter state on-enter)
    (swap! state assoc :display-completions true)))

(defn autocomplete-completions [ completions selected-index ]
  [:div.autocomplete-completions
   (doall
    (for [ [ii completion] (number-elements completions)]
      #^{:key completion} [autocomplete-entry completion (= ii selected-index)]))])

(defn input-field [ { :keys [ get-completions placeholder on-enter ] } ]
  (let [ state (reagent/atom {:display-completions false
                              :current-text ""
                              :filter-text ""
                              :selected-index -1}) ]
    (fn []
      (let [ completions (filter-completions (get-completions) (:filter-text @state))]
        [:div.autocomplete
         [:input {:value (:current-text @state)
                  :placeholder placeholder
                  :onBlur #(swap! state assoc :display-completions false)
                  :onKeyDown #(autocomplete-handle-keypress completions state on-enter %)
                  :onChange #(do
                               (swap! state assoc :current-text (.. % -target -value))
                               (when (= -1 (:selected-index @state))
                                 (swap! state assoc :filter-text (.. % -target -value))))}]
         (when (:display-completions @state)
           [autocomplete-completions completions (:selected-index @state)])]))))


