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

(defn post [data] 
  (-> d3 (.xhr "/json")
      (.header "Content-Type" "application/x-www-form-urlencoded")
      (.response #(.parse js/JSON (.-responseText %)))
      (.post data #(update %2))))

(defn update-links [nodes]
  (let [find-node (fn [coord posn] 
                    (fn [d i] (coord (nth nodes (posn d)))))]
    (-> svg 
        (.selectAll "g.link line")
        (.attr {:x1 (find-node :x :tail)
                :x2 (find-node :x :head)
                :y1 (find-node :y :tail)
                :y2 (find-node :y :head)}))))

(defn update [data]
  (let [l (.log js/console data)
        nodes (map (fn [n] {:id (.-id n) :x (.-x n) :y (.-y n) 
                            :colour (.-colour n) :name (.-name n)}) 
                   (.-nodes data))
        links (map (fn [n] {:head (.-head n) :tail (.-tail n) 
                            :weight (.-weight n) :id (.-id n)}) 
                   (.-links data))
        weight (fn [l r] (if (or (= l r) (> (:y l) (:y r))) 
                           0 
                           (if (> (:x l) (:x r)) 
                             1 
                             -1)))
        node (-> svg (.selectAll "g.node")
                 (.data nodes #(:id %)))
        node-enter (-> node
                       .enter
                       (.append "g")
                       (.attr "class" "node")
                       (.attr {:transform #(str "translate(" 
                                                (:x %) "," 
                                                (:y %) ")")}))
        circle (-> node
                   (.append "circle")
                   (.attr "r" 20))
        text (-> node
                 (.append "text")
                 (.text #(:name %1)))
        dragmove #(this-as 
                   this
                   (let [old-data (-> d3 (.select this) .data)]
                     (-> d3 
                         (.select this)
                         (.data [{:id (:id (first old-data))
                                  :x (-> d3 .-event .-x)
                                  :y (-> d3 .-event .-y)}])
                         (.attr {:transform (str "translate(" 
                                                 (-> d3 .-event .-x) ","
                                                 (-> d3 .-event .-y) ")")})))
                   (update-links (-> svg (.selectAll "g.node") .data)))
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
        
        link (-> svg (.selectAll "g.link")
                 (.data links #(:id %))
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
        (.call drag))
    (-> circle
        (.attr "class" #(:colour %)))
    (.data node)))
  
(-> d3 (.json "/json" #(update-links (update %1))))