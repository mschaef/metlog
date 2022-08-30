(ns metlog-agent.main
  (:gen-class)
  (:require [metlog-common.logging :as logging]
            [metlog-common.config :as config]
            [taoensso.timbre :as log]
            [metlog-agent.core :as core]
            [metlog-agent.sensor :as sensor]))

(defn -main
  "Agent entry point"
  [& args]
  (let [config (config/load-config)]
    (logging/setup-logging config [])
    (core/start-app config)
    (log/info "running.")))
