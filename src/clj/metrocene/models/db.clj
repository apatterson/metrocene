(ns metrocene.models.db
  (:use [lobos.core :only (defcommand migrate)])
  (:require [lobos.migration :as lm]
        [clojure.string :as str])
  (:import (java.net URI)))

(defn heroku-db
  "Generate the db map according to Heroku environment when available."
  []
  (when (System/getenv "HEROKU_POSTGRESQL_MAROON_URL")
    (let [url (URI. (System/getenv "HEROKU_POSTGRESQL_MAROON_URL"))
          host (.getHost url)
          port (if (pos? (.getPort url)) (.getPort url) 5432)
          path (.getPath url)]
      (merge
       {:subname (str "//" host ":" port path)}
       (when-let [user-info (.getUserInfo url)]
         {:user (first (str/split user-info #":"))
          :password (second (str/split user-info #":"))})))))

(def db
  (merge {:classname "org.postgresql.Driver"
          :subprotocol "postgresql"
          :subname "//localhost:5432/metrocene"}
         (heroku-db)))


(defcommand pending-migrations []
  (binding [lobos.migration/*src-directory* "src/clj"]
    (lm/pending-migrations db sname)))

(defn actualized?
  "checks if there are no pending migrations"
  []
  (empty? (pending-migrations)))

(defn actualize []
  (binding [lobos.migration/*src-directory* "src/clj"]
    (migrate)))
