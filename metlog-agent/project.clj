(defproject metlog-agent "0.1.0-SNAPSHOT"
  :description "Metlog agent - polls data sources for upload to vault."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[metlog-common "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.8.0"]
                 [clj-http "3.1.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [overtone/at-at "1.2.0"]
                 [com.cognitect/transit-clj "0.8.285"]]
  
  :main ^:skip-aot metlog-agent.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  
  :jar-name "metlog-agent.jar"
  :uberjar-name "metlog-agent-standalone.jar")
