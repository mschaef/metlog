(defproject metlog "0.6.7"
  :description "Lightweight tool for gathering, storing, and inspecting metrics."

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.60"]
                 [overtone/at-at "1.2.0"]
                 [com.mschaef/sql-file "0.4.8"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 [slester/ring-browser-caching "0.1.1"]
                 [com.cognitect/transit-clj "1.0.329"]
                 [compojure "1.7.0"
                  :exclusions [commons-codec]]
                 [hiccup "1.0.5"]
                 [clj-time "0.15.2"]
                 [clj-http "3.12.3"
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [org.clojure/core.async "1.5.648"]
                 [cljs-ajax "0.8.4"]
                 [cljsjs/react "18.2.0-0"]
                 [cljsjs/react-dom "18.2.0-0"]
                 [reagent "1.1.1"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [it.sauronsoftware.cron4j/cron4j "2.2.5"]
                 [com.bhauman/figwheel-main "0.2.18"]
                 [com.bhauman/rebel-readline-cljs "0.1.4"]
                 [com.mschaef/playbook "0.0.2"]]

  :plugins [[lein-tar "3.3.0"]]

  :tar {:uberjar true
        :format :tar-gz
        :output-dir "."
        :leading-path "metlog-install"}

  :source-paths ["src-client"  "src-server"]
  :resource-paths ["resources"]

  :clean-targets ^{:protect false} [:target-path :compile-path  "resources/public/cljs-out"]

  :uberjar-name "metlog-vault-standalone.jar"

  :main metlog.main

  :jvm-opts ["-Dconf=local-config.edn"]

  :target-path "target/%s"

  :aliases {"fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]}

  :profiles {:uberjar
             {:prep-tasks ["compile" ["fig:min"]]
              :aot :all}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["tar"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
