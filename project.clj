(defproject metlog "0.7.1-SNAPSHOT"
  :description "Lightweight tool for gathering, storing, and inspecting metrics."

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [overtone/at-at "1.2.0"]
                 [com.mschaef/sql-file "0.4.8"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 [slester/ring-browser-caching "0.1.1"]
                 [ring/ring-devel "1.9.4"]
                 [ring/ring-json "0.5.1"]
                 [com.cognitect/transit-clj "1.0.329"]
                 [compojure "1.7.0"
                  :exclusions [commons-codec]]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "2.4.0"]
                 [clj-time "0.15.2"]
                 [clj-http "3.12.3"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [org.clojure/core.async "1.5.648"]
                 [reagent "1.1.1"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [it.sauronsoftware.cron4j/cron4j "2.2.5"]
                 [com.mschaef/playbook "0.0.2"]]

  :plugins [[lein-tar "3.3.0"]]

  :tar {:uberjar true
        :format :tar-gz
        :output-dir "."
        :leading-path "metlog-install"}

  :resource-paths ["resources"]

  :uberjar-name "metlog-standalone.jar"

  :main metlog.main

  :jvm-opts ["-Dconf=local-config.edn"]

  :target-path "target/%s"

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["tar"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
