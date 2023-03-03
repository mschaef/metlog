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

(defn dashboard-select [ dashboard-id ]
  [:select {:id "select-dashboard" :name "select-dashboard"
            :onchange "window._metlog.onDashboardSelectChange(event)"}
   (hiccup-form/select-options
    (concat
     (map (fn [ dashboard-info ]
            [ (:name dashboard-info) (hashid/encode :db (:dashboard_id dashboard-info))])
          (data/get-dashboard-names))
     [[ "Add Dashboard" ""]])
    (hashid/encode :db dashboard-id))])

(defn- series-select [ ]
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
    (dashboard-select dashboard-id)
    (post-button {:target (str "/dashboard/" (hashid/encode :db dashboard-id) "/delete")} "delete dashboard")]

   [:div.header-element
    (hiccup-form/text-field { :id "query-window" :maxlength "8" } "query-window" "1d")]])

(defn- add-series-pane [ ]
  [:div.series-pane
   [:div.series-pane-header "&nbsp;"]
   [:div.add-series-block
    "Add Series: "
    (series-select)]])

(defn- dashboard-link [ id & others ]
  (apply str "/dashboard/" (hashid/encode :db id) others))

(defn- series-pane [ dashboard-id index series-name ]
  (let [ delete-url (dashboard-link dashboard-id "/delete-by-index/" index)]
    [:div.series-pane
     [:div.series-pane-header
      [:span.series-name series-name]
      (hiccup-form/form-to
       {:class "close-form"}
       [:post delete-url]
       (post-button {:target delete-url} "close"))]
        [:div.tsplot-container
         [:canvas.series-plot {:data-series-name series-name}]]]))

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
        (header id)
        [:div.series-list
         (map-indexed (partial series-pane (:dashboard_id dashboard)) displayed-series)
         (add-series-pane)]]))))

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


(defn- ensure-dashboard-id-by-name [ dashboard-name ]
  (or
   (:dashboard_id
    (data/get-dashboard-by-name dashboard-name))
     (data/insert-dashboard-definition dashboard-name [])))

(defn- redirect-to-dashboard [ id ]
  (ring/redirect (dashboard-link id)))

(defn redirect-to-default-dashboard []
  (redirect-to-dashboard (ensure-dashboard-id-by-name "default")))

(defn- create-dashboard [ req ]
  (let [dashboard-name (.trim (:dashboard-name (:params req)))]
    (when (> (count dashboard-name) 0)
      (redirect-to-dashboard (ensure-dashboard-id-by-name dashboard-name )))))

(defn- delete-dashboard [ dashboard-id ]
  (data/delete-dashboard-by-id dashboard-id)
  (redirect-to-default-dashboard))

(defn dashboard-routes [ dashboard-id ]
  (when-let-route [ dashboard-id (hashid/decode :db dashboard-id) ]
    (GET "/" [ ]
      (render-dashboard dashboard-id))

    (POST "/delete" [ ]
      (delete-dashboard dashboard-id))

    (POST "/delete-by-index/:index" req
      (delete-dashboard-series-by-index dashboard-id req))))

(defn all-routes [ ]
  (routes
   (context "/:dashboard-id" [ dashboard-id ]
     (dashboard-routes dashboard-id))

   (POST "/" req
     (create-dashboard req))))
