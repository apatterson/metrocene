(ns metrocene.graph
  (:require [strokes :refer [d3]]
            [cljs.core.async :as async :refer [chan <! >!]]
            [vomnibus.color-brewer :as color-brewer]
            [c2.scale :as scale])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(strokes/bootstrap)

(def svg (-> d3 
             (.selectAll "svg #layer1")))

(defn make-arrow 
  "Given a start point and end point corresponding to the mid points of two 
circles, return a set of points defining an arrow from the edges of the circles"
  [d i xtm ytm xhm yhm]
  (let [alpha (if (= xtm xhm) 
                0 
                (.atan js/Math (/ (- ytm yhm)
                                  (- xtm xhm))))
        ltrgt (if (< xtm xhm) - +)
        l 15
        offset-x (fn [x da] 
                   (ltrgt x 
                          (* l 
                             (.cos js/Math (+ alpha da)))))
        offset-y (fn [y da] 
                   (ltrgt y 
                          (* l 
                             (.sin js/Math (+ alpha da)))))
        xhe (offset-x xhm 0)
        yhe (offset-y yhm 0)]
    [xtm ytm xhe yhe 
     (offset-x xhe .3) 
     (offset-y yhe .3) 
     xhe yhe
     (offset-x xhe -0.3) 
     (offset-y yhe -0.3)]))

(def data-chan (chan))
(def drag-chan (chan))
(def state-chan (chan))
(def init-chan (chan))
(def votes-chan (chan))

(defn- drag-move [nodes e i]
  (go 
   (>! data-chan 
       {:nodes (-> (assoc-in nodes [i :x] (.-x e))
                   (assoc-in [i :y] (.-y e)))})))

(go
 (while true
   (let [votes (<! votes-chan) 
         vote (-> svg (.selectAll "#votes")
                   (.data [votes])) 
         new-vote (-> vote 
                      .enter
                      (.append "g")
                      (.attr {:transform "translate(50,300)"
                              :id "votes"}))
         vote-count-new (-> new-vote
                        (.append "text"))
         vote-count (-> vote (.selectAll "text")
                        (.text votes))
         lucre (-> new-vote 
                   (.append "image")
                   (.attr {:xlink:href "/img/votes.png"
                           :width 30
                           :height 30
                           :id "votes"}))])))

(go
 (loop [tail nil target nil state nil votes (<! init-chan)]
   (let [v (>! votes-chan votes)
         {e :event node :node end-state :state weight :weight this :this} 
         (<! drag-chan)
         end -1
         [nt-datum x new-target] 
         (if (and e node)
           (reduce 
            (fn [[t i j] d] (let [dx  (- (:x d) (.-x e))
                                  dy  (- (:y d) (.-y e))
                                  close (< (.sqrt js/Math
                                                  (+
                                                   (.pow js/Math dx 2)
                                                   (.pow js/Math dy 2)))
                                           20)]
                              [(if close d t) (inc i) (if close i j)]))
            [nil 0 -1]
            (-> node 
                .data)) 
           [tail end end])]
     (if end-state (do
                     (>! data-chan {:state end-state
                                    :tail nil
                                    :head nil})
                     (recur end end end-state votes)))
     (if (= state :connecting)
       (-> svg (.selectAll "polyline.connect")
           (.attr {:points #(this-as this
                                     (make-arrow %1 
                                                 %2 
                                                 (-> this .-points 
                                                     (.getItem 0) .-x)
                                                 (-> this .-points
                                                     (.getItem 0) .-y)
                                                 (.-x e)
                                                 (.-y e)))
                   :stroke #(if (< % 0) "red" "blue")})))
     (if (and (= state :seeking)
              (> votes 0))
       (do (-> d3 (.select this)
               (.style "opacity" 0.3)
               (.attr {:x (.-x e)
                       :y (.-y e)})))
       (-> d3 (.select this)
           (.style "opacity" 1)))
     (when-not (= target new-target) ;;target changed
       (cond (and (= tail end) (= state :seeking) (> votes 0))
             (do
               (-> svg (.selectAll "polyline.connect")
                   (.data [1])
                   .enter
                   (.append "polyline")
                   (.attr {:class "connect"
                           :fill "none"})
                   (.attr {:points #(make-arrow %1 
                                                %2 
                                                (:x nt-datum)
                                                (:y nt-datum)
                                                (.-x e)
                                                (.-y e))}))
               (>! data-chan {:state :connecting
                              :tail new-target
                              :head end})
               (recur new-target new-target :connecting votes))
             (and (> tail end) (= state :connecting) (> new-target end))
             (do
               (-> svg (.selectAll "polyline.connect")
                   (.data [])
                   .exit
                   .remove)
               (>! data-chan {:state :connected
                              :tail tail
                              :head new-target
                              :weight weight
                              :votes (max 0 (dec votes))})
               (recur end end :connected votes))
             (= state :connected) 
             (do
               (>! data-chan {:state :done})
               (recur end end :done (max 0 (dec votes))))))
     (recur tail new-target (if end-state end-state state) votes))))

(go
 (loop [d {:state :start}]
   (let [new-data (merge d (<! data-chan))
         {nodes :nodes 
          links :links 
          state :state 
          tail :tail
          head :head
          votes :votes
          weight :weight} new-data
         post (fn [data] 
                (-> d3 (.xhr "/json")
                    (.header "Content-Type" 
                             "application/x-www-form-urlencoded")
                    (.response #(.parse js/JSON (.-responseText %)))
                    (.post 
                     (str "data=" data) 
                     #(go 
                       (>! data-chan 
                           (js->clj %2 :keywordize-keys true))))))
         links-key (fn [d i] (str (:tail d) "-" (:head d)))
         link (-> svg (.selectAll "g.link")
                  (.data links links-key))
         new-link (-> link 
                      .enter
                      (.append "g")
                      (.attr "class" #(str "link " 
                                           (if (< (:weight %) 0) "neg" "pos"))))
         line (-> new-link
                  (.append "polyline"))
         changes (->
                  (if (= state :connected)
                    (assoc-in 
                     new-data 
                     [:links] 
                     (map 
                      second
                      (merge-with
                       #(merge %1 
                               {:weight 
                                (+ (:weight %1) (:weight %2))})
                       (into {} (map #(vector (links-key % %2) %) links)) ;;make map
                       {(links-key {:tail tail :head head} 0)
                        {:weight weight
                         :tail tail
                         :head head}})))
                    new-data)
                  (assoc-in [:state] state)
                  (assoc-in [:votes] votes))
         p (.log js/console (:votes changes) state)
         dragmove #(drag-move nodes (.-event d3) %2)
         node (-> svg (.selectAll "g.node")
                  (.data nodes #(:id %)))
         drag-vote-move #(this-as 
                          this
                          (let [e (-> d3 .-event)]
                            (go (>! drag-chan {:event e 
                                               :node node
                                               :weight %
                                               :this this}))))
         dragmoveend #(post
                       {:nodes 
                        (map identity 
                             (-> svg (.selectAll "g.node") 
                                 .data))
                        :links 
                        (map identity 
                             (-> svg (.selectAll "g.link") 
                                 .data))})
         dragstart #(go (>! drag-chan {:state :seeking}))
         drag-vote-end #(go (>! drag-chan {:state :waiting}))
         dragnode (-> d3 
                      .-behavior 
                      .drag
                      (.on "drag" dragmove)
                      (.on "dragend" dragmoveend))
         dragvote (-> d3 
                      .-behavior 
                      .drag
                      (.on "dragstart" dragstart)
                      (.on "drag" drag-vote-move)
                      (.on "dragend" drag-vote-end))
         new-node (-> node 
                      .enter 
                      (.append "g"))
         radius 20]
     (if (= state :connected) 
       (do
         (-> svg (.select "#plus")
             (.attr {:x 50
                     :y 100}))
         (-> svg (.select "#minus")
             (.attr {:x 50
                     :y 200}))
         (post changes)))
     (-> svg (.selectAll "g.link")
         (.attr "class" #(str "link " 
                              (if (< (:weight %) 0) "neg" "pos"))))
     (-> svg (.selectAll "#plus")
         (.data [1])
         .enter
         (.append "image")
         (.attr {:xlink:href "/img/up.svg"
                 :x 50
                 :y 100
                 :width 30
                 :height 30
                 :id "plus"
                 :class "vote"})
         (.call dragvote)
         (.append "title") 
         (.text "Drag from here to make a positive link"))
     (-> svg (.selectAll "#minus")
         (.data [-1])
         .enter
         (.append "image")
         (.attr {:xlink:href "/img/down.svg"
                 :x 50
                 :y 200
                 :width 30
                 :height 30
                 :id "minus"
                 :class "vote"})         
         (.call dragvote)
         (.append "title") 
         (.text "Drag from here to make a negative link"))
     (-> node 
         (.call dragnode)
         (.attr {:class "node"
                 :transform #(str "translate(" 
                                  (:x %) "," 
                                  (:y %) ")")})        
         (.style "fill" #(:colour %)))
     (-> new-node
         (.append "circle")
         (.attr "r" radius))
     (-> new-node 
         (.append "text")
         (.attr {:x radius :y radius})
         (.text #(:name %)))
     (-> svg 
         (.attr "class" (name state)))
     (-> svg (.selectAll ".link polyline")
         (.attr {:points (fn [d i] 
                           (make-arrow d i
                                       (:x (nth nodes (:tail d)))
                                       (:y (nth nodes (:tail d)))
                                       (:x (nth nodes (:head d)))
                                       (:y (nth nodes (:head d)))))})
         (.attr "fill" "none")
         (.style "stroke"
                 #(let [colour-scheme color-brewer/RdYlBu-11 
                        colour-scale
                        (let [max-votes 10
                              min-votes (unchecked-negate max-votes)
                              scale (scale/linear
                                     :domain [(dec min-votes)
                                              (inc max-votes)]
                                     :range [0 (count colour-scheme)])]
                          (fn [wt](nth colour-scheme 
                                       (.floor js/Math 
                                               (scale
                                                (-> wt
                                                    (min max-votes)
                                                    (max min-votes)))))))]
                    (colour-scale (:weight %)))))
     (recur changes))))

(-> d3 
    (.json "/json" 
           #(go 
             (let [d  (js->clj %1 :keywordize-keys true)]
               (>! init-chan (:votes d))
               (>! data-chan d)))))

