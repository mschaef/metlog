(defproject metlog-agent "0.1.2"
  :description "Metlog agent - polls data sources for upload to vault."

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :scm {:dir ".."}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-time "0.15.2"]
                 [clj-http "3.12.3"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [overtone/at-at "1.2.0"]
                 [com.cognitect/transit-clj "1.0.329"]
                 [metlog-common "0.1.2"]]

  :plugins [[lein-tar "3.3.0"]]

  :tar {:uberjar true
        :format :tar-gz
        :output-dir "."
        :leading-path "metlog-agent-install"}

  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}}

  :jar-name "metlog-agent.jar"
  :uberjar-name "metlog-agent-standalone.jar"

  :main ^:skip-aot metlog-agent.core
  :jvm-opts ["-Dconf=local-config.edn"]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "metlog-agent-" "--no-sign"]
                  ["tar"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
