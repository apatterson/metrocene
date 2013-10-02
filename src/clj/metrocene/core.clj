(ns metrocene.core
  (:use compojure.core
        metrocene.models.db)
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
            [compojure.handler :as handler]
            [c2.scale :as scale]
            [vomnibus.color-brewer :as color-brewer]
            [clojure.math.numeric-tower :as math]
            [clojure.java.jdbc :as sql]))

(defn render-app []
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf8"}
   :body (slurp (io/resource "venn.html"))})

(defn json [request]
  (let [data (if-let [d (-> request :session :data)]
               d
               {:nodes
                (sql/with-connection 
                  db
                  (sql/with-query-results results
                    ["select * from nodes"]
                    (into [] results)))
                  :links
                (sql/with-connection 
                  db
                  (sql/with-query-results results
                    ["select * from links"]
                    (into [] results)))})]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str data)}))

(defn post [{{data :data} :params session :session}]
  (let [nodes (:nodes (edn/read-string data))
        links (:links (edn/read-string data))
        dim (count nodes)
        blank (matrix/matrix (repeat dim (repeat dim 0)))
        causes (reduce #(matrix/mset %1 (:head %2) (:tail %2)
                                     (:weight %2)) blank links)
        states (matrix/matrix (repeat dim (repeat 1 1)))
        gain 0.1
        squash (fn [out] (matrix/emap 
                          #(/ 1 
                              (inc (math/expt 
                                    Math/E 
                                    (unchecked-negate (* gain %))))) out))
        out (nth (iterate #(squash (matrix/add % (matrix/mul causes %))) 
                          states) 10)
        minusahalf (matrix/sub out 0.5)
        col-class #(let [colour-scheme color-brewer/RdYlBu-11 
                         colour-scale
                         (let [s (scale/linear 
                                  :domain [-1 1]
                                  :range [0 (count colour-scheme)])]
                           (fn [d] (nth colour-scheme (math/floor (s d)))))]
                     (colour-scale %))
        newnodes (map #(assoc (nth nodes %) 
                         :colour (col-class (first (get minusahalf %))))
                      (range (count nodes)))
        links-key (fn [d] (str (:tail d) "-" (:head d)))
        array->map (fn [m] (into {} (map #(vector (links-key %) %) m)))
        links-map (array->map links)
        old-links (if-let [l (-> session :data :links)]
                    l [])
        diff-links (merge-with #(merge %1 {:weight (- (:weight %2) 
                                                      (:weight %1))})
                               (array->map old-links) 
                               links-map)]
    (sql/with-connection db
      (doseq [{tail :tail head :head weight :weight}
              (vals
               (merge-with
                #(merge %1 {:weight (+ (:weight %2) 
                                       (:weight %1))})
                (sql/with-query-results results
                  ["select * from links"]
                  (into {} (array->map results)))
                diff-links))]
        (sql/update-or-insert-values
         :links
         ["tail=? AND head=?" tail head]
         {:tail tail :head head :weight weight}))
      (doseq [node newnodes]
        (sql/update-or-insert-values
         :nodes
         ["id=?" (:id node)]
         node)))
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str {:nodes newnodes 
                            :links (map #(assoc % 
                                           :colour (if (< (:weight %) 0)
                                                     :neg :pos)) 
                                        links)})
     :session {:data {:nodes newnodes :links links}}}))

(defroutes app
  (GET "/" [] (render-app))
  (GET "/json" request (json request))
  (POST "/json" request (post request))
  (route/resources "/" {:root "public"})
  (route/not-found "<h1>Page not found</h1>"))

(defn -main [port]
  (jetty/run-jetty (handler/site app) {:port (Integer. port) :join? false}))