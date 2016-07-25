(ns metlog-client.autocomplete
  (:require [reagent.core :as reagent]))

(defn- filter-completions [ completions filter-string ]
  (filter #(> (.indexOf % filter-string) -1)
          completions))

(defn- number-elements [ seq ]
  (map list (range) seq))

(defn- modular-inc [ number delta ]
  #(mod (+ % delta) number))

(defn- autocomplete-select-completion [ state completions index ]
  (swap! state merge {:selected-index index
                      :update-filter-text false
                      :current-text (nth completions index)
                      :display-completions true}))

(defn- autocomplete-navigate [ state completions direction ]
  (autocomplete-select-completion state completions
                                  (mod (+ (:selected-index @state) direction)
                                       (count completions))))

(defn- autocomplete-hide-completions [ state ]
  (swap! state merge {:display-completions false
                      :selected-index -1
                      :update-filter-text true}))

(defn- autocomplete-entry [ content selected? ]
  [:div {:class (str "autocomplete-completion-entry "
                     (if selected? "selected"))}
   content])

(defn autocomplete-completions [ completions state ]
  (let [selected-index  (:selected-index @state)]
    [:div.autocomplete-completions
     (if (empty? completions)
       [:div.autocomplete-no-results "No Matching Values"]
       (doall
        (for [ [ii completion] (number-elements completions)]
          #^{:key completion} [autocomplete-entry completion (= ii selected-index)
                               #(autocomplete-select-completion state completions ii)])))]))

(defn accept-text [ state new-text ]
  (swap! state assoc :current-text new-text)
  (when (:update-filter-text @state)
    (swap! state assoc :filter-text new-text)))

(defn- autocomplete-enter [ state on-enter ]
  (when on-enter
    (autocomplete-hide-completions state)
    (accept-text state (on-enter (:current-text @state)))))

(defn- autocomplete-handle-keydown [ completions state on-enter key-event ]
  (case (.-key key-event)
    "ArrowUp" (autocomplete-navigate state completions -1)
    "ArrowDown" (autocomplete-navigate state completions 1)
    "Escape" (autocomplete-hide-completions state)
    "Enter" (autocomplete-enter state on-enter)
    (swap! state merge {:display-completions true
                        :update-filter-text true
                        :selected-index -1})))

(defn input-field [ { :keys [ get-completions placeholder on-enter ] } ]
  (let [ state (reagent/atom {:display-completions false
                              :current-text ""
                              :filter-text ""
                              :selected-index -1
                              :update-filter-text true}) ]
    (fn []
      (let [ completions (filter-completions (get-completions) (:filter-text @state))]
        [:div.autocomplete
         [:input {:value (:current-text @state)
                  :placeholder placeholder
                  :onBlur #(swap! state assoc :display-completions false)
                  :onKeyDown #(autocomplete-handle-keydown completions state on-enter %)
                  :onChange #(accept-text state (.. % -target -value))}]
         (when (:display-completions @state)
           [autocomplete-completions completions state])]))))


