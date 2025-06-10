(ns metlog.vault.data-service
  (:use compojure.core
        playbook.core
        metlog.util
        metlog.vault.util)
  (:require [taoensso.timbre :as log]
            [compojure.route :as route]
            [ring.util.response :as ring]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [metlog.vault.data :as data]
            [metlog.vault.healthcheck-service :as healthcheck-service]))

(defn make-sample [series-name value]
  {:t (java.util.Date.)
   :series_name series-name
   :val value})

(defn get-series-data [params]
  {:body
   (vec
    (data/get-data-for-series-name (:series-name params)
                                   (try-parse-long (:begin-t params))
                                   (try-parse-long (:end-t params))))})

(def series-name-re #"^([a-zA-Z0-9]+-?)+$")

(defn- validate-series-name [series-name]
  (when (not (re-find series-name-re (str series-name)))
    (throw (Exception. (str "Invalid data series name: " series-name)))))

(defn- read-request-body-data [req]
  (let [body (read-request-body req)]
    (if (map? body)
      body
      {:samples body})))

(defn- validate-samples [samples]
  (map (fn [sample]
         (validate-series-name (:series_name sample))
         (when (nil? (:val sample))
           (throw (Exception. (str "No data value in sample:" sample))))
         sample)
       samples))

(defn store-series-data [store-samples healthchecks req]
  (try
    (let [{samples :samples
           healthcheck-data :healthcheck} (read-request-body-data req)]
      (when healthcheck-data
        (healthcheck-service/notice-healthcheck healthcheck-data healthchecks))
      (store-samples (validate-samples samples))
      (respond-success "Incoming data accepted." {:n (count samples)}))
    (catch Exception ex
      (log/error "Error accepting inbound data" ex)
      (respond-bad-request (str "Error accepting inbound data: " (.getMessage ex))))))

(defn store-sample [store-samples req]
  (let [series-name (:series-name (:params req))]
    (log/debug "Incoming sample for " series-name ", content-type:" (:content-type req))
    (try
      (store-samples (validate-samples [(make-sample series-name (read-request-body req))]))
      (respond-success "Incoming sample accepted.")
      (catch Exception ex
        (log/error "Error accepting inbound data" ex)
        (respond-bad-request (str "Error accepting inbound data: " (.getMessage ex)))))))

(defn all-routes [store-samples healthchecks]
  (routes
   (POST "/data" req
     (store-series-data store-samples healthchecks req))

   (POST "/sample/:series-name" req
     (store-sample store-samples req))))
