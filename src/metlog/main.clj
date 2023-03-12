(ns metlog.main
  (:gen-class)
  (:require [playbook.logging :as logging]
            [playbook.config :as config]
            [taoensso.timbre :as log]
            [metlog-agent.core :as agent]
            [metlog-vault.core :as vault]
            [metlog-agent.sensor :as sensor]))

(defn -main [& args]
  (let [config (config/load-config)
        mode (:mode config)]
    (logging/setup-logging config [[#{"metlog.main"} :info]
                                   [#{"hsqldb.*" "com.zaxxer.hikari.*"} :warn]])
    (log/info "config: " config)
    (when (:agent mode)
      (log/info "Starting Agent")
      (agent/start-app config))
    (when (:vault mode)
      (log/info "Starting Vault")
      (vault/start-app config))
    (if (> (count mode) 0)
      (log/info "running, mode: " mode)
      (log/error "No modes specified. Ending run."))))
