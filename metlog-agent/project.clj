(defproject metlog-agent "0.1.1-SNAPSHOT"
  :description "Metlog agent - polls data sources for upload to vault."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :scm {:dir ".."}
  
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.14.4"]
                 [clj-http "3.1.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [overtone/at-at "1.2.0"]
                 [com.cognitect/transit-clj "0.8.285"]

                 [metlog-common "0.1.0"]]
  
  :main ^:skip-aot metlog-agent.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}

  :plugins [[lein-tar "3.3.0"]]
  
  :tar {:uberjar true
        :format :tar-gz
        :output-dir "."
        :leading-path "metlog-agent-install"}
  
  :jar-name "metlog-agent.jar"
  :uberjar-name "metlog-agent-standalone.jar"

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "metlog-agent-" "--no-sign"]
                  ["tar"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]  )
