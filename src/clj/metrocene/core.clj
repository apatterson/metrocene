(ns metrocene.core
  (:use compojure.core)
  (:require [clojure.data.json :as json]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as resources]
            [ring.middleware.content-type :as content-type]
            [ring.util.response :as response]
            [clatrix.core :as clatrix]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [compojure.route :as route]
            [compojure.handler :as handler]))

(defn render-app []
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf8"}
   :body (slurp (io/resource "venn.html"))})

(defn json []
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str
          {:nodes 
           [{:id "a" :name "Greenhouse Gases"   :x 100 :y 100 :r 20}
            {:id "b" :name "Climate Change"     :x 200 :y 200 :r 20}
            {:id "c" :name "Economic Growth"    :x 300 :y 300 :r 20}]
           :links
           [{:id "x" :weight 1 :tail 1 :head 2}]})})

(defn post [{data :data}]
  (let [nodes (:nodes (edn/read-string data))
        links (:links (edn/read-string data))
        p (println links)
        dim (count nodes)
        blank (clatrix/matrix (repeat dim (repeat dim 0)))
        matrix (reduce #(clatrix/slice %1 (:tail %2) (:head %2)
                                       (:weight %2)) blank links)]
    (println matrix)
    {:status 200}))

(defroutes app
  (GET "/" [] (render-app))
  (GET "/json" [] (json))
  (POST "/json" {params :params} (post params))
  (route/resources "/" {:root "public"})
  (route/not-found "<h1>Page not found</h1>"))

(defn -main [port]
  (jetty/run-jetty (handler/site app) {:port (Integer. port) :join? false}))