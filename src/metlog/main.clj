(ns metlog.main
  (:gen-class)
  (:use playbook.core
        playbook.main)
  (:require [taoensso.timbre :as log]
            [playbook.config :as config]
            [metlog.agent.core :as agent]
            [metlog.vault.core :as vault]
            ;; Loaded so that sensor files have a namespace to load into.
            [metlog.agent.sensor :as sensor]))

(def ^:private known-modes #{:agent :vault})

(defn- check-database-name
  "Fail unless the database name is configured, as [:db :subname] or
  the db.subname property (which playbook.db prefers). Without one,
  playbook.db would quietly open a new, empty database named after
  [:app :name]. Checked in every mode, so a configuration that's
  missing it fails as soon as metlog starts."
  []
  (when-not (or (config/property "db.subname")
                (config/cval-or [:db :subname] nil))
    (FAIL "No database name configured: set [:db :subname] (or the db.subname property).")))

(defmain [& args]
  (let [mode (config/cval :mode)]
    (log/info "config: " (config/cval))
    (when (empty? mode)
      (FAIL "No modes specified in [:mode]; expected some of: " known-modes))
    (when-let [unknown (seq (remove known-modes mode))]
      (FAIL "Unknown modes in [:mode]: " (vec unknown)))
    (check-database-name)
    (log/info "running, mode: " mode)
    ;; The agent starts its polls and jobs and returns. The vault runs
    ;; until its web server stops, so it starts last. Each has its own
    ;; scheduler, and only the vault opens a database.
    (when (:agent mode)
      (log/info "Starting Agent")
      (agent/start-app))
    (when (:vault mode)
      (log/info "Starting Vault")
      (vault/start-app))))
