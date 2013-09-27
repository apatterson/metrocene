(ns metrocene.models.population
  (:require [clojure.java.jdbc :as sql]))

(defn populate-nodes []
  (sql/with-connection (System/getenv "HEROKU_POSTGRESQL_MAROON_URL")
    (sql/insert-records 
     :nodes
     {:name "CO2 Emissions"   :x 200 :y 100}
     {:name "Global Warming"     :x 300 :y 200}
     {:name "Extreme weather"     :x 400 :y 300}
     {:name "Economic Growth"    :x 500 :y 200}
     {:name "Clean Technology"    :x 600 :y 100}
     {:name "Environmental Regulation"    :x 200 :y 300}
     {:name "Free Trade"    :x 700 :y 300})))


(defn -main []
  (print "populating...") (flush)
  (populate-nodes)
  (println " done"))