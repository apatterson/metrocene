(ns metrocene.graph
  (:require [strokes :refer [d3]]))

(strokes/bootstrap)

(def svg (-> d3 (.select "body") (.append "svg")))

(-> svg
    (.append "svg:marker")
    (.attr "id" "marker")
    (.attr "viewBox" "0 0 10 10")
    (.attr "refX" 15)
    (.attr "refY" 3)
    (.attr "markerUnits"  "strokeWidth")
    (.attr "markerWidth", 10)
    (.attr "markerHeight", 10)
    (.attr "orient", "auto")
    (.append "svg:path")
    (.attr "d", "M0,0L10,3L0,6"))

(defn update [data]
  (let [nodes (map (fn [n] {:id (.-id n) :x (.-x n) :y (.-y n)}) (.-nodes data))
        links (map (fn [n] {:head 0 :tail 1}) (.-links data))
        l (.log js/console nodes)
        weight (fn [l r] (if (or (= l r) (> (:y l) (:y r))) 
                           0 
                           (if (> (:x l) (:x r)) 
                             1 
                             -1)))
        node (-> svg (.selectAll "g.node")
                 (.data nodes #(:id %))
                 (.enter)
                 (.append "g")
                 (.attr "class" "node" ))
        circle (-> node
                   (.append "circle")
                   (.attr "r" 20))
        text (-> node
                 (.append "text")
                 (.text #(:name %1)))
        dragmove #(this-as 
                   this
                   (-> d3 
                       (.select this)
                       (.data [{:id (fn [d i] (:id d))
                                :x (-> d3 .-event .-x)
                                :y (-> d3 .-event .-y)}])
                       (.attr {:transform (str "translate(" 
                                               (-> d3 .-event .-x) ","
                                               (-> d3 .-event .-y) ")")}))
                   (update-links (-> svg (.selectAll "g.node") .data) links))
        dragmoveend #(-> node
                         (.select "circle")
                         (.attr {:r (fn [d i] 
                                      (-> (reduce (fn [acc c] 
                                                    (+ acc (weight c d)))
                                                  0
                                                  (.data node))
                                          (* 10)
                                          (+ 30)))}))
        drag (-> d3 
                 .-behavior 
                 .drag
                 (.on "drag" dragmove)
                 (.on "dragend" dragmoveend))
        
        link (-> svg (.selectAll "g.link")
                 (.data links :id)
                 (.enter)
                 (.append "g")
                 (.attr "class" "link" )
                 (.attr "id" #(:id %)))
        find-node (fn [coord posn] 
                    #(coord (nth data (posn %))))
        line (-> link
                 (.append "line")
                 (.attr "marker-end" "url(#marker)"))]
    (-> node 
        (.attr {:transform #(str "translate(" (:x %) "," (:y %) ")")})
        (.call drag))))

(defn update-links [nodes links]
  (let [find-node (fn [coord posn] 
                    (fn [d i] (coord (nth nodes (posn d)))))
        p (.log js/console nodes)]
    (-> svg 
        (.selectAll "g.link line")
        (.attr {:x1 (find-node :x :tail)
                :x2 (find-node :x :head)
                :y1 (find-node :y :tail)
                :y2 (find-node :y :head)}))))

(-> d3 (.json "/json" #(update %1)))
