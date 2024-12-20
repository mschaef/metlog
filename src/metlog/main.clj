(ns metlog.main
  (:gen-class)
  (:use playbook.main)
  (:require [playbook.logging :as logging]
            [playbook.config :as config]
            [taoensso.timbre :as log]
            [playbook.scheduler :as scheduler]
            [metlog.agent.core :as agent]
            [metlog.vault.core :as vault]
            [metlog.agent.sensor :as sensor]))

(defmain [ & args ]

  (let [mode (config/cval :mode)
        scheduler (scheduler/start)]
    (log/info "config: " (config/cval))
    (when (:agent mode)
      (log/info "Starting Agent")
      (agent/start-app scheduler))
    (when (:vault mode)
      (log/info "Starting Vault")
      (vault/start-app scheduler))
    (if (> (count mode) 0)
      (log/info "running, mode: " mode)
      (log/error "No modes specified. Ending run."))))
