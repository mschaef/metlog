(ns metlog-common.logging
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [taoensso.timbre.tools.logging :as tools]
            [taoensso.encore :as enc]
            [taoensso.timbre.appenders.3rd-party.rolling :as rolling]))

(defn- log-output-fn [ data ]
  (let [{:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                timestamp_ ?line]} data]
    (str
     (when-let [ts (force timestamp_)] (str ts " "))
     (str/upper-case (name level))  " "
     "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
     (force msg_)
     (when-let [err ?err]
       (str enc/system-newline (log/stacktrace err {:stacktrace-fonts {}}))))))

(defn setup-logging [ config log-levels ]
  (log/info "Starting logging in: " (:log-path config) ", log levels: " log-levels)
  (let [{:keys [development-mode]} config]
    (tools/use-timbre)
    (log/merge-config! {:min-level (conj log-levels
                                         [#{"*"} (if development-mode :info :warn)])
                        :output-fn log-output-fn
                        :appenders {:println {:enabled? development-mode}
                                    :log-file (assoc
                                               (rolling/rolling-appender
                                                {:path (:log-path config)
                                                 :pattern :daily})
                                               :enabled? (not development-mode))}})))
