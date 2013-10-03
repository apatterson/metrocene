(defproject metrocene "0.0.1-SNAPSHOT"
  :dependencies [[ring "1.2.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1859"]
                 [org.clojure/core.async "0.1.222.0-83d0c2-alpha"]
                 [org.clojure/data.json "0.2.0"]
                 [clatrix "0.3.0"]
                 [net.drib/strokes "0.5.0"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [compojure "1.1.5"]
                 [com.keminglabs/c2 "0.2.3"]
                 [com.keminglabs/vomnibus "0.3.1"]
                 [org.clojure/java.jdbc "0.2.3"]
                 [postgresql "9.1-901.jdbc4"]
                 [com.cemerick/friend "0.2.0"]
                 [mavericklou/clj-facebook-graph "0.5.3"]
                 [friend-oauth2 "0.0.4" 
                  :exclusions [[org.apache.httpcomponents/httpcore] 
                               [com.cemerick/friend]]]
                 [enlive "1.1.4"]]
  :plugins [[lein-cljsbuild "0.3.2"]
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
  :min-lein-version "2.0.0"
  :ring {:handler metrocene.core/handler})