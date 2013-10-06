(ns lobos.migrations
  (:refer-clojure 
   :exclude [alter drop bigint boolean char double float time])
  (:use (lobos [migration :only [defmigration]] core schema config)))

#_(defmigration add-links-table
  (up [] 
      (create
       (table :links
              (integer :id :auto-inc :primary-key)
              (integer :head :not-null)
              (integer :tail :not-null)
              (integer :weight :not-null))))
  (down [] (drop (table :links))))

(defmigration add-users
  (up []
      (alter :add
             (table :links
                    (varchar :userid 30 :not-null))))
  (down [] 
        (alter :drop
               (table :links
                      (column :userid)))))

(defmigration add-groups
  (up []
      (alter :add
             (table :links
                    (varchar :groupid 30 (default "0") :not-null))))
  (down [] 
        (alter :drop
               (table :links
                      (column :groupid)))))


(defmigration add-grouplinks-table
  (up [] 
      (create
       (table :grouplinks
              (integer :id :auto-inc :primary-key)
              (integer :head :not-null)
              (integer :tail :not-null)
              (integer :weight :not-null)
              (varchar :groupid 30 :not-null))))
  (down [] (drop (table :grouplinks))))