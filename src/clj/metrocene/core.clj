(ns metrocene.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as resources]
            [ring.middleware.content-type :as content-type]
            [ring.util.response :as response]
            [clojure.java.io :as io]))

(defn render-app []
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf8"}
   :body (slurp (io/resource "venn.html"))})

(defn handler [request]
  (if (= "/" (:uri request))
      (response/redirect "/help.html")
      (render-app)))

(def app 
  (-> handler
    (resources/wrap-resource "public")
    content-type/wrap-content-type))

(defn -main [port]
  (jetty/run-jetty app {:port (Integer. port) :join? false}))