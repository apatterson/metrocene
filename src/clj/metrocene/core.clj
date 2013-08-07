(ns metrocene.core
  (:use compojure.core)
  (:require [clojure.data.json :as json]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as resources]
            [ring.middleware.content-type :as content-type]
            [ring.util.response :as response]
            [clojure.java.io :as io]
            [compojure.route :as route]))

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

(defn handler [request]
  (if (= "/" (:uri request))
      (response/redirect "/help.html")
      (render-app)))

(defroutes app
  (GET "/" [] (render-app))
  (GET "/json" [] (json))
  (route/resources "/" {:root "public"})
  (route/not-found "<h1>Page not found</h1>"))

#_(def app 
  (-> handler
    (resources/wrap-resource "public")
    content-type/wrap-content-type))

(defn -main [port]
  (jetty/run-jetty app {:port (Integer. port) :join? false}))