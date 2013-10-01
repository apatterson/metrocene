(ns metrocene.models.population
  (:require 
[clojure.java.jdbc :as sql]))

(defn populate-nodes []
  (sql/with-connection (System/getenv "HEROKU_POSTGRESQL_MAROON_URL")
    (sql/delete-rows :nodes "*")
    (sql/insert-records 
     :nodes
     {:name "Increased Emissions"   :x 200 :y 100}
     {:name "Global Warming"     :x 300 :y 200}
     {:name "Extreme Weather Occurrence"     :x 400 :y 300}
     {:name "Employment"    :x 500 :y 200}
     {:name "Investment in Clean Technology"    :x 600 :y 100}
     {:name "Increased Environmental Regulation"    :x 200 :y 300}
     {:name "Global Investment"    :x 700 :y 300})))


(defn -main []
  (print "populating...") (flush)
  (populate-nodes)
  (println " done"))