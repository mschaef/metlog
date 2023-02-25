(ns metlog-vault.dashboard
  (:use compojure.core
        playbook.core
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [hiccup.page :as hiccup-page]
            [hiccup.form :as hiccup-form]
            [ring.util.response :as ring]
            [clojure.data.json :as json]
            [playbook.hashid :as hashid]
            [metlog-vault.data :as data]))

(defn- resource [ path ]
  (str "/" (get-version) "/" path))

(defn- post-button [ attrs body ]
  (let [ target (:target attrs)]
    [:span.clickable.post-button
     (cond-> {:onclick (if-let [next-url (:next-url attrs)]
                         (str "window._metlog.doPost('" target "'," (json/write-str (get attrs :args {})) ", '" next-url "')")
                         (str "window._metlog.doPost('" target "'," (json/write-str (get attrs :args {})) ")"))}
       (:shortcut-key attrs) (merge {:data-shortcut-key (:shortcut-key attrs)
                                     :data-target target}))
     body]))

(defn- render-page [ & contents ]
  (hiccup-page/html5
   [:html
    [:head {:name "viewport"
           ;; user-scalable=no fails to work on iOS n where n > 10
            :content "width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0"}
     (hiccup-page/include-css (resource "metlog.css"))
     [:script {:type "module" :src (resource "metlog.js")}]
     [:title "Metlog - " (get-version)]]
    [:body
     contents]]))

(defn- series-select [ dashboard-name ]
  [:select {:id "new-series" :name "new-series"
            :onchange "window._metlog.onAddSeriesChange(event)"}
   (hiccup-form/select-options
    (map (fn [ series-name ]
           [ series-name series-name ])
         (cons "-" (data/get-series-names))))])

(defn- header [ dashboard-id ]
  [:div.header
   [:span#app-name "Metlog"]

   [:div#add-series.header-element
    (series-select dashboard-id)]

   [:div.header-element
    (hiccup-form/text-field { :id "query-window" :maxlength "8" } "query-window" "1d")]])

(defn- series-pane [ dashboard-id index series-name ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series-name]
    (hiccup-form/form-to
     {:class "close-form"}
     [:post (str "/dashboard/" (hashid/encode :db dashboard-id) "/delete-by-index/" index)]
     (post-button {:target (str "/dashboard/" (hashid/encode :db dashboard-id) "/delete-by-index/" index)} "close"))]
   [:div.tsplot-container
    [:canvas.series-plot {:data-series-name series-name}]]])

(defn- get-dashboard [ id ]
  (if-let [ dashboard (data/get-dashboard-by-id id)]
    (if-let [ parsed (or (try-parse-json (:definition dashboard))
                         [])]
      (assoc dashboard :definition parsed))))

(defn- render-dashboard [ id ]
  (when-let [ dashboard (get-dashboard id)]
    (let [ displayed-series (:definition dashboard)]
      (render-page
       [:div.dashboard
        (header (:name dashboard))
        [:div.series-list
         (map-indexed (partial series-pane (:dashboard_id dashboard)) displayed-series)]]))))

(defn- add-dashboard-series [ dashboard-id req ]
  (let [new-series (:new-series (:params req))]
    (when (not (= "-" new-series))
      (log/info "Adding series " new-series " to dashboard " dashboard-id)
      (data/update-dashboard-definition
       dashboard-id
       (conj (:definition (get-dashboard dashboard-id)) new-series)))
    (success)))

(defn- delete-dashboard-series-by-index [ dashboard-id req ]
  (let [index (try-parse-integer (:index (:params req)))]
    (log/info "Deleting series index" index "from dashboard " dashboard-id)
    (when index
      (data/update-dashboard-definition
       dashboard-id
       (drop-nth index (:definition (get-dashboard dashboard-id)))))
    (success)))

(defn- get-default-dashboard-id [ ]
  (or
   (:dashboard_id
    (data/get-dashboard-by-name "default" ))
   (data/insert-dashboard-definition "default" [])))

(defn redirect-to-default-dashboard []
  (ring/redirect (str "/dashboard/" (hashid/encode :db (get-default-dashboard-id)))))

(defn all-routes [ dashboard-id ]
  (when-let-route [ dashboard-id (hashid/decode :db dashboard-id) ]
    (GET "/" [ ]
      (render-dashboard dashboard-id))

    (POST "/add-series" req
      (add-dashboard-series dashboard-id req))

    (POST "/delete-by-index/:index" req
      (delete-dashboard-series-by-index dashboard-id req))))
