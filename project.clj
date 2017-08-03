(defproject specium "0.1.0-SNAPSHOT"
  :description "Inverse of s/form, reducing eval calls as much as possible"
  :url "https://github.com/athos/specium"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/clojurescript "1.9.854"]]

  :test-paths ["test/cljc"]

  :plugins [[lein-cloverage "1.0.9"]
            [lein-eftest "0.3.1"]]
  :eftest {:report eftest.report.pretty/report}
  :aliases {"test" ["eftest"]})
