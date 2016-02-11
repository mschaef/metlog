(defproject metlog-vault "0.1.0-SNAPSHOT"
  :description "Repository for long term storage of series data."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [com.ksmpartners/sql-file "0.1.0"
                  :exclusions [org.clojure/java.jdbc]]                 
                 [ring/ring-jetty-adapter "1.4.0"]
                 [slester/ring-browser-caching "0.1.1"]
                 [compojure "1.4.0"
                  :exclusions [commons-codec]]
                 [hiccup "1.0.5"]
                 
                 [org.clojure/core.async "0.2.374"]
                 ;; Old version to work around https://github.com/JulianBirch/cljs-ajax/issues/109
                 [cljs-ajax "0.3.11"] 
                 [reagent "0.5.1"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]

                 [metlog-common "0.1.0-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-cooper "1.1.1"]]

  :min-lein-version "2.5.3"

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
             {:dependencies [[figwheel "0.5.0-6"]
                             [figwheel-sidecar "0.5.0-6"]
                             [com.cemerick/piggieback "0.2.1"]
                             [org.clojure/tools.nrepl "0.2.12"]]

              :plugins [[lein-figwheel "0.5.0-6"]]

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
                             :pretty-print false}}}}}})
