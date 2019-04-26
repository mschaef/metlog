(defproject metlog-common "0.1.0-SNAPSHOT"
  :description "Code common between the metlog agent and vault."

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :scm {:dir ".."}
  
  :main metlog-common.core
  :aot [metlog-common.core]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.clojure/data.json "0.2.6"]]


  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "metlog-common-" "--no-sign"]
                  ["install"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
