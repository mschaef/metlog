(ns metlog-vault.data-service
  (:use compojure.core
        playbook.core
        metlog-vault.util)
  (:require [taoensso.timbre :as log]
            [compojure.route :as route]
            [ring.util.response :as ring]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [metlog-vault.data :as data]))

(defn make-sample [ series-name value ]
  {:t (java.util.Date.)
   :series_name series-name
   :val value})

(defn get-series-data [ params ]
  {:body
   (vec
    (data/get-data-for-series-name (:series-name params)
                                   (try-parse-long (:begin-t params))
                                   (try-parse-long (:end-t params))))})

(defn- respond-success
  ([ message details ]
   (ring/response
    (merge details {:message message :success true})))

  ([ message ]
   (respond-success message {})))

(defn- respond-bad-request
  ([ message details ]
   (ring/bad-request
    (merge details {:message message :success false})))

  ([ message ]
   (respond-bad-request message {})))

(defn- read-request-body [ req ]
  (let [req-body (slurp (:body req)) ]
    (case (:content-type req)
      "application/json" (json/read-str req-body :key-fn keyword)
      "application/transit+json" (read-transit req-body)
      (edn/read-string req-body))))

(def series-name-re #"^([a-zA-Z0-9]+-?)+$")

(defn- validate-series-name [ series-name ]
  (when (not (re-find series-name-re (str series-name)))
    (throw (Exception. (str "Invalid data series name: " series-name)))))


(defn- normalize-samples [ samples ]
  (map (fn [ sample ]
         (validate-series-name (:series_name sample))
         sample)
       samples))

(defn store-series-data [ store-samples req ]
  (log/debug "Incoming data, content-type:" (:content-type req))
  (try
    (let [samples (read-request-body req)]
      (store-samples (normalize-samples samples))
      (respond-success "Incoming data accepted." {:n (count samples)}))
    (catch Exception ex
      (log/error "Error accepting inbound data" ex)
      (respond-bad-request (str "Error accepting inbound data: " (.getMessage ex))))))

(defn store-sample [ store-samples req ]
  (let [ series-name (:series-name (:params req)) ]
    (log/debug "Incoming sample for " series-name ", content-type:" (:content-type req))
    (try
      (store-samples (normalize-samples [ (make-sample series-name (read-request-body req)) ]))
      (respond-success "Incoming sample accepted.")
      (catch Exception ex
        (log/error "Error accepting inbound data" ex)
        (respond-bad-request (str "Error accepting inbound data: " (.getMessage ex)))))))

(defn all-routes [ store-samples ]
  (routes
   (POST "/data" req
     (store-series-data store-samples req))

   (POST "/sample/:series-name" req
     (store-sample store-samples req))))
