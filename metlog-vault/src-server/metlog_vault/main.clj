(ns metlog-vault.main
  (:gen-class :main true)
  (:use playbook.core
        metlog-vault.util)
  (:require [playbook.logging :as logging]
            [playbook.config :as config]
            [taoensso.timbre :as log]
            [metlog-vault.core :as core]))

(defn -main [& args]
  (let [config (config/load-config)]
    (logging/setup-logging config [[#{"hsqldb.*" "com.zaxxer.hikari.*"} :warn]])
    (core/start-app config)
    (log/info "running.")))
