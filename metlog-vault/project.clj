(defproject metlog-vault "0.1.0-SNAPSHOT"
  :description "Repository for long term storage of series data."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[lein-cljsbuild "1.0.6"]]
  :hooks [leiningen.cljsbuild]
  :source-paths ["src-server"]
  
  :dependencies [[org.clojure/clojure "1.7.0-beta2"]
                 [org.clojure/clojurescript "0.0-3269"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [compojure "1.3.4"]
                 [com.ksmpartners/sql-file "0.1.0"]
                 [metlog-common "0.1.0-SNAPSHOT"]
                 [org.omcljs/om "0.8.8"]]

  :main ^:skip-aot metlog-vault.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}

  :cljsbuild {:builds [{:source-paths ["src-client"]
                        :compiler {:output-to "resources/public/metlog.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]}
  
  :jar-name "metlog-vault.jar"
  :uberjar-name "metlog-vault-standalone.jar")
