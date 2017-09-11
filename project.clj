(defproject specium "0.1.0-SNAPSHOT"
  :description "Inverse of s/form, reducing eval calls as much as possible"
  :url "https://github.com/athos/specium"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0-alpha19" :scope "provided"]
                 [org.clojure/clojurescript "1.9.908" :scope "provided"]]

  :test-paths ["test/cljc"]

  :plugins [[lein-cloverage "1.0.9"]
            [lein-doo "0.1.7"]
            [lein-eftest "0.3.1"]]

  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/out/test.js"
                                   :output-dir "target/out"
                                   :main specium.test-runner
                                   :optimizations :none
                                   :target :nodejs}}]}

  :eftest {:report eftest.report.pretty/report}

  :aliases {"test-all" ["do" ["test-clj"] ["test-cljs"]]
            "test-clj" ["eftest"]
            "test-cljs" ["do" ["test-cljs-node" "once"]]
            "test-cljs-node" ["doo" "node" "test"]})
