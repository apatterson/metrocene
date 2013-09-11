(ns metrocene.graph
  (:require [strokes :refer [d3]]
            [cljs.core.async :as async :refer [chan <! >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(strokes/bootstrap)

(def svg (-> d3 
             (.select "body") 
             (.append "svg")
             (.attr {:width 900 :height 900})))

(defn make-marker [parent id]
  (-> parent
      (.append "svg:marker")
      (.attr "id" id)
      (.attr "viewBox" "0 0 10 10")
      (.attr "refX" 15)
      (.attr "refY" 3)
      (.attr "markerUnits"  "strokeWidth")
      (.attr "markerWidth", 10)
      (.attr "markerHeight", 10)
      (.attr "orient", "auto")
      (.append "svg:path")
      (.attr "d", "M0,0L10,3L0,6")))

(make-marker svg "marker")
(make-marker svg "neg-marker")

(defn make-symbol [parent id]
  (-> parent
      (.append "svg:symbol")
      (.attr "id" id)
      (.attr "viewBox" "0 0 3 3")
      (.append "svg:path")
      (.attr "d", "M1,0L2,0L2,1L3,1L3,2L2,2L2,3L1,3L1,2L0,2L0,1L1,1")))

(make-symbol svg "plus")

(def data-chan (chan))

(defn- drag-move [nodes e i]
  (go 
   (>! data-chan 
       {:nodes (-> (assoc-in nodes [i :x] (.-x e))
                   (assoc-in [i :y] (.-y e)))})))

(defn update [data]
  (go
   (loop [d (merge data {:state :waiting})]
     (let [{nodes :nodes links :links state :state tail :tail} d
           post (fn [data] 
                  (-> d3 (.xhr "/json")
                      (.header "Content-Type" 
                               "application/x-www-form-urlencoded")
                      (.response #(.parse js/JSON (.-responseText %)))
                      (.post data #(go 
                                    (>! data-chan 
                                        (js->clj %2 :keywordize-keys true))))))
           weight (fn [l r] (if (or (= l r) (> (:y l) (:y r))) 
                              0 
                              (if (> (:x l) (:x r)) 
                                1 
                                -1)))
           link (-> svg (.selectAll "g.link")
                    (.data links #(:id %)))
           new-link (-> link 
                        .enter 
                        (.append "g")                 
                        (.attr "class" #(str "link " (:colour %))))
           line (-> new-link
                    (.append "line"))
           dragmove #(drag-move nodes (.-event d3) %2)
           node (-> svg (.selectAll "g.node")
                    (.data nodes #(:id %)))
           over (fn 
                  [e this nodes links state tail]
                  (-> node 
                      (.each 
                       (fn [d i] (let [dx  (- (:x d) (.-x e))
                                       dy  (- (:y d) (.-y e))
                                       close (< (.sqrt js/Math
                                                       (+
                                                        (.pow js/Math dx 2)
                                                        (.pow js/Math dy 2)))
                                                20)]
                                   (when (and close (not= tail (:id d)))
                                     (when tail
                                       (go (>! data-chan 
                                               {:nodes nodes
                                                :links (conj links 
                                                             {:id (str (:id d) 
                                                                       tail)
                                                              :tail tail
                                                              :head i
                                                              :weight 1
                                                              :colour "pos"})
                                                :state :connected}))
                                       (go (>! data-chan {:tail (:id d)
                                                          :state :connecting})))))))))
           drag-vote-move #(this-as 
                            this
                            (let [e (-> d3 .-event)]
                              (over e this nodes links state tail)
                              (drag-move nodes e %2)))
           dragmoveend #(post  
                         (str "data="
                              {:nodes 
                               (map identity 
                                    (-> svg (.selectAll "g.node") 
                                        .data))
                               :links 
                               (map identity 
                                    (-> svg (.selectAll "g.link") 
                                        .data))}))
           dragstart #(go (>! data-chan {:state :waiting}))
           dragnode (-> d3 
                        .-behavior 
                        .drag
                        (.on "drag" dragmove)
                        (.on "dragend" dragmoveend))
           dragvote (-> d3 
                        .-behavior 
                        .drag
                        (.on "dragstart" dragstart)
                        (.on "drag" drag-vote-move))
           new-node (-> node 
                        .enter 
                        (.append "g"))
           find-node (fn [coord posn]
                       (fn [d i] (coord (nth nodes (posn d)))))]
       (-> svg (.selectAll "use")
           (.data [{:id "plus" :done false :new nil}])
           .enter
           (.append "use")
           (.call dragvote)
           (.attr "xlink:href" "#plus")
           (.attr "width" 30)
           (.attr "height" 30))
       (-> svg
           (.attr "class" (name state)))
       (-> node 
           (.call dragnode)
           (.attr "class" #(str "node " (:colour %)))
           (.attr {:transform #(str "translate(" 
                                    (:x %) "," 
                                    (:y %) ")")}))
       (-> new-node
           (.append "circle")
           (.attr "r" 20))
       (-> new-node 
           (.append "text")
           (.text #(:name %)))
       (-> svg (.selectAll ".link line")
           (.attr {:x1 (find-node :x :tail)
                   :x2 (find-node :x :head)
                   :y1 (find-node :y :tail)
                   :y2 (find-node :y :head)}))
       (recur (merge d (<! data-chan)))))))

(-> d3 (.json "/json" #(update (js->clj %1 :keywordize-keys true))))

