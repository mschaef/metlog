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

(defn- ensure-sample-sequence [ samples ]
  (if (or (vector? samples)
          (seq? samples))
    samples
    [samples]))

(defn store-series-data [ store-samples req ]
  (log/debug "Incoming data, content-type:" (:content-type req))
  (try
    (let [samples (ensure-sample-sequence (read-request-body req))]
      (store-samples samples)
      (respond-success "Incoming data accepted." {:n (count samples)}))
    (catch Exception ex
      (log/error "Error accepting inbound data" ex)
      (respond-bad-request (str "Error accepting inbound data: " (.getMessage ex))))))

(defn all-routes [ store-samples ]
  (routes
   (POST "/data" req
     (store-series-data store-samples req))))
