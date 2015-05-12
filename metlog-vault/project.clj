(defproject metlog-vault "0.1.0-SNAPSHOT"
  :description "Repository for long term storage of series data."
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [compojure "1.3.2"]
                 [metlog-common "0.1.0-SNAPSHOT"]]

  :main ^:skip-aot metlog-vault.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  
  :jar-name "metlog-vault.jar"
  :uberjar-name "metlog-vault-standalone.jar")
