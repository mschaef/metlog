(ns metlog.main
  (:gen-class)
  (:require [playbook.logging :as logging]
            [playbook.config :as config]
            [taoensso.timbre :as log]
            [metlog-agent.core :as agent]
            [metlog-vault.core :as vault]
            [metlog-agent.sensor :as sensor]))

(defn -main [& args]
  (let [config (config/load-config)]
    (log/info "config: " config)
    (logging/setup-logging config [[#{"metlog-agent.*"} :info]
                                   [#{"hsqldb.*" "com.zaxxer.hikari.*"} :warn]])
    (when (:enable (:agent config))
      (agent/start-app config))
    (when (:enable (:vault config))
      (vault/start-app config))
    (log/info "running.")))
