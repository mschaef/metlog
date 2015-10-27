(ns metlog-vault.core
  (:gen-class :main true)
  (:use metlog-common.core
        compojure.core
        [ring.middleware resource
                         not-modified
                         content-type
                         browser-caching])
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
     [:link {:href (str "/" (get-version) "/metlog.css")
             :rel "stylesheet"
             :type "text/css"}]
     [:title "Metlog - " (get-version)]]
    [:body
     [:div {:id "metlog"}]
     [:script {:src  (str "/" (get-version) "/metlog.js")
               :type "text/javascript"}]]]))

(defroutes all-routes
  (GET "/series-names" []
    (edn-response
     (data/get-series-names)))

  (GET "/data/:series-name" {params :params}
    (get-data-for-series-name (:series-name params)
                              (try-parse-integer (:query-window-secs params) 86400)))

  (POST "/data" req
    (data/store-data-samples (edn/read-string (slurp (:body req))))
    "Incoming data accepted.")
  
  (GET "/dashboard" [] (render-dashboard))
  
  (route/resources (str "/" (get-version)))
  
  (GET "/" [] (ring/redirect "/dashboard"))
  (route/not-found "Resource Not Found"))

(defn wrap-request-logging [ app ]
  (fn [req]
    (log/trace 'REQ (:request-method req) (:uri req) (:params req))
    (let [begin-t (. System (nanoTime))
          resp (app req)]
      (log/info 'RESP (:status resp) (:request-method req) (:uri req)
                "-" (/ (- (. System (nanoTime)) begin-t) 1000000.0))
      resp)))

(def handler (-> all-routes
                 (data/wrap-db-connection)
                 (wrap-content-type)
                 (wrap-browser-caching {"text/javascript" 360000
                                        "text/css" 360000})
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
  (log/info "Starting Vault -" (get-version))
  (start-webserver (config-property "http.port" 8080))
  (log/info "end run."))
