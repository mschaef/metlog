(ns metlog-agent.core
  (:gen-class)
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as jio]
            [overtone.at-at :as at-at]
            [clj-http.client :as http]
            [cognitect.transit :as transit]))

(defn pr-transit [ val ]
  (let [out (java.io.ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer val)
    (.toString out)))

(defn seconds [ seconds ] (* 1000 seconds))
(defn minutes [ minutes ] (seconds (* 60 minutes)))
(defn hours [ hours ] (minutes (* 60 hours)))
(defn days [ days ] (hours (* 24 days)))

(def vault-update-interval (minutes 1))
(def sensor-poll-interval (seconds 15))

(def my-pool (at-at/mk-pool))

(def sensor-result-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn enqueue-sensor-reading [ series-name val ]
  (let [ reading {:t (java.util.Date.)
                  :series_name series-name
                  :val val}]
    (log/trace "enquing sensor reading" reading)
    (.add sensor-result-queue reading)))

(def sensor-defs (atom {}))

(def vault-url (config-property "vault.url" "http://localhost:8080/data"))

(defn add-sensor-def [ sensor-name sensor-fn ]
  (swap! sensor-defs assoc sensor-name sensor-fn))

(def default-sensor-attrs {:poll-interval (minutes 1)})

(defmacro defsensor*
  ([ name attrs fn-form ]
   (let [attrs (merge default-sensor-attrs attrs)]
     `(add-sensor-def '~name (merge ~attrs {:sensor-fn ~fn-form}))))

  ([ name fn-form ]
   `(defsensor* ~name {} ~fn-form)))

(defmacro defsensor [ name & body ]
  (if (map? (first body))
    `(defsensor* ~name ~(first body) (fn [] ~@(rest body)))
    `(defsensor* ~name {}  (fn [] ~@body))))

(defn all-sensors []
  (let [ current-sensor-defs @sensor-defs ]
    (map (fn [ sensor-name ]
           (merge (get current-sensor-defs sensor-name) {:sensor-name sensor-name}))
         (keys current-sensor-defs)))  )

(defn poll-sensor [ sensor-def ]
  (try 
    ((:sensor-fn sensor-def))
    (catch Exception ex
      (log/error "Error polling sensor" (:sensor-name sensor-def) (str ex))
      false)))

(defn process-sensor-reading [ sensor-name sensor-value ]
  (log/trace "process-sensor-reading" [ sensor-name sensor-value ])
  (cond
    (or (nil? sensor-value) 
        (false? sensor-value))
    (log/debug "No value for sensor" sensor-name)
    
    (number? sensor-value)
    (enqueue-sensor-reading sensor-name sensor-value)
    
    (map? sensor-value)
    (doseq [[sub-name sub-value] sensor-value]
      (process-sensor-reading (str sensor-name "-" (name sub-name))
                              sub-value))
    
    :else
    (log/error "Bad sensor value" sensor-value "(" (.getClass sensor-value) ")"
               "from sensor" sensor-name)))


(defn poll-sensors [ sensor-defs ]
  (log/trace "poll-sensors" (map :sensor-name sensor-defs))
  (doseq [ sensor-def sensor-defs ]
    (process-sensor-reading (:sensor-name sensor-def) (poll-sensor sensor-def))))

(defn take-result-queue-snapshot []
  (let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
    (locking sensor-result-queue
      (.drainTo sensor-result-queue snapshot))
    (seq snapshot)))

(def update-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn clean-readings [ unclean ]
  (map #(assoc % :val (double (:val %))) unclean))

(defn update-vault []
  (locking update-queue
    (when-let [ snapshot (take-result-queue-snapshot) ]
      (.addAll update-queue snapshot))
    (unless (.isEmpty update-queue)
            (let [url vault-url
                  readings (clean-readings (seq update-queue))]
              (log/debug "Posting" (count readings) "reading(s) to" url)
              (let [post-response
                    (http/post url {:content-type "application/transit+json"
                                    :body (pr-transit readings)}) ]
                (when (= (:status post-response) 200)
                  (.clear update-queue)))))))

(defn maybe-load-config-file [ filename ]
  (binding [ *ns* (find-ns 'metlog-agent.core)]
    (when (.exists (jio/as-file filename))
      (log/info "Loading configuration file:" filename)
      (load-file filename))))

(defn -main
  "Agent entry point"
  [& args]
  (maybe-load-config-file (config-property "agent.configurationFile" "config.clj"))

  (doseq [ [ poll-interval sensors ] (group-by :poll-interval (all-sensors))]
    (log/debug "Scheduling poll job @" poll-interval "msec. for" (map :sensor-name sensors))
    (at-at/every poll-interval
                 (exception-barrier #(poll-sensors sensors) (str "poller-" poll-interval))
                 my-pool))

  (at-at/every vault-update-interval (exception-barrier update-vault "vault-update") my-pool)
  
  (log/info "running."))


