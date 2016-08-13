(ns metlog-client.logger
  (:require-macros metlog-client.logger)
  (:require [clojure.string :as string]))

(def load-time (.getTime (js/Date.)))

(def log-writer (.-log js/console))
(def warning-writer (.-warn js/console))
(def error-writer (.-error js/console))

(defn- log-message-timestamp []
  (.substr (.toISOString (js/Date.)) 11 12))

(def log-level-info
  {:trace {:message-writer log-writer     :ordinal 0}
   :debug {:message-writer log-writer     :ordinal 1}
   :info  {:message-writer log-writer     :ordinal 2}
   :warn  {:message-writer warning-writer :ordinal 3}
   :error {:message-writer error-writer   :ordinal 4}
   :fatal {:message-writer error-writer   :ordinal 5}})

(def default-logger-configuration {"" :info })

(def logger-configuration default-logger-configuration)

(defn set-configuration! [ new-configurations ]
  (set! logger-configuration (merge default-logger-configuration
                                    new-configurations)))

(defn- logger-names [ logger-name ]
  (let [ segments (js->clj (.split logger-name ".")) ]
    (vec (map #(string/join "." (take % segments))
              (range (count segments) -1 -1)))))

(defn logger-active? [ logger-name message-level ]
  (let [ logger-level (some logger-configuration (logger-names logger-name)) ]
    (<= (:ordinal (log-level-info logger-level))
        (:ordinal (log-level-info message-level)))))


(defn- do-message [ message-writer logger-name level args ]
  (.apply message-writer js/console
          (clj->js (concat [ (log-message-timestamp) (str level) logger-name "-" ]
                           args))))

(defn log* [ logger-name message-level & args ]
  (if-let [ message-level-info (log-level-info message-level)]
    (when (logger-active? logger-name message-level)
        (do-message (:message-writer message-level-info) logger-name message-level args))
    (error-writer "Unknown log level" (clj->js message-level))))
