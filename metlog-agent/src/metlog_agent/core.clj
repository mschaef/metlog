(ns metlog-agent.core
  (:gen-class)
  (:require [overtone.at-at :as at-at]))

(def my-pool (at-at/mk-pool))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (at-at/every 1000 #(println "I am cool!") my-pool :initial-delay 2000)
  (println "Hello, World!" (java.util.Date.)))
