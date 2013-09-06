(defproject metrocene "0.0.1"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1859"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [org.clojure/data.json "0.2.0"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [clatrix "0.3.0"]
                 [net.drib/strokes "0.5.0"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [compojure "1.1.5"]]
  :repositories {"sonatype-staging"
                 "https://oss.sonatype.org/content/groups/staging/"}
  :plugins [[lein-cljsbuild "0.3.0"]
            [lein-ring "0.8.3"]]
  :uberjar-name "metrocene-standalone.jar"
  :hooks [leiningen.cljsbuild]
  :source-paths ["src/clj"]
  :cljsbuild { 
    :builds {
      :main {
        :source-paths ["src/cljs"]
        :compiler {:output-to "resources/public/js/cljs.js"
                   :optimizations :simple
                   :pretty-print true}
        :jar true}}}
  :min-lein-version "2.0.0")