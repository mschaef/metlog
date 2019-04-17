(ns metlog-client.autocomplete
  (:require [reagent.core :as reagent]
            [reagent.debug :as debug]
            [metlog-client.logger :as log]))

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

(defn- ensure-entry-visible [ container-elem entry-elem ]
  (cond
    (< (.-offsetTop entry-elem)
       (.-scrollTop container-elem))
    (aset container-elem "scrollTop" (.-offsetTop entry-elem))

    (> (+ (.-offsetTop entry-elem) (.-clientHeight entry-elem))
       (+ (.-scrollTop container-elem) (.-clientHeight container-elem)))
    ;; Place the bottom edge of the entry against the bottom edge
    ;; of the container.
    (aset container-elem "scrollTop" (- (.-offsetTop entry-elem)
                                        (- (.-clientHeight container-elem)
                                           (.-clientHeight entry-elem))))))

(defn autocomplete-entry [ ]
  (reagent/create-class
   {:component-did-update
    (fn [ this old-argv ]
      (let [[ _ _ _ was-selected? ] old-argv
            [ _ container-elem _ is-selected? ] (reagent/argv this) ]
        (when (and is-selected? (not was-selected?))
          (ensure-entry-visible container-elem (reagent/dom-node this)))))
    
    :reagent-render
    (fn [ container-elem content selected? ]
      [:div {:class (str "autocomplete-completion-entry "
                         (if selected? "selected"))}
       content])}))

(defn autocomplete-completions [ _ _ ]
  (let [ dom-node (reagent/atom nil ) ]
    (reagent/create-class
     {:component-did-mount
      (fn [ this ]
        (reset! dom-node (reagent/dom-node this)))

      :reagent-render
      (fn [ completions selected-index ]
        [:div.autocomplete-completions
         (if (empty? completions)
           [:div.autocomplete-no-results "No Matching Values"]
           (doall
            (for [ [ii completion] (number-elements completions)]
              #^{:key completion} [autocomplete-entry @dom-node completion (= ii selected-index)])))])})))

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
    "ignore"))

(defn- autocomplete-handle-keypress [ state key-event ]
  (when (not (= "Enter" (.-key key-event)))
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
                  :onKeyPress #(autocomplete-handle-keypress state %)
                  :onChange #(accept-text state (.. % -target -value))}]
         (when (:display-completions @state)
           [autocomplete-completions completions (:selected-index @state)])]))))
