(defproject metrocene "0.0.1-SNAPSHOT"
  :dependencies [[ring "1.2.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1978"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]
                 [org.clojure/data.json "0.2.0"]
                 [clatrix "0.3.0"]
                 [net.drib/strokes "0.5.0"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [compojure "1.1.5"]
                 [com.keminglabs/c2 "0.2.3"]
                 [com.keminglabs/vomnibus "0.3.1"]
                 [org.clojure/java.jdbc "0.3.0-alpha5"]
                 [postgresql "9.1-901.jdbc4"]
                 [com.cemerick/friend "0.2.0"]
                 [mavericklou/clj-facebook-graph "0.5.3"]
                 [friend-oauth2 "0.0.4" 
                  :exclusions [[org.apache.httpcomponents/httpcore] 
                               [com.cemerick/friend]]]
                 [enlive "1.1.4"]
                 [org.clojure/core.typed "0.2.13"]
                 [lobos "1.0.0-beta1"]]
  :plugins [[lein-cljsbuild "0.3.4"]
            [lein-ring "0.8.3"]
            [lein-typed "0.3.0"]
            [lein-lobos "1.0.0-beta1"]]
  :uberjar-name "metrocene-standalone.jar"
  :hooks [leiningen.cljsbuild]
  :source-paths ["src/clj"]
  :cljsbuild { 
    :builds {
      :main {
        :source-paths ["src/cljs"]
        :compiler {:output-to "resources/public/js/cljs.js"
                   :output-dir "resources/public/js"
                   :optimizations :none
                   :source-map true}
        :jar true}}}
  :min-lein-version "2.0.0"
  :ring {:handler metrocene.core/handler}
  :core.typed {:check [metrocene.core]})