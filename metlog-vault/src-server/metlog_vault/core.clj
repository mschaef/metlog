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
            [cognitect.transit :as transit]
            [clojure.edn :as edn]
            [metlog-vault.data :as data]
            [hiccup.core :as hiccup]))

(def http-thread-count 4)

(defmacro get-version []
  ;; Capture compile-time property definition from Lein
  (or (System/getProperty "metlog-vault.version")
      "dev"))

(defn pr-transit [ val ]
  (let [out (java.io.ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer val)
    (.toString out)))

(defn read-transit [ string ]
  (let [in (java.io.ByteArrayInputStream. (.getBytes string))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn transit-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/transit+json"}
   :body (pr-transit data)})

(defn render-dashboard []
  (hiccup/html
   [:html
    [:head
     [:link {:href "/metlog.css"
             :rel "stylesheet"
             :type "text/css"}]
     [:title "Metlog - " (get-version)]]
    [:body
     [:div {:id "metlog"}]
     [:script {:src "/compiled/metlog.js"
               :type "text/javascript"}]]]))

(defroutes all-routes
  (GET "/series-names" []
    (transit-response
     (data/get-series-names)))

  (GET "/data/:series-name" {params :params}
    (transit-response
     (data/get-data-for-series-name (:series-name params)
                                    (try-parse-long (:begin-t params))
                                    (try-parse-long (:end-t params)))))

  (POST "/data" req
    (log/error (:content-type req))
    (data/store-data-samples
     (if (= "application/transit+json" (:content-type req))       
       (read-transit (slurp (:body req)))
       (edn/read-string (slurp (:body req)))))
    
    "Incoming data accepted.")
  
  (GET "/dashboard" [] (render-dashboard))
  
  (route/resources "/")
  
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

(defn wrap-request-thread-naming [ app ]
  (fn [ req ]
    (let [thread (Thread/currentThread)
          initial-thread-name (.getName thread)]
      (try
        (.setName thread (str initial-thread-name " " (:request-method req) " " (:uri req)))
        (app req)
        (finally
          (.setName thread initial-thread-name))))))

(def handler (-> all-routes
                 (data/wrap-db-connection)
                 (wrap-content-type)
                 (wrap-browser-caching {"text/javascript" 360000
                                        "text/css" 360000})
                 (wrap-not-modified)
                 (handler/site)
                 (wrap-request-logging)
                 (wrap-request-thread-naming)))

(defn start-webserver [ http-port ]
  (log/info "Starting Vault Webserver on port" http-port
            (str "(HTTP threads=" http-thread-count ")"))
  (let [server (jetty/run-jetty handler {:port http-port
                                         :join? false
                                         :min-threads http-thread-count
                                         :max-threads http-thread-count})]
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
