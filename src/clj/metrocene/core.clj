(ns metrocene.core
  (:use compojure.core)
  (:require [clojure.data.json :as json]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as resources]
            [ring.middleware.content-type :as content-type]
            [ring.util.response :as response]
            [clojure.core.matrix :as matrix]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.math.numeric-tower :as math]
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
           [{:id "a" :name "Greenhouse Gases"   :x 100 :y 100 :r 20 
             :colour :pos}
            {:id "b" :name "Climate Change"     :x 200 :y 200 :r 20 
             :colour :pos}
            {:id "c" :name "Economic Growth"    :x 300 :y 300 :r 20 
             :colour :neg}]
           :links
           [{:id "x" :weight 1 :tail 1 :head 2}
            {:id "y" :weight -2 :tail 2 :head 0}]})})

(defn post [{data :data}]
  (let [nodes (:nodes (edn/read-string data))
        links (:links (edn/read-string data))
        dim (count nodes)
        blank (matrix/matrix (repeat dim (repeat dim 0)))
        causes (reduce #(matrix/mset %1 (:tail %2) (:head %2)
                                     (:weight %2)) blank links)
        states (matrix/matrix (repeat dim (repeat 1 1)))
        squash (fn [out] (matrix/emap 
                          #(/ 1 
                              (inc (math/expt 
                                    Math/E 
                                    (unchecked-negate %)))) out))
        out (nth (iterate #(squash (matrix/add % (matrix/mul causes %))) 
                          states) 10)
        minusahalf (matrix/sub out 0.5)
        col-class #(if (< % 0) :neg :pos)
        newnodes (map #(assoc (nth nodes %) 
                         :colour (col-class (first (get minusahalf %))))
                      (range (count nodes)))]
    {:status 200
     :header {"Content-Type" "application/json"}
     :body (json/write-str {:nodes newnodes :links links})}))

(defroutes app
  (GET "/" [] (render-app))
  (GET "/json" [] (json))
  (POST "/json" {params :params} (post params))
  (route/resources "/" {:root "public"})
  (route/not-found "<h1>Page not found</h1>"))

(defn -main [port]
  (jetty/run-jetty (handler/site app) {:port (Integer. port) :join? false}))