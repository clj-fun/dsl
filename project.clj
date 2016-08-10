(defproject dsl "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [hawk "0.2.10"] ; Watch files with clojure (in the manner of a hawk), works on OSX
                 ]
  :main core
  :aot :all
  :uberjar-name ~(str "dsl-%s-"
                      (-> (clojure.java.shell/sh "git" "rev-parse" "--short" "HEAD") :out .trim)
                      ".uber.jar")
  :profiles {:uberjar {:aot :all}}
  )
