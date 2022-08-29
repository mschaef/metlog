(defproject metlog-common "0.1.2"
  :description "Code common between the metlog agent and vault."

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :scm {:dir ".."}

  :main metlog-common.core
  :aot [metlog-common.core]

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [com.taoensso/timbre "5.2.1"]
                 [com.fzakaria/slf4j-timbre "0.3.21"]
                 [cprop "0.1.19"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.clojure/data.json "2.4.0"]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "metlog-common-" "--no-sign"]
                  ["install"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
