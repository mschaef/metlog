(defproject metlog-vault "0.1.0-SNAPSHOT"
  :description "Repository for long term storage of series data."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[lein-cljsbuild "1.0.6"
             :exclusions [org.clojure/clojure]]
            [lein-cooper "1.1.1"]]
  
  :source-paths ["src-server"]
  
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
                 [cljs-ajax "0.5.3"]
                 [reagent "0.5.1"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]

                 [metlog-common "0.1.0-SNAPSHOT"]]

  :main ^:skip-aot metlog-vault.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}

  :cljsbuild {:builds [{:source-paths ["src-client"]
                        :compiler
                        {:output-to "resources/public/metlog.js"
                         ;; :optimizations :advanced
                         :optimizations :whitespace :pretty-print true}}]}
  
  :jar-name "metlog-vault.jar"
  :uberjar-name "metlog-vault-standalone.jar")
