(ns metlog-agent.core
  (:gen-class)
  (:use metlog-common.core)
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as jio]
            [overtone.at-at :as at-at]
            [clj-http.client :as http]))

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
    (log/debug "enquing sensor reading" reading)
    (.add sensor-result-queue reading)))

(def sensor-defs (atom {}))

(defn add-sensor-def [ sensor-name sensor-fn ]
  (swap! sensor-defs assoc sensor-name sensor-fn))

(def default-sensor-attrs {:poll-interval (minutes 1)})

(defmacro defsensor [ name & body ]
  (let [attrs (merge default-sensor-attrs
                     (if (map? (first body)) (first body) {}))
        body (if (map? (first body)) (next body) body)]
    `(add-sensor-def '~name (merge ~attrs {:sensor-fn (fn [] ~@body)}))))

(defn read-w1-sensor-at-path [ sensor-path ]
  (with-open [rdr (jio/reader sensor-path)]
    (let [ line (second (line-seq rdr)) ]
      (and (not (nil? line))
           (/ (Double/valueOf (.substring line 29)) 1000.0)))))

(if-let [ sensor-path (config-property "sensor.path") ]
  (defsensor "basement-temp"
    (read-w1-sensor-at-path sensor-path)))

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
  (cond
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
  (log/debug "poll-sensors" (map :sensor-name sensor-defs))
  (doseq [ sensor-def sensor-defs ]
    (if-let [ sensor-value (poll-sensor sensor-def)]
      (process-sensor-reading (:sensor-name sensor-def) sensor-value))))

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
            (let [url (config-property "vault.url" "http://localhost:8080/data")
                  readings (clean-readings (seq update-queue))
                  data { :body (pr-str readings)}]
              (log/info "Posting" (count readings) "reading(s) to" url)
              (let [ post-response (http/post url data) ]
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
  (maybe-load-config-file "config.clj")

  (doseq [ [ poll-interval sensors ] (group-by :poll-interval (all-sensors))]
    (log/info "Scheduling poll job @" poll-interval "msec. for" (map :sensor-name sensors))
    (at-at/every poll-interval
                 (exception-barrier #(poll-sensors sensors) (str "poller-" poll-interval))
                 my-pool))

  (at-at/every vault-update-interval (exception-barrier update-vault "vault-update") my-pool)
  
  (log/info "running."))


