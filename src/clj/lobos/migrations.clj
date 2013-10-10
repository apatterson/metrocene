(ns lobos.migrations
  (:refer-clojure 
   :exclude [alter drop bigint boolean char double float time])
  (:require [clojure.java.jdbc :as sql])
  (:use (lobos [migration :only [defmigration]] core schema config)
        [metrocene.models.db]))

(defmigration add-nodes-table
  (up [] 
      (create
       (table :nodes
              (integer :id :auto-inc :primary-key)
              (varchar :name 128 :not-null)
              (integer :x :not-null)
              (integer :y :not-null)
              (varchar :userid 32 :not-null)
              (varchar :groupid 32 :not-null)
              (varchar :colour 32 :not-null (default "#ffffbf"))))))

(defmigration add-groupnodes-table
  (up [] 
      (create
       (table :groupnodes
              (integer :id :auto-inc :primary-key)
              (varchar :name 128 :not-null)
              (integer :x :not-null)
              (integer :y :not-null)
              (varchar :groupid 32 :not-null)
              (varchar :colour 32 :not-null (default "#ffffbf"))))))
              
(defmigration add-links-table
  (up [] 
      (create
       (table :links
              (integer :id :auto-inc :primary-key)
              (integer :head :not-null)
              (integer :tail :not-null)
              (integer :weight :not-null)
              (varchar :userid 32 :not-null)
              (varchar :groupid 32 :not-null))))
  (down [] (drop (table :links))))

(defmigration add-grouplinks-table
  (up [] 
      (create
       (table :grouplinks
              (integer :id :auto-inc :primary-key)
              (integer :head :not-null)
              (integer :tail :not-null)
              (integer :weight :not-null)
              (varchar :groupid 32 :not-null)))),
  (down [] (drop (table :grouplinks))))

(defmigration add-node-entries
  (up 
   []
   (sql/with-connection db
     (sql/insert-records 
      :groupnodes
      {:groupid "0" :name "Increased Emissions"   :x 200 :y 100}
      {:groupid "0" :name "Global Warming"     :x 300 :y 200}
      {:groupid "0" :name "Extreme Weather Occurrence"     :x 400 :y 300}
      {:groupid "0" :name "Employment"    :x 500 :y 200}
      {:groupid "0" :name "Investment in Clean Technology"    :x 600 :y 100}
      {:groupid "0" :name "Increased Environmental Regulation"    :x 200 :y 300}
      {:groupid "0" :name "Global Investment"    :x 700 :y 300}))))