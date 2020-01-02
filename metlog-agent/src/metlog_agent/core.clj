(ns metlog-agent.core
  (:gen-class)
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as jio]
            [overtone.at-at :as at-at]
            [clj-http.client :as http]
            [cognitect.transit :as transit]
            [clj-time.format :as time-format]
            [clj-time.coerce :as time-coerce]))

(def update-size-limit 200)

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

(defrecord TimestampedValue [ t val ])

(defn ensure-timestamped [ val ]
  (if (instance? TimestampedValue val)
    val
    (TimestampedValue. (java.util.Date.) val)))

(defn enqueue-sensor-reading [ t series-name val ]
  (let [ reading {:t t
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
         (keys current-sensor-defs))))

(defn poll-sensor [ sensor-def ]
  (try 
    ((:sensor-fn sensor-def))
    (catch Exception ex
      (log/error (str "Error polling sensor: " (:sensor-name sensor-def)
                      " (" (.getMessage ex) ")"))
      false)))

(defn process-sensor-reading [ sensor-name ts-sensor-reading ]
  (log/trace "process-sensor-reading" [ sensor-name ts-sensor-reading ])
  (let [t (:t ts-sensor-reading)]
    (loop [sensor-name sensor-name
           val (:val ts-sensor-reading)] 
      (cond
        (or (nil? val) (false? val))
        (log/debug "No value for sensor" sensor-name)
        
        (number? val)
        (enqueue-sensor-reading t sensor-name val)
    
        (map? val)
        (doseq [[sub-name sub-value] val]
          (process-sensor-reading (str sensor-name "-" (name sub-name)) (TimestampedValue. t sub-value)))
        
        :else
        (log/error "Bad sensor value" val "(" (.getClass val) ")" "from sensor" sensor-name)))))

(defn ->timestamped-readings [ obj ]
  (log/trace "->timestamped-readings" obj)
  (map ensure-timestamped (if (vector? obj) obj [ obj ])))

(defn poll-sensors [ sensor-defs ]
  (log/trace "poll-sensors" (map :sensor-name sensor-defs))
  (doseq [ sensor-def sensor-defs ]
    (doseq [ ts-sensor-reading (->timestamped-readings (poll-sensor sensor-def)) ]
      (process-sensor-reading (:sensor-name sensor-def) ts-sensor-reading))))

(defn take-result-queue-snapshot [ snapshot-size-limit ]
  (let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
    (locking sensor-result-queue
      (log/info "Taking snapshot queue with n:" (.size sensor-result-queue) ", limit:" snapshot-size-limit)
      (.drainTo sensor-result-queue snapshot snapshot-size-limit ))
    (seq snapshot)))

(def update-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn clean-readings [ unclean ]
  (map #(assoc % :val (double (:val %))) unclean))

(defn post-readings [ url readings ]
  (log/debug "Posting" (count readings) "reading(s) to" url)
  (let [post-response
        (try
          (http/post url {:content-type "application/transit+json"
                          :body (pr-transit readings)})
          (catch java.net.ConnectException ex
            ;; Pretend connection errors are HTTP errors
            (log/error (str "Error posting readings to " url " (" (.getMessage ex) ")"))
            {:status 400})) ]
    (log/debug "Post readings response:" post-response)
    (= (:status post-response) 200)))

(defn snapshot-to-update-queue []
  (locking update-queue
    (let [snapshot (take-result-queue-snapshot
                    (min update-size-limit
                         (- update-size-limit (.size update-queue)))) ]
      (when snapshot
        (.addAll update-queue snapshot))
      snapshot)))

(defn post-update []
  (locking update-queue
    (or (.isEmpty update-queue)
        (and (post-readings vault-url (clean-readings (seq update-queue)))
             (do
               (.clear update-queue)
               true)))))

(defn update-vault []
  (loop []
    (let [snapshot (snapshot-to-update-queue)]
      ;; TODO: If the update queue was not extended in this cycle (ie:
      ;; the queue is already full, this will not loop around to try
      ;; to check for additional samples that might be still in the
      ;; result queue. This introduces an update-vault period's worth
      ;; of additional latency in recovery scenarios, which would ideally
      ;; be eliminated.
      (when (and (post-update)
                 (> (count snapshot) 0))
        (recur)))))

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


