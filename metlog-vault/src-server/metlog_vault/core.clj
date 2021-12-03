 (ns metlog-vault.core
  (:gen-class :main true)
  (:use metlog-common.core
        compojure.core
        [ring.middleware resource
                         not-modified
                         content-type
                         browser-caching])
  (:require [clojure.tools.logging :as log]
            [ring.util.response :as ring]
            [compojure.route :as route]
            [overtone.at-at :as at-at]
            [cognitect.transit :as transit]
            [clojure.edn :as edn]
            [hiccup.core :as hiccup]
            [metlog-vault.data :as data]
            [metlog-vault.web :as web]))

(def my-pool (at-at/mk-pool))

(defn make-sample [ series-name value ]
  {:t (java.util.Date.)
   :series_name series-name
   :val value})

(defn queued-data-sink [ db-pool ]
  (let [ sample-queue (java.util.concurrent.LinkedBlockingQueue.) ]
    (at-at/every 60000
                 (exception-barrier
                  #(.add sample-queue (make-sample "vault-ingress-queue-size"
                                                   (.size sample-queue)))
                  "Record ingress queue size")
                 my-pool)
    (at-at/interspaced 15000
                       (exception-barrier
                        #(let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
                           (locking sample-queue
                             (.drainTo sample-queue snapshot))
                           (when (> (count snapshot) 0)
                             (log/info "Storing " (count snapshot) " samples.")
                             (data/with-db-connection db-pool
                               (data/store-data-samples (seq snapshot)))))
                        "Store ingress queue contents")
                       my-pool)
    (fn [ samples ]
      (log/info "Enqueuing " (count samples) " samples for later storage.")
      (doseq [ sample samples ]
        (.add sample-queue sample)))))

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
    [:head {:name "viewport"
           ;; user-scalable=no fails to work on iOS n where n > 10
           :content "width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0"}
     [:link {:href "/metlog.css"
             :rel "stylesheet"
             :type "text/css"}]
     [:title "Metlog - " (get-version)]]
    [:body
     [:div {:id "metlog"}]
     [:script {:src "/compiled/metlog.js"
               :type "text/javascript"}]]]))

(defn all-routes [ store-samples ]
  (routes
   (GET "/series-names" []
     (transit-response
      (data/get-series-names)))

   (GET "/data/:series-name" {params :params}
     (transit-response
      (data/get-data-for-series-name (:series-name params)
                                     (try-parse-long (:begin-t params))
                                     (try-parse-long (:end-t params)))))

   (POST "/data" req
     (log/debug "Incoming data, content-type:" (:content-type req))
     (let [ samples (if (= "application/transit+json" (:content-type req))
                                (read-transit (slurp (:body req)))
                                (edn/read-string (slurp (:body req))))]
       (store-samples samples))
     "Incoming data accepted.")

   (GET "/dashboard" [] (render-dashboard))

   (POST "/dashboard-defn/:name" req
     (let [name (:name (:params req))
           defn (slurp (:body req))]
       (log/info "Incoming dashboard definition: " name defn)
       (data/store-dashboard-definition name defn)))

   (GET "/dashboard-defn/:name" [ name ]
     (transit-response
      (edn/read-string
       (data/get-dashboard-definition name))))

   (route/resources "/")

   (GET "/" [] (ring/redirect "/dashboard"))
   (route/not-found "Resource Not Found")))

(defn -main
  []
  (log/info "Starting Vault -" (get-version))
  (let [db-pool (data/open-db-pool)
        data-sink (queued-data-sink db-pool)]
    (web/start-webserver (config-property "http.port" 8080) db-pool (all-routes data-sink)))
  (log/info "end run."))
