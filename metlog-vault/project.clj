(defproject metlog-vault "0.3.2-SNAPSHOT"
  :description "Repository for long term storage of series data."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :scm {:dir ".."}
  
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [clj-time "0.15.1"]
                 [com.mschaef/sql-file "0.4.0"]                 
                 [ring/ring-jetty-adapter "1.7.1"]
                 [slester/ring-browser-caching "0.1.1"]
                 [com.cognitect/transit-clj "0.8.313"]
                 [compojure "1.6.1"
                  :exclusions [commons-codec]]
                 [hiccup "1.0.5"]
                 
                 [org.clojure/core.async "0.4.490"]
                 [cljs-ajax "0.8.0"] 
                 [reagent "0.8.1"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]

                 [metlog-common "0.1.0"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-tar "3.3.0"]]

  :tar {:uberjar true
        :format :tar-gz
        :output-dir "."
        :leading-path "metlog-vault-install"}
  
  :source-paths ["src-server"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/compiled"]
  
  :uberjar-name "metlog-vault-standalone.jar"

  :main metlog-vault.core

  :target-path "target/%s"

  :cljsbuild {:builds
              {:app
               {:source-paths ["src-client"]
                
                :figwheel true

                :compiler {:main metlog-client.metlog
                           :asset-path "compiled/out"
                           :output-to "resources/public/compiled/metlog.js"
                           :output-dir "resources/public/compiled/out"
                           :source-map-timestamp true}}}}
    
  :figwheel {:server-port 8080
             :css-dirs ["resources/public"]
             :ring-handler metlog-vault.core/handler
             :server-logfile "log/figwheel.log"}
  
  :profiles {:dev
             {:dependencies [[figwheel "0.5.18"]
                             [figwheel-sidecar "0.5.18"]
                             [com.cemerick/piggieback "0.2.2"]
                             [org.clojure/tools.nrepl "0.2.13"]]
              :jvm-opts ["--add-modules" "java.xml.bind"]
              :plugins [[lein-figwheel "0.5.18"]]

              :cljsbuild {:builds
                          {:test
                           {:source-paths ["src-client"]
                            :compiler
                            {:optimizations :none
                             :pretty-print true}}}}}

             :uberjar
             {:source-paths ^:replace ["src-server"]
              :hooks [leiningen.cljsbuild]
              :omit-source true
              :aot :all
              :cljsbuild {:builds
                          {:app
                           {:source-paths ^:replace ["src-client"]
                            :compiler
                            {:optimizations :advanced
                             :pretty-print false}}}}}}

    :release-tasks [["vcs" "assert-committed"]
                    ["change" "version" "leiningen.release/bump-version" "release"]
                    ["vcs" "commit"]
                    ["vcs" "tag" "metlog-vault-" "--no-sign"]
                    ["tar"]
                    ["change" "version" "leiningen.release/bump-version"]
                    ["vcs" "commit"]
                    ["vcs" "push"]])
