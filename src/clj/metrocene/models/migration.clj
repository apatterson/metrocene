(ns metrocene.models.migration
  (:require [clojure.java.jdbc :as sql]))

(defn create-nodes []
  (sql/with-connection (System/getenv "HEROKU_POSTGRESQL_MAROON_URL")
    (sql/create-table :nodes
      [:id :serial "PRIMARY KEY"]
      [:name :varchar "NOT NULL"]      
      [:x :integer "NOT NULL"]      
      [:y :integer "NOT NULL"]
      [:created_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"])))

(defn -main []
  (print "Creating database structure...") (flush)
  (create-nodes)
  (println " done"))