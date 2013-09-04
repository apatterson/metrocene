(ns metrocene.graph
  (:require [strokes :refer [d3]]))

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

(defn post [data] 
  (-> d3 (.xhr "/json")
      (.header "Content-Type" "application/x-www-form-urlencoded")
      (.response #(.parse js/JSON (.-responseText %)))
      (.post data #(update (js->clj %2 :keywordize-keys true)))))

(defn update-links [nodes]
  (let [find-node (fn [coord posn]
                    (fn [d i] (coord (nth nodes (posn d)))))]
    (-> svg 
        (.selectAll "g.link line")
        (.attr {:x1 (find-node :x :tail)
                :x2 (find-node :x :head)
                :y1 (find-node :y :tail)
                :y2 (find-node :y :head)}))))

(defn update [{nodes :nodes links :links}]
  (let [weight (fn [l r] (if (or (= l r) (> (:y l) (:y r))) 
                           0 
                           (if (> (:x l) (:x r)) 
                             1 
                             -1)))
        node (-> svg (.selectAll "g.node")
                 (.data nodes #(:id %)))
        new-node (-> node 
                     .enter 
                     (.append "g"))
        link (-> svg (.selectAll "g.link")
                 (.data links #(:id %)))
        drag-move #(let [old-data (-> d3 (.select %) .data)]
                     (-> d3 
                         (.select %)
                         (.data [(merge (first old-data)
                                  {:x (-> d3 .-event .-x)
                                   :y (-> d3 .-event .-y)})])
                         (.attr {:transform (str "translate(" 
                                                 (-> d3 .-event .-x) ","
                                                 (-> d3 .-event .-y) ")")})))
        dragmove #(this-as 
                   this
                   (drag-move this)
                   (update-links (-> d3 (.selectAll "g.node") .data)))
        over #(let [old-data (-> d3 (.select %) .data)]
                (-> node 
                    (.each 
                     (fn [d i] (let [dx  (- (:x d) (-> d3 .-event .-x))
                                     dy  (- (:y d) (-> d3 .-event .-y))
                                     close (< (.sqrt js/Math
                                                     (+
                                                      (.pow js/Math dx 2)
                                                      (.pow js/Math dy 2)))
                                              20)]
                                 (when close
                                   (when (not= 
                                          (:new (first old-data)) 
                                          (:name d))
                                     (do
                                       (-> d3 (.select %) 
                                           (.data [(merge (first old-data)
                                                          {:old (:new (first old-data)) 
                                                           :new (:name d)})]))
                                       (.log js/console (:new (first old-data)))
                                       (.log js/console (:name d))))))))))
        drag-vote-move #(this-as 
                         this
                         (drag-move this)
                         (over this))
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
        drag (-> d3 
                 .-behavior 
                 .drag
                 (.on "drag" dragmove)
                 (.on "dragend" dragmoveend))
        dragvote (-> d3 
                     .-behavior 
                     .drag
                     (.on "drag" drag-vote-move))
        find-node (fn [coord posn]
                    (fn [d i] (coord (nth nodes (posn d)))))]
    (-> svg (.selectAll "use")
        (.data [{:id "plus" :old nil :new nil}])
        .enter
        (.append "use")
        (.attr "xlink:href" "#plus")
        (.attr "width" 30)
        (.attr "height" 30)
        (.call dragvote))
    (-> node 
        (.attr "class" #(str "node " (:colour %))))
    (-> new-node 
        (.call drag)
        (.attr "class" #(str "node " (:colour %)))
        (.attr {:transform #(str "translate(" 
                                 (:x %) "," 
                                 (:y %) ")")})
        (.append "circle")
        (.attr "r" 20))
    (-> new-node 
        (.append "text")
        (.text #(:name %)))
    (-> link
        (.attr "class" #(str "link " (:colour %))))
    (-> link
        .enter
        (.append "g")
        (.attr "class" "link")
        (.append "line")
        (.attr {:x1 (find-node :x :tail)
                :x2 (find-node :x :head)
                :y1 (find-node :y :tail)
                :y2 (find-node :y :head)}))))

(-> d3 (.json "/json" #(update (js->clj %1 :keywordize-keys true))))