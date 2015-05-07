(ns metlog-agent.util
  (:require [clojure.tools.logging :as log]))

(defn exception-barrier [ fn ]
  #(try
     (fn)
     (catch Exception ex
         (log/error "Uncaught exception" ex))))

