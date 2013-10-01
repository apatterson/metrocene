(ns metrocene.models.migration
  (:require [clojure.java.jdbc :as sql]))

(def db (System/getenv "HEROKU_POSTGRESQL_MAROON_URL"))

(defn create-nodes []
  (sql/with-connection db
    (sql/drop-table :nodes)
    (sql/create-table :nodes
      [:id :serial "PRIMARY KEY"]
      [:name :varchar "NOT NULL"]      
      [:x :integer "NOT NULL"]      
      [:y :integer "NOT NULL"]
      [:created_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"])
    (sql/insert-records 
     :nodes
     {:name "Increased Emissions"   :x 200 :y 100}
     {:name "Global Warming"     :x 300 :y 200}
     {:name "Extreme Weather Occurrence"     :x 400 :y 300}
     {:name "Employment"    :x 500 :y 200}
     {:name "Investment in Clean Technology"    :x 600 :y 100}
     {:name "Increased Environmental Regulation"    :x 200 :y 300}
     {:name "Global Investment"    :x 700 :y 300})))

(defn create-links []
  (sql/with-connection db
    (sql/create-table :links
      [:id :serial "PRIMARY KEY"]
      [:tail :integer "NOT NULL"]      
      [:head :integer "NOT NULL"]      
      [:weight :integer "NOT NULL"])))

(defn -main []
  (print "Creating database structure...") (flush)
  (create-nodes)
  (create-links)
  (println " done"))