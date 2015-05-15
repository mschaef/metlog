(defproject metlog-common "0.1.0-SNAPSHOT"
  :description "Code common between the metlog agent and vault."

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :main metlog-common.core
  :aot [metlog-common.core]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [org.clojure/java.jdbc "0.3.5"]])
