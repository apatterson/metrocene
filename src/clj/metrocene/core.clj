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
            [clojure.java.jdbc :as sql]
            [friend-oauth2.workflow :as oauth2]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [clj-facebook-graph.client :as fb-client]
            [clj-facebook-graph.auth :as fb-auth]
            [net.cgrand.enlive-html :as enlive]))
  
(enlive/deftemplate index "venn.html" [{session :session}]
  [:#login]
  (enlive/content 
   (if (-> session :cemerick.friend/identity)
     (str 
      "You are logged in as "
      (:name 
       (fb-auth/with-facebook-auth 
         {:access-token (-> session :cemerick.friend/identity 
                            :authentications first val :access_token)}
         (fb-client/get [:me] {:extract :body}))))
     (enlive/html [:a {:href "/login"} "Please login"]))))

(defn json [req]
  (let [data (if-let [d (-> req :session :data)]
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
    (if (-> session :cemerick.friend/identity)
      (println
       (fb-auth/with-facebook-auth 
         {:access-token (-> session :cemerick.friend/identity 
                            :authentications first val :access_token)}
         (fb-client/get [:me] {:extract :body}))))
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

;; OAuth2 config
(defn access-token-parsefn
  [response]
  (-> response
      :body
      ring.util.codec/form-decode
      clojure.walk/keywordize-keys
      :access_token))

(def config-auth {:roles #{::user}})

(def client-config
  {:client-id (System/getenv "FACEBOOK_APP_ID")
   :client-secret (System/getenv "FACEBOOK_SECRET")
   :callback {:domain (System/getenv "FACEBOOK_CALLBACK") 
              :path "/facebook.callback"}})

(def uri-config
  {:authentication-uri {:url "https://www.facebook.com/dialog/oauth"
                        :query {:client_id (:client-id client-config)
                                :redirect_uri (oauth2/format-config-uri 
                                               client-config)}}
   
   :access-token-uri {:url "https://graph.facebook.com/oauth/access_token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :redirect_uri (oauth2/format-config-uri 
                                             client-config)
                              :code ""}}})

(defroutes app
  (GET "/login" [] (friend/authorize #{::user} {:status 200}))
  (GET "/" request (index request))
  (GET "/json" request (json request))
  (POST "/json" request (post request))
  (GET "/user" request
       (friend/authorize 
        #{::user} 
        {:status 200
         :headers {"Content-Type" "text"}
         :body (fb-client/get [:me] {:extract :body})}))
  (GET "/authlink2" request
       (friend/authorize #{::user} "Authorized page 2."))
  (GET "/admin" request
       (friend/authorize #{::admin} "Only admins can see this page."))
  #_(ANY "/logout" request (update-in (ring.util.response/redirect "/login") [:session] dissoc ::identity))
  (friend/logout (ANY "/logout" request (ring.util.response/redirect "/login")))
  (route/resources "/" {:root "public"})
  (route/not-found "<h1>Page not found</h1>"))

(def handler 
  (handler/site 
   (friend/authenticate
    app
    {:allow-anon? true
     :workflows [(oauth2/workflow
                  {:client-config client-config
                   :uri-config uri-config
                   :access-token-parsefn access-token-parsefn
                   :config-auth config-auth})]})))

(defn -main [port]
  (jetty/run-jetty 
   handler
   {:port (Integer. port) :join? false}))