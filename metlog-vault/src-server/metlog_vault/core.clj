(ns metlog-vault.core
  (:gen-class :main true)
  (:use metlog-common.core
        compojure.core
        [ring.middleware resource
                         not-modified
                         content-type])
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as ring]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.edn :as edn]
            [metlog-vault.data :as data]
            [hiccup.core :as hiccup]))

(defmacro get-version []
  ;; Capture compile-time property definition from Lein
  (System/getProperty "metlog-vault.version"))

(defn edn-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn get-data-for-series-name [ series-name window-size-secs ]
  (edn-response
   (merge {:data (data/get-data-for-series-name series-name window-size-secs)}
          (data/get-time-range window-size-secs))))

(defn render-dashboard []
  (hiccup/html
   [:html
    [:head
     [:link { :href "/metlog.css" :rel "stylesheet" :type "text/css"}]
     [:title "Metlog - " (get-version)]]
    [:body
     [:div {:id "metlog"}]
     [:script {:src (str "metlog-" (get-version) ".js")
               :type "text/javascript"}]]]))

(defroutes all-routes
  (GET "/series-names" []
    (edn-response
     (data/get-series-names)))

  (GET "/data/:series-name" {{series-name :series-name
                              query-window-secs :query-window-secs}
                             :params}
    (get-data-for-series-name series-name (or (parsable-integer? query-window-secs) 86400)))

  (POST "/data" req
    (data/store-data-samples (edn/read-string (slurp (:body req))))
    "Incoming data accepted.")
  
  (GET "/" [] (ring/redirect "/dashboard"))
  (GET "/dashboard" [] (render-dashboard))
  (route/not-found "Resource Not Found"))

(defn wrap-request-logging [ app ]
  (fn [req]
    (log/trace 'REQ (:request-method req) (:uri req) (:params req))
    (let [begin-t (. System (nanoTime))
          resp (app req)]
      (log/info 'RESP (:status resp) (:uri req)
                "-" (/ (- (. System (nanoTime)) begin-t) 1000000.0))
      resp)))

(def handler (-> all-routes
                 (data/wrap-db-connection)
                 (wrap-resource "public")
                 (wrap-content-type)
                 (wrap-not-modified)
                 (handler/site)
                 (wrap-request-logging)))

(defn start-webserver [ http-port ]
  (log/info "Starting Vault Webserver on port" http-port)
  (let [server (jetty/run-jetty handler  { :port http-port :join? false })]
    (add-shutdown-hook
     (fn []
       (log/info "Shutting down webserver")
       (.stop server)))
    (.join server)))

(defn -main
  []
  (log/info "Starting Vault")
  (start-webserver (config-property "http.port" 8080))
  (log/info "end run."))
