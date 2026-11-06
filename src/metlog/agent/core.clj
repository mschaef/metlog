(ns metlog.agent.core
  (:use playbook.core
        metlog.util)
  (:require [taoensso.timbre :as log]
            [clojure.java.io :as jio]
            [overtone.at-at :as at-at]
            [clj-http.client :as http]
            [cognitect.transit :as transit]
            [clj-time.format :as time-format]
            [clj-time.coerce :as time-coerce]
            [playbook.config :as config]
            [playbook.service :as service]))

(defn- get-local-ip-address []
  (try
    (-> (->> (java.net.NetworkInterface/getNetworkInterfaces)
             enumeration-seq
             (map bean)
             (mapcat :interfaceAddresses)
             (map bean)
             (filter :broadcast)
             (filter #(= (.getClass (:address %)) java.net.Inet4Address)))
        (nth 0)
        (get :address)
        .getHostAddress)
    (catch Exception ex
      (log/error (str "Error determining local IP address (" (.getMessage ex) ")"))
      "unknown")))

(defn seconds [seconds] (* 1000 seconds))
(defn minutes [minutes] (seconds (* 60 minutes)))
(defn hours [hours] (minutes (* 60 hours)))
(defn days [days] (hours (* 24 days)))

(def my-pool (at-at/mk-pool))

(def count-vault-post (atom 0))
(def count-vault-post-error (atom 0))

(def count-sensor-poll (atom 0))
(def count-sensor-poll-error (atom 0))
(def count-sensor-poll-timeout (atom 0))

(defn- count-inc! [count]
  (swap! count inc))

(def sensor-result-queue (java.util.concurrent.LinkedBlockingQueue.))

(defrecord TimestampedValue [t val])

(defn timestamped-value [t val]
  (TimestampedValue. t val))

(defn ensure-timestamped [val]
  (if (instance? TimestampedValue val)
    val
    (TimestampedValue. (java.util.Date.) val)))

(defn enqueue-sensor-reading [t series-name val]
  (let [reading {:t t
                 :series_name series-name
                 :val val}]
    (log/trace "enquing sensor reading" reading)
    (.add sensor-result-queue reading)))

(def sensor-defs (atom {}))

(defn add-sensor-def [sensor-name sensor-fn]
  (swap! sensor-defs assoc sensor-name sensor-fn))

(def default-sensor-attrs {:poll-interval (minutes 1)})

(defmacro defsensor*
  ([name attrs fn-form]
   (let [attrs (merge default-sensor-attrs attrs)]
     `(add-sensor-def '~name (merge ~attrs {:sensor-fn ~fn-form}))))

  ([name fn-form]
   `(defsensor* ~name {} ~fn-form)))

(defmacro defsensor [name & body]
  (if (map? (first body))
    `(defsensor* ~name ~(first body) (fn [] ~@(rest body)))
    `(defsensor* ~name {}  (fn [] ~@body))))

(defn all-sensors []
  (let [current-sensor-defs @sensor-defs]
    (map (fn [sensor-name]
           (merge (get current-sensor-defs sensor-name) {:sensor-name sensor-name}))
         (keys current-sensor-defs))))

(defn- call-with-timeout
  "Call f, waiting at most timeout-msec for it to return. A nil timeout
  calls f directly, on the current thread. On timeout, the worker thread
  is interrupted and a TimeoutException is thrown. Note that blocking
  socket I/O generally ignores interrupts, so a sensor stuck in a read
  may keep its worker thread until the read returns. The poller itself
  is released either way."
  [f timeout-msec]
  (if (nil? timeout-msec)
    (f)
    (let [result (future (f))
          val (try
                (deref result timeout-msec ::timeout)
                (catch java.util.concurrent.ExecutionException ex
                  (throw (or (.getCause ex) ex))))]
      (when (= val ::timeout)
        (future-cancel result)
        (throw (java.util.concurrent.TimeoutException.
                (str "Timed out after " timeout-msec " msec."))))
      val)))

(defn poll-sensor [sensor-def]
  (try
    (count-inc! count-sensor-poll)
    (call-with-timeout (:sensor-fn sensor-def) (:poll-timeout sensor-def))
    (catch java.util.concurrent.TimeoutException ex
      (count-inc! count-sensor-poll-error)
      (count-inc! count-sensor-poll-timeout)
      (log/error (str "Timeout polling sensor: " (:sensor-name sensor-def)
                      " (" (.getMessage ex) ")"))
      false)
    (catch Exception ex
      (count-inc! count-sensor-poll-error)
      (log/error (str "Error polling sensor: " (:sensor-name sensor-def)
                      " (" (.getMessage ex) ")"))
      false)))

(defn process-sensor-reading [sensor-name ts-sensor-reading]
  (log/trace "process-sensor-reading" [sensor-name ts-sensor-reading])
  (let [t (:t ts-sensor-reading)]
    (loop [sensor-name sensor-name
           val (:val ts-sensor-reading)]
      (cond
        (or (nil? val) (false? val))
        (log/debug "No value for sensor" sensor-name)

        (number? val)
        (enqueue-sensor-reading t sensor-name val)

        (map? val)
        (do
          (doseq [[sub-name sub-value] val]
            (process-sensor-reading (str sensor-name "-" (name sub-name)) (TimestampedValue. t sub-value))))

        :else
        (log/error "Bad sensor value" val "(" (.getClass val) ")" "from sensor" sensor-name)))))

(defn ->timestamped-readings [obj]
  (map ensure-timestamped (if (vector? obj) obj [obj])))

(defn poll-sensors [sensor-defs]
  (log/trace "poll-sensors" (map :sensor-name sensor-defs))
  (doseq [sensor-def sensor-defs]
    (doseq [ts-sensor-reading (->timestamped-readings (poll-sensor sensor-def))]
      (process-sensor-reading (:sensor-name sensor-def) ts-sensor-reading))))

;;; Readings stay at the head of sensor-result-queue until the vault
;;; has accepted them. A batch is peeked (copied, not removed), posted,
;;; and only removed after a successful post. If a post fails, the
;;; batch is still at the head of the queue, ahead of anything enqueued
;;; since, so the next attempt sends the same readings in the same
;;; order. This relies on there being a single consumer of the queue:
;;; agent-vault-update is the only code that removes readings, and
;;; playbook's schedule-job never runs a job reentrantly.

(defn- peek-result-queue-batch [batch-size-limit]
  (locking sensor-result-queue
    (log/trace "Peeking result queue with n:" (.size sensor-result-queue)
               ", limit:" batch-size-limit)
    (vec (take batch-size-limit (iterator-seq (.iterator sensor-result-queue))))))

(defn- remove-result-queue-batch [batch]
  (locking sensor-result-queue
    (dotimes [_ (count batch)]
      (.poll sensor-result-queue))))

(defn clean-readings [unclean]
  (map #(assoc % :val (double (:val %))) unclean))

(defn post-to-vault
  "Post data to the vault. Returns :ok if the vault accepted it,
  :rejected if the vault explicitly refused it (HTTP 400, which is how
  the vault reports invalid inbound data), and :failed for anything
  else (connection errors, timeouts, other HTTP statuses), which
  should be retried later."
  [data]
  (let [url (str (config/cval :agent :vault-url) "/agent/data")]
    (log/debug "Posting to vault at:" url)
    (count-inc! count-vault-post)
    (let [begin-t (System/currentTimeMillis)
          post-response
          (try
            (http/post url {:content-type "application/transit+json"
                            :body (pr-transit data)
                            :throw-exceptions false
                            :connection-timeout (seconds (config/cval :agent :vault-connection-timeout-sec))
                            :socket-timeout (seconds (config/cval :agent :vault-socket-timeout-sec))})
            (catch Exception ex
              (log/error (str "Error posting to vault at " url
                              " (" (.getMessage ex) ")"))
              nil))
          status (:status post-response)
          result (case status
                   200 :ok
                   400 :rejected
                   :failed)]
      (log/debug "Vault post response, status" status
                 "(" (- (System/currentTimeMillis) begin-t) "msec. )")
      (when (and post-response (not= result :ok))
        (log/error "Vault post to" url "returned HTTP status" status
                   (str "(" (:body post-response) ")")))
      (when (not= result :ok)
        (count-inc! count-vault-post-error))
      result)))

(defn- capture-agent-health  [start-time]
  (log/info "Sending healthcheck")
  (locking sensor-result-queue
    (let [pending-readings (.size sensor-result-queue)]
      (let [agent-name (config/cval :agent :name)
            agent-sensors {:pending-readings pending-readings
                           :sensor-polls @count-sensor-poll
                           :sensor-errors @count-sensor-poll-error
                           :sensor-timeouts @count-sensor-poll-timeout
                           :vault-posts @count-vault-post
                           :vault-errors @count-vault-post-error}]
        (process-sensor-reading (str "agent-" agent-name)
                                (ensure-timestamped agent-sensors))
        (merge
         {:name agent-name
          :current-time (current-time)
          :start-time start-time
          :healthcheck-interval (config/cval :agent :vault-healthcheck-interval-sec)
          :local-ip-address (get-local-ip-address)}
         agent-sensors)))))

(defn- agent-vault-update [start-time]
  (log/info "Updating vault")
  (let [update-size-limit (config/cval :agent :vault-update-size-limit)]
    (loop [need-healthcheck? true]
      (let [batch (peek-result-queue-batch update-size-limit)]
        (when (or need-healthcheck? (seq batch))
          (let [extras (when need-healthcheck?
                         {:healthcheck (capture-agent-health start-time)})
                result (post-to-vault (merge extras {:samples (clean-readings batch)}))]
            (case result
              :ok
              (do
                (remove-result-queue-batch batch)
                (when (seq batch)
                  (recur false)))

              :rejected
              (do
                (log/error "Vault rejected batch, discarding" (count batch) "readings.")
                (remove-result-queue-batch batch)
                (when (seq batch)
                  (recur false)))

              :failed
              (log/warn "Vault post failed, retaining" (.size sensor-result-queue)
                        "pending readings for the next update."))))))))

(defn- start-sensor-polls []
  (doseq [[poll-interval sensors] (group-by :poll-interval (all-sensors))]
    (log/info "Scheduling poll job @" poll-interval "msec. for" (map :sensor-name sensors))
    (at-at/every poll-interval
                 (config/wrap-with-current-config
                  #(with-exception-barrier (str "poller-" poll-interval)
                     (poll-sensors sensors)))
                 my-pool)))

(defn- maybe-load-sensor-file [filename]
  (binding [*ns* (find-ns 'metlog.agent.sensor)]
    (if (.exists (jio/as-file filename))
      (do
        (log/report "Loading sensor file:" filename "*ns*=" *ns*)
        (log/report "  Sensor *ns* public definitions: " (keys (ns-publics *ns*)))
        (load-file filename))
      (log/error "Cannot find sensor file: " filename))))

(defn start-app
  "Start the agent: its sensor polls, and the job that posts readings
  to the vault. Returns once they're started; they keep running in the
  background. Needs no database."
  []
  (let [start-time (current-time)]
    (log/info "Starting agent with config: " (config/cval :agent))
    (maybe-load-sensor-file (:sensor-file (config/cval :agent)))
    (service/with-service {:jobs {:agent-vault-update #(agent-vault-update start-time)}}
      (start-sensor-polls))
    (log/info "Agent started.")))
