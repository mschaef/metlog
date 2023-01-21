(ns metlog-agent.main
  (:gen-class)
  (:require [playbook.logging :as logging]
            [playbook.config :as config]
            [taoensso.timbre :as log]
            [metlog-agent.core :as core]
            [metlog-agent.sensor :as sensor]))

(defn -main
  "Agent entry point"
  [& args]
  (let [config (config/load-config)]
    (log/info "configuration: " config)
    (logging/setup-logging config [[#{"metlog-agent.*"} :info]])
    (core/start-app config)
    (log/info "running.")))
