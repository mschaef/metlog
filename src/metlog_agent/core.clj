(ns metlog-agent.core
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
            [playbook.scheduler :as scheduler]))

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

(defn seconds [ seconds ] (* 1000 seconds))
(defn minutes [ minutes ] (seconds (* 60 minutes)))
(defn hours [ hours ] (minutes (* 60 hours)))
(defn days [ days ] (hours (* 24 days)))

(def my-pool (at-at/mk-pool))

(def count-vault-post (atom 0))
(def count-vault-post-error (atom 0))

(def count-sensor-poll (atom 0))
(def count-sensor-poll-error (atom 0))

(defn- count-inc! [ count ]
  (swap! count inc))

(def sensor-result-queue (java.util.concurrent.LinkedBlockingQueue.))

(defrecord TimestampedValue [ t val ])

(defn timestamped-value [ t val ]
  (TimestampedValue. t val))

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
    (count-inc! count-sensor-poll)
    ((:sensor-fn sensor-def))
    (catch Exception ex
      (count-inc! count-sensor-poll-error)
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
        (do
          (doseq [[sub-name sub-value] val]
            (process-sensor-reading (str sensor-name "-" (name sub-name)) (TimestampedValue. t sub-value))))

        :else
        (log/error "Bad sensor value" val "(" (.getClass val) ")" "from sensor" sensor-name)))))

(defn ->timestamped-readings [ obj ]
  (map ensure-timestamped (if (vector? obj) obj [ obj ])))

(defn poll-sensors [ sensor-defs ]
  (log/trace "poll-sensors" (map :sensor-name sensor-defs))
  (doseq [ sensor-def sensor-defs ]
    (doseq [ ts-sensor-reading (->timestamped-readings (poll-sensor sensor-def)) ]
      (process-sensor-reading (:sensor-name sensor-def) ts-sensor-reading))))

(defn take-result-queue-snapshot [ snapshot-size-limit ]
  (let [ snapshot (java.util.concurrent.LinkedBlockingQueue.) ]
    (locking sensor-result-queue
      (log/trace "Taking snapshot queue with n:" (.size sensor-result-queue)
                 ", limit:" snapshot-size-limit)
      (.drainTo sensor-result-queue snapshot snapshot-size-limit ))
    (seq snapshot)))

(def update-queue (java.util.concurrent.LinkedBlockingQueue.))

(defn clean-readings [ unclean ]
  (map #(assoc % :val (double (:val %))) unclean))

(defn post-to-vault [ data ]
  (let [ url (str (config/cval :agent :vault-url) "/agent/data")]
    (log/debug "Posting to vault at:" url)
    (count-inc! count-vault-post)
    (let [begin-t (System/currentTimeMillis)
          post-response
          (try
            (http/post url {:content-type "application/transit+json"
                            :body (pr-transit data)})
            (catch Exception ex
              ;; Pretend POST exceptions are HTTP errors
              (log/error (str "Error posting to vault at " url
                              " (" (.getMessage ex) ")"))
              {:status 400}))]
      (log/debug "Vault post response, status" (:status post-response)
                 "(" (- (System/currentTimeMillis) begin-t) "msec. )")
      (let [ success (= (:status post-response) 200) ]
        (when (not success)
          (count-inc! count-vault-post-error))
        success))))

(defn- snapshot-to-update-queue [ ]
  (let [ update-size-limit (config/cval :agent :vault-update-size-limit)]
    (locking update-queue
      (let [snapshot (take-result-queue-snapshot
                      (min update-size-limit
                           (- update-size-limit (.size update-queue)))) ]
        (when snapshot
          (.addAll update-queue snapshot))
        snapshot))))

(defn- capture-agent-health  [ start-time ]
  (log/info "Sending healthcheck")
  (locking sensor-result-queue
    (let [pending-readings (.size sensor-result-queue)]
      (let [agent-name (config/cval :agent :name)
            agent-sensors {:pending-readings pending-readings
                           :sensor-polls @count-sensor-poll
                           :sensor-errors @count-sensor-poll-error
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

(defn- post-update [ update-extras ]
  (locking update-queue
    (let [has-samples? (not (.isEmpty update-queue))]
      (post-to-vault (merge
                      update-extras
                      {:samples (clean-readings (seq update-queue))}))
      (.clear update-queue)
      has-samples?)))

(defn- agent-vault-update [ start-time ]
  (log/info "Updating vault")
  (loop [ need-healthcheck? true ]
    (let [snapshot (snapshot-to-update-queue)]
      (when (post-update (if need-healthcheck?
                           {:healthcheck (capture-agent-health start-time)}
                           {}))
        (recur false)))))

(defn wrap-with-current-config [ f ]
  (let [ config (config/cval) ]
    #(config/with-config config
       (f))))

(defn- start-sensor-polls [ ]
  (doseq [ [ poll-interval sensors ] (group-by :poll-interval (all-sensors))]
    (log/info "Scheduling poll job @" poll-interval "msec. for" (map :sensor-name sensors))
    (at-at/every poll-interval
                 (wrap-with-current-config
                  #(with-exception-barrier (str "poller-" poll-interval)
                     (poll-sensors sensors)))
                 my-pool)))

(defn- maybe-load-sensor-file [ filename ]
  (binding [ *ns* (find-ns 'metlog-agent.sensor)]
    (if (.exists (jio/as-file filename))
      (do
        (log/report "Loading sensor file:" filename "*ns*=" *ns*)
        (log/report "  Sensor *ns* public definitions: " (keys (ns-publics *ns*)))
        (load-file filename))
      (log/error "Cannot find sensor file: " filename))))

(defn start-app [ scheduler ]
  (let [ start-time (current-time)]
    (log/info "Starting agent with config: " (config/cval :agent))
    (maybe-load-sensor-file (:sensor-file (config/cval :agent)))
    (start-sensor-polls)
    (scheduler/schedule-job scheduler :agent-vault-update
                            #(agent-vault-update start-time))
    (log/info "Agent started.")))
