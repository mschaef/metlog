(defproject metlog "0.8.29-SNAPSHOT"
  :description "Lightweight tool for gathering, storing, and inspecting metrics."

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.12.1"]
                 [overtone/at-at "1.4.65"]
                 [com.mschaef/sql-file "0.4.13"]
                 [yesql "0.5.4"]
                 [ring/ring-jetty-adapter "1.14.1"]
                 [ring/ring-devel "1.14.1"]
                 [ring/ring-codec "1.3.0"]
                 [slester/ring-browser-caching "0.1.1"]
                 [ring/ring-json "0.5.1"]
                 [com.cognitect/transit-clj "1.0.333"]
                 [compojure "1.7.1"
                  :exclusions [commons-codec]]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "2.5.1"]
                 [clj-time "0.15.2"]
                 [clj-http "3.13.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.mschaef/playbook "0.1.5"]]

  :plugins [[lein-tar "3.3.0"]
            [dev.weavejester/lein-cljfmt "0.13.0"]]

  :cljfmt {:load-config-file? true}

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
