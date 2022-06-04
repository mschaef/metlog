(ns metlog-vault.routes
  (:use compojure.core
        metlog-common.core
        metlog-vault.util)
  (:require [clojure.tools.logging :as log]
            [compojure.route :as route]
            [hiccup.core :as hiccup]
            [ring.util.response :as ring]
            [clojure.edn :as edn]
            [metlog-vault.data :as data]))

(defn render-dashboard []
  (hiccup/html
   [:html
    [:head {:name "viewport"
           ;; user-scalable=no fails to work on iOS n where n > 10
           :content "width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0"}
     [:link {:href "/metlog.css"
             :rel "stylesheet"
             :type "text/css"}]
     [:title "Metlog - " (get-version)]]
    [:body
     [:div {:id "metlog"}]
     [:script {:src "/cljs-out/dev-main.js"
               :type "text/javascript"}]]]))

(defn get-series-names []
  (transit-response
   (data/get-series-names)))

(defn get-series-data [ params ]
  (transit-response
   (data/get-data-for-series-name (:series-name params)
                                  (try-parse-long (:begin-t params))
                                  (try-parse-long (:end-t params)))))

(defn store-dashboard [ req ]
  (let [name (:name (:params req))
        defn (slurp (:body req))]
    (log/info "Incoming dashboard definition: " name defn)
    (data/store-dashboard-definition name defn)))

(defn get-dashboard [ name ]
  (transit-response
   (edn/read-string
    (data/get-dashboard-definition name))))

(defn store-series-data [ store-samples req ]
  (log/debug "Incoming data, content-type:" (:content-type req))
  (let [ samples (if (= "application/transit+json" (:content-type req))
                   (read-transit (slurp (:body req)))
                   (edn/read-string (slurp (:body req))))]
    (store-samples samples))
  "Incoming data accepted.")

(defn all-routes [ store-samples ]
  (routes
   (GET "/series-names" []
     (get-series-names))

   (GET "/data/:series-name" {params :params}
     (get-series-data params))

   (POST "/data" req
     (store-series-data store-samples req))

   (GET "/dashboard" []
     (render-dashboard))

   (POST "/dashboard-defn/:name" req
     (store-dashboard req))

   (GET "/dashboard-defn/:name" [ name ]
     (get-dashboard name))

   (route/resources "/")

   (GET "/" [] (ring/redirect "/dashboard"))
   (route/not-found "Resource Not Found")))
