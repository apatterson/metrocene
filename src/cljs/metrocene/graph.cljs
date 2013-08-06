(ns metrocene.graph
  (:require [strokes :refer [d3]]))

(strokes/bootstrap)

(def svg (-> d3 (.select "body") (.append "svg")))

(defn update [{data :nodes links :links}]
  (let [weight (fn [l r] (if (or (= l r) (> (:y l) (:y r))) 
                           0 
                           (if (> (:x l) (:x r)) 
                             1 
                             -1)))
        node (-> svg (.selectAll "g.node")
                 (.data data :id)
                 (.enter)
                 (.append "g")
                 (.attr "class" "node" ))
        circle (-> node
                   (.append "circle")
                   (.attr "r" 20))
        text (-> node
                 (.append "text")
                 (.text #(:name %1)))
        dragmove #(this-as this
                           (-> d3 
                               (.select this)
                               (.data [{:id (fn [d i] (:id d))
                                        :x (-> d3 .-event .-x)
                                        :y (-> d3 .-event .-y)}])
                               (.attr {:transform (str "translate(" 
                                                       (-> d3 .-event .-x) ","
                                                       (-> d3 .-event .-y) ")")})))
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
                 (.attr "class" "link" ))
        line (-> link
                 (.append "line")
                 (.attr {:x1 #(:x (.find 
                                   js/_ 
                                   data 
                                   (fn [n] (= (:tail %) (:id n)))))
                         :x2 #(:x (.find 
                                   js/_ 
                                   data 
                                   (fn [n] (= (:head %) (:id n)))))
                         :y1 #(:y (.find 
                                   js/_ 
                                   data 
                                   (fn [n] (= (:tail %) (:id n)))))
                         :y2 #(:y (.find 
                                   js/_ 
                                   data 
                                   (fn [n] (= (:head %) (:id n)))))}))]
    (-> node 
        (.attr {:transform #(str "translate(" (:x %) "," (:y %) ")")})
        (.call drag))))

(update {:nodes 
         [{:id :a :name "Greenhouse Gases"   :x 100 :y 100 :r 20}
          {:id :b :name "Climate Change"     :x 200 :y 200 :r 20}
          {:id :c :name "Economic Growth"    :x 300 :y 300 :r 20}]
         :links
         [{:id :x :weight 1 :tail :c :head :b}]})
