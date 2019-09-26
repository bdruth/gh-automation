(defproject github-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [tentacles "0.3.0"]]
  :profiles {:dev {
    :plugins [[lein-midje "3.1.3"]]
    :dependencies [[midje "1.6.3"]]}})
