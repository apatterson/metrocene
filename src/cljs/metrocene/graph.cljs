(ns metrocene.graph
  (:require [strokes :refer [d3]]
            [cljs.core.async :as async :refer [chan <! >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(strokes/bootstrap)

(def svg (-> d3 
             (.select "#metrocene") 
             (.append "svg")
             (.attr {:width 900 :height 600})))

(defn make-marker [parent id]
  (-> parent
      (.append "svg:marker")
      (.attr "id" id)
      (.attr "viewBox" "0 0 10 10")
      (.attr "refX" 30)
      (.attr "refY" 3)
      (.attr "markerUnits"  "strokeWidth")
      (.attr "markerWidth", 10)
      (.attr "markerHeight", 10)
      (.attr "orient", "auto")
      (.append "svg:path")
      (.attr "d", "M0,0L10,3L0,6")))

(make-marker svg "marker")
(make-marker svg "neg-marker")

(-> svg
    (.append "svg:symbol")
    (.attr "id" "plus")
    (.attr "viewBox" "0 0 3 3")
    (.append "svg:path")
    (.attr "d", "M1,0L2,0L2,1L3,1L3,2L2,2L2,3L1,3L1,2L0,2L0,1L1,1"))

(-> svg
    (.append "svg:symbol")
    (.attr "id" "minus")
    (.attr "viewBox" "0 0 3 3")
    (.append "svg:path")
    (.attr "d", "M0,1L3,1L3,2L0,2"))



(def data-chan (chan))
(def drag-chan (chan))
(def state-chan (chan))

(defn- drag-move [nodes e i]
  (go 
   (>! data-chan 
       {:nodes (-> (assoc-in nodes [i :x] (.-x e))
                   (assoc-in [i :y] (.-y e)))})))

(go
 (loop [tail nil target nil state nil]
   (let [{e :event node :node end-state :state weight :weight this :this} 
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
                     (recur end end end-state)))
     (if (= state :connecting)
       (-> svg (.selectAll "line.connect")
           (.attr {:x2 (.-x e)
                   :y2 (.-y e)})))
     (if (= state :seeking)
       (-> d3 (.select this)
           (.style "opacity" 0.3))
       (-> d3 (.select this)
           (.style "opacity" 1)))
     (when-not (= target new-target) ;;target changed
       (cond (and (= tail end) (= state :seeking))
             (do
               (-> svg (.selectAll "line.connect")
                   (.data [1])
                   .enter
                   (.append "line")
                   (.attr {:class "connect"})
                   (.attr {:x1 (:x nt-datum)
                           :y1 (:y nt-datum)
                           :x2 (.-x e)
                           :y2 (.-y e)}))
               (>! data-chan {:state :connecting
                              :tail new-target
                              :head end})
               (recur new-target new-target :connecting))
             (and (> tail end) (= state :connecting) (> new-target end))
             (do
               (-> svg (.selectAll "line.connect")
                   (.data [])
                   .exit
                   .remove)
               (>! data-chan {:state :connected
                              :tail tail
                              :head new-target
                              :weight weight})
               (recur end end :connected))
             (= state :connected) 
             (do
               (>! data-chan {:state :done})
               (recur end end :done))))
     (recur tail new-target (if end-state end-state state)))))

(go
 (loop [d {:state :start}]
   (let [new-data (merge d (<! data-chan))
         {nodes :nodes 
          links :links 
          state :state 
          tail :tail
          head :head
          weight :weight} new-data
         post (fn [data] 
                (-> d3 (.xhr "http://localhost:5000/json")
                    (.header "Content-Type" 
                             "application/x-www-form-urlencoded")
                    (.response #(.parse js/JSON (.-responseText %)))
                    (.post 
                     (str "data=" data) 
                     #(go 
                       (>! data-chan 
                           (js->clj %2 :keywordize-keys true))))))
         link (-> svg (.selectAll "g.link")
                  (.data links #(:id %)))
         new-link (-> link 
                      .enter
                      (.append "g")
                      (.attr "class" #(str "link " 
                                           (if (< (:weight %) 0) "neg" "pos"))))
         line (-> new-link
                  (.append "line"))
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
                                (let [w (+ (:weight %1) (:weight %2))]
                                  (cond (< w -1) -1
                                        (> w 1) 1
                                        :else w))})
                       (into {} (map #(vector (:id %) %) links)) ;;make map
                       {(str tail "." head)
                        {:id (str tail "." head)
                         :weight weight
                         :tail tail
                         :head head}})))
                    new-data)
                  (assoc-in [:state] state))
         p (.log js/console state (:state changes))
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
         find-node (fn [coord posn]
                     (fn [d i] (coord (nth nodes (posn d)))))]
     (if (= state :connected) (post changes))
     (-> svg (.selectAll "g.link")
         (.attr "class" #(str "link " 
                              (if (< (:weight %) 0) "neg" "pos"))))
     (-> svg (.selectAll "use.plus")
         (.data [0.2])
         .enter
         (.append "use")
         (.call dragvote)
         (.attr {:xlink:href "#plus"
                 :x 50
                 :y 100
                 :width 30
                 :height 30
                 :class "plus"}))
     (-> svg (.selectAll "use.minus")
         (.data [-0.2])
         .enter
         (.append "use")
         (.call dragvote)
         (.attr {:xlink:href "#minus"
                 :x 50
                 :y 200
                 :width 30
                 :height 30
                 :class "minus"}))
     (-> node 
         (.call dragnode)
         (.attr {:class "node"
                 :transform #(str "translate(" 
                                  (:x %) "," 
                                  (:y %) ")")})        
         (.style "fill" #(:colour %)))
     (-> new-node
         (.append "circle")
         (.attr "r" 20))
     (-> new-node 
         (.append "text")
         (.attr {:x 20 :y -20})
         (.text #(:name %)))
     (-> svg 
         (.attr "class" (name state)))
     (-> svg (.selectAll ".link line")
         (.data links)
         (.attr {:x1 (find-node :x :tail)
                 :x2 (find-node :x :head)
                 :y1 (find-node :y :tail)
                 :y2 (find-node :y :head)})                      
         (.style "stroke-width" #(* 3 (.abs js/Math (:weight %)))))
     (.log js/console links)
     (recur changes))))

(-> d3 (.json "http://localhost:5000/json" #(go (>! data-chan (js->clj %1 :keywordize-keys true)))))

