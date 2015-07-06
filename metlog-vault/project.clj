(defproject metlog-vault "0.1.0-SNAPSHOT"
  :description "Repository for long term storage of series data."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[lein-cljsbuild "1.0.6"
             :exclusions [org.clojure/clojure]]
            [lein-cooper "1.1.1"]]
  
  :source-paths ["src-server"]
  
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [com.ksmpartners/sql-file "0.1.0"]                 
                 [ring/ring-jetty-adapter "1.3.2"]
                 [compojure "1.3.4"]
                 [cljs-ajax "0.3.11"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.8"]
                 [reagent "0.5.0"]
                 [com.andrewmcveigh/cljs-time "0.3.5"]
                 [metlog-common "0.1.0-SNAPSHOT"]]

  :main ^:skip-aot metlog-vault.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}

  :cljsbuild {:builds [{:source-paths ["src-client"]
                        :compiler {:output-to "resources/public/metlog.js"
                                   ; :optimizations :advanced
                                   :optimizations :whitespace :pretty-print true
                                   }}]}
  
  :jar-name "metlog-vault.jar"
  :uberjar-name "metlog-vault-standalone.jar")
