(ns metlog-vault.routes
  (:use compojure.core
        playbook.core
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [compojure.route :as route]
            [hiccup.core :as hiccup]
            [hiccup.page :as hiccup-page]
            [hiccup.form :as hiccup-form]
            [ring.util.response :as ring]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [metlog-vault.data :as data]))

(defn resource [ path ]
  (str "/" (get-version) "/" path))

(defn- try-parse-json
  ([ str default-value ]
   (try
     (json/read-str str)
     (catch Exception ex
       default-value)))

  ([ str ]
   (try-parse-json str false)))

(defn success []
  (ring/response "ok"))

(defn post-button [ attrs body ]
  (let [ target (:target attrs)]
    [:span.clickable.post-button
     (cond-> {:onclick (if-let [next-url (:next-url attrs)]
                         (str "window._metlog.doPost('" target "'," (json/write-str (get attrs :args {})) ", '" next-url "')")
                         (str "window._metlog.doPost('" target "'," (json/write-str (get attrs :args {})) ")"))}
       (:shortcut-key attrs) (merge {:data-shortcut-key (:shortcut-key attrs)
                                     :data-target target}))
     body]))

(defn success []
  (ring/response "ok"))

(defn render-page [ & contents ]
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

(defn series-select [ dashboard-name ]
  [:select {:id "new-series" :name "new-series"
            :onchange "window._metlog.onAddSeriesChange(event)"}
   (hiccup-form/select-options
    (map (fn [ series-name ]
           [ series-name series-name ])
         (cons "-" (data/get-series-names))))])

(defn header [ dashboard-name ]
  [:div.header
   [:span#app-name "Metlog"]

   [:div#add-series.header-element
    (series-select dashboard-name)]

   [:div.header-element
    (hiccup-form/text-field { :id "query-window" :maxlength "8" } "query-window" "1d")]])

(defn series-pane [ dashboard-name index series-name ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name series-name]
    (hiccup-form/form-to
     {:class "close-form"}
     [:post (str "/dashboard/" dashboard-name "/delete-by-index/" index)]
     (post-button {:target (str "/dashboard/" dashboard-name "/delete-by-index/" index)} "close"))]
   [:div.tsplot-container
    [:canvas.series-plot {:data-series-name series-name}]]])

(defn get-dashboard [ dashboard-name ]
  (or (try-parse-json (data/get-dashboard-definition dashboard-name ))
      []))

(defn render-dashboard [ dashboard-name ]
  (let [ displayed-series (get-dashboard dashboard-name)]
    (render-page
     [:div.dashboard
      (header dashboard-name)
      [:div.series-list
       (map-indexed (partial series-pane dashboard-name) displayed-series)]])))

(defn get-series-data [ params ]
  {:body
   (vec
    (data/get-data-for-series-name (:series-name params)
                                   (try-parse-long (:begin-t params))
                                   (try-parse-long (:end-t params))))})

(defn add-dashboard-series [ req ]
  (let [name (:name (:params req))
        new-series (:new-series (:params req))]
    (when (not (= "-" new-series))
      (log/info "Adding series " new-series " to dashboard " name)
      (data/store-dashboard-definition name (json/write-str (conj (get-dashboard name) new-series))))
    (success)))

(defn drop-nth [n coll]
  (keep-indexed #(if (not= %1 n) %2) coll))

(defn delete-dashboard-series-by-index [ req ]
  (let [name (:name (:params req))
        index (try-parse-integer (:index (:params req)))]
    (log/info "Deleting series index" index "from dashboard " name)
    (when index
      (data/store-dashboard-definition
       name (json/write-str (drop-nth index (get-dashboard name)))))
    (success)))

(defn store-series-data [ store-samples req ]
  (log/debug "Incoming data, content-type:" (:content-type req))
  (let [ samples (if (= "application/transit+json" (:content-type req))
                   (read-transit (slurp (:body req)))
                   (edn/read-string (slurp (:body req))))]
    (store-samples samples))
  "Incoming data accepted.")

(defn all-routes [ store-samples ]
  (routes

   (GET "/data/:series-name" {params :params}
     (get-series-data params))

   (POST "/data" req
     (store-series-data store-samples req))

   (GET "/dashboard/:name" [ name ]
     (render-dashboard name))

   (POST "/dashboard/:name/add-series" req
     (add-dashboard-series req))

   (POST "/dashboard/:name/delete-by-index/:index" req
     (delete-dashboard-series-by-index req))

   (route/resources (str "/" (get-version)))

   (GET "/" [] (ring/redirect "/dashboard/default"))
   (route/not-found "Resource Not Found")))
