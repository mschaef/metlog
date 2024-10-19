(ns metlog-vault.dashboard
  (:use compojure.core
        playbook.core
        playbook.web
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [hiccup.page :as hiccup-page]
            [hiccup.form :as hiccup-form]
            [ring.util.response :as ring]
            [clojure.data.json :as json]
            [playbook.hashid :as hashid]
            [playbook.config :as config]
            [metlog-vault.data :as data]
            [metlog-vault.data-service :as data-service]))

(defn- resource [ path ]
  (str "/" (get-version) "/" path))

(defn- post-button [ attrs body ]
  (let [{:keys [ target next-url ]} attrs]
    (hiccup-form/submit-button
     (cond-> {:onclick (if-let [next-url (:next-url attrs)]
                         (str "window._metlog.doPost('" target "'," (json/write-str (get attrs :args {})) ", '" next-url "')")
                         (str "window._metlog.doPost('" target "'," (json/write-str (get attrs :args {})) ")"))}
       (:class attrs) (merge {:class (:class attrs)})
       (:shortcut-key attrs) (merge {:data-shortcut-key (:shortcut-key attrs)
                                     :data-target target}))
     body)))

(defn- render-standard-header [ attrs ]
  (let [title (:title attrs)
        client-redirect-time (:client-redirect-time attrs)
        client-redirect (:client-redirect attrs)]
    [:head
     [:meta
      {:name "viewport"
       ;; user-scalable=no fails to work on iOS n where n > 10
       :content "width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0"}]
     (when (or client-redirect-time
               client-redirect)
       [:meta {:http-equiv "refresh"
               :content (str (or client-redirect-time 2)
                             (if client-redirect
                               (str "; url=" client-redirect)))}])
     (hiccup-page/include-css (resource "metlog.css"))
     [:script {:type "module" :src (resource "metlog.js")}]
     [:title
      (when (config/cval :development-mode) "DEV - ")
      (config/cval :app :name)
      (when title (str " - " title))]]))

(defn- render-page [ attrs & contents ]
  (hiccup-page/html5
   [:html
    (render-standard-header attrs)
    [:body (when-let [ body-class (:body-class attrs)] 
             {:class body-class})
     contents]]))

(defn dashboard-select [ dashboard-id ]
  [:select {:id "select-dashboard" :name "select-dashboard"
            :onchange "window._metlog.onDashboardSelectChange(event)"}
   (hiccup-form/select-options
    (map (fn [ dashboard-info ]
           [ (:name dashboard-info) (hashid/encode :db (:dashboard_id dashboard-info))])
         (data/get-dashboard-names))
    (hashid/encode :db dashboard-id))])

(defn- series-select [ id ]
  [:select {:id id
            :name id}
   (hiccup-form/select-options
    (map (fn [ series-name ]
           [ series-name series-name ])
         (data/get-series-names)))])

(defn- header [ dashboard-id query-window ]
  [:div.header
   [:span#app-name "Metlog"]

   [:div#add-series.header-element
    (dashboard-select dashboard-id)
    (hiccup-form/submit-button {:onclick "window._metlog.addDashboard();"} "Add Dashboard")
    (post-button {:target (str "/dashboard/" (hashid/encode :db dashboard-id) "/delete")}
                 "Delete Dashboard")]

   [:a {:href "/dashboard/healthchecks"} "Agent Status"]

   [:div.header-element
    (hiccup-form/text-field { :id "query-window" :maxlength "8" }
                            "query-window" (or query-window "1d"))]])

(defn- dashboard-link [ id & others ]
  (apply str "/dashboard/" (hashid/encode :db id) others))

(defn- add-series-pane [ id ]
  [:div.series-pane
   [:div.series-pane-header
    [:span.series-name "Add Series"]]
   [:div.add-series-body
    (hiccup-form/form-to
     {:class "add-series-form"} [:post (dashboard-link id)]
     [:div
      [:label {:for "series-name"} "Series Name: "]
      (series-select "series-name")]

     [:div
      [:label {:for "force-zero"} "Force Zero:"]
      (hiccup-form/check-box "force-zero" false "Y")]

     [:div
      [:label {:for "base-2-y-axis"} "Base 2 Y-Axis:"]
      (hiccup-form/check-box "base-2-y-axis" false "Y")]

     [:div
      [:label {:for "draw-points"} "Draw Points:"]
      (hiccup-form/check-box "draw-points" false "Y")]

     [:div.add-series-row
      (hiccup-form/submit-button {:class "add-series-button"
                                  :onclick (str "window._metlog.onAddSeries(event)")}
                                 "Add Series")])]])


(defn- normalize-series-defn [ series-defn ]
  (if (string? series-defn)
    {:series-name series-defn
     :force-zero false
     :base-2-y-axis false
     :draw-points false}
    series-defn))

(defn- series-pane [ dashboard-id index series-defn ]
  (let [series-defn (normalize-series-defn series-defn)
        { :keys [ series-name force-zero ] } series-defn]
    [:div.series-pane
     [:div.series-pane-header
      [:span.series-name series-name
       (when force-zero
         [:span.pill "zero"])]
      (hiccup-form/submit-button {:class "close-button"
                                  :onclick (str "window._metlog.onDeleteSeries(" index ");")}
                                 "Close")]
     [:div.tsplot-container
      [:canvas.series-plot {:data-series-defn (json/write-str series-defn)}]]]))

(defn- get-dashboard [ id ]
  (if-let [ dashboard (data/get-dashboard-by-id id)]
    (if-let [ parsed (or (try-parse-json (:definition dashboard))
                         [])]
      (assoc dashboard :definition parsed))))

(defn- render-duration [ msec ]
  (let [ s (quot msec 1000) ]
    (if (< s 60)
      (str s "s")
      (let [ m (quot s 60) ]
        (if (< m 60)
          (str m "m")
          (let [ h (quot m 60) ]
            (if (< h 48)
              (str h "h")
              (str (quot h 24) "d"))))))))

(defn- time-diff [ t1 t2 ]
  (- (.getTime t1)
     (.getTime t2)))

(defn- render-healthcheck-display [ healthchecks ]
  (let [ t (current-time) ]
    (render-page
     {:client-redirect-time 5
      :title "Agent Status"
      :body-class "healthcheck"}
     [:h1 "Agent Status"]
     [:p "Current Time: " t]
     [:table.agent-healthcheck
      [:tr
       [:th "Name"]
       [:th "IP Address"]
       [:th "Time Since Start"]
       [:th "Time Since Sample"]
       [:th "Healthcheck Interval"]
       [:th "Pending Readings"]
       [:th "Sensor Polls"]
       [:th "Sensor Poll Errors"]
       [:th "Vault Posts"]
       [:th "Vault Post Errors"]]
      (map (fn [ k ]
             (let [ hc (get healthchecks k)]
               [:tr
                [:td (:name hc)]
                [:td (:local-ip-address hc)]
                [:td
                 (render-duration (time-diff t (:start-time hc)))]
                [:td
                 (render-duration (time-diff t (:current-time hc)))]
                [:td (:healthcheck-interval hc)]
                [:td (:pending-readings hc)]
                [:td (:sensor-polls hc)]
                [:td (:sensor-errors hc)]
                [:td (:vault-posts hc)]
                [:td (:vault-errors hc)]]))
           (keys healthchecks))]
     [:a {:href "/"} "Dashboard"])))

(defn- render-dashboard [ id req ]
  (when-let [ dashboard (get-dashboard id)]
    (let [ definition (:definition dashboard)]
      (render-page { :title (:name dashboard) }
       [:script "var dashboard = " (json/write-str (:definition dashboard)) ";"]
       [:div.dashboard
        (header id (:query-window (:params req)))
        [:div.series-list
         (map-indexed (partial series-pane (:dashboard_id dashboard)) definition)
         (add-series-pane id)]]))))

(defn- update-dashboard [ dashboard-id req ]
  (let [ new-definition (or (try-parse-json (:new-definition (:params req)))
                            []) ]
    (data/update-dashboard-definition dashboard-id new-definition)
    (success)))

(defn- ensure-dashboard-id-by-name [ dashboard-name ]
  (or
   (:dashboard_id
    (data/get-dashboard-by-name dashboard-name))
     (data/insert-dashboard-definition dashboard-name [])))

(defn- redirect-to-dashboard [ id ]
  (ring/redirect (dashboard-link id)))

(defn redirect-to-default-dashboard []
  (redirect-to-dashboard
   (if-let [ existing-dashboard (first (data/get-dashboard-names)) ]
     (:dashboard_id existing-dashboard)
     (ensure-dashboard-id-by-name "Dashboard"))))

(defn- create-dashboard [ req ]
  (let [dashboard-name (.trim (:dashboard-name (:params req)))]
    (when (> (count dashboard-name) 0)
      (redirect-to-dashboard (ensure-dashboard-id-by-name dashboard-name )))))

(defn- delete-dashboard [ dashboard-id ]
  (data/delete-dashboard-by-id dashboard-id)
  (redirect-to-default-dashboard))

(defn dashboard-routes [ dashboard-id ]
  (when-let-route [ dashboard-id (hashid/decode :db dashboard-id) ]
    (GET "/" req
      (render-dashboard dashboard-id req))

    (POST "/" req
      (update-dashboard dashboard-id req))

    (POST "/delete" [ ]
      (delete-dashboard dashboard-id))))

(defn all-routes [ healthchecks ]
  (routes
   (context "/:dashboard-id" [ dashboard-id ]
     (dashboard-routes dashboard-id))

   (GET "/healthchecks" []
     (render-healthcheck-display @healthchecks))

   (GET "/data/:series-name" {params :params}
     (data-service/get-series-data params))

   (POST "/" req
     (create-dashboard req))))
