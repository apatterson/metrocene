(ns metrocene.graph
  (:require [strokes :refer [d3]]))

(strokes/bootstrap)

(def svg (-> d3 (.select "body") (.append "svg")))

(defn update [data]
  (let [weight (fn [l r] (if (or (= l r) (> (:y l) (:y r))) 
                           0 
                           (if (> (:x l) (:x r)) 
                             1 
                             -1)))
        circle (-> svg (.selectAll "circle")
                   (.data data :id)
                   (.enter)
                   (.append "circle"))
        dragmove #(this-as this
                           (-> d3 
                               (.select this)
                               (.attr {:cx (-> d3 .-event .-x)
                                       :cy (-> d3 .-event .-y)})))
        circles (-> svg (.selectAll "circle"))
        dragmoveend #(do
                       (this-as 
                        this
                        (-> d3
                            (.select this)
                            (.data (fn [d i] [{:x (-> this .-cx .-animVal .-value)
                                               :y (-> this .-cy .-animVal .-value)}]))
                            (.attr {:cx (fn [d i] (:x d))
                                    :cy (fn [d i] (:y d))})))
                       (.attr circles {:r (fn [d i] 
                                            (-> (reduce (fn [acc c] 
                                                          (+ acc (weight c d)))
                                                        0
                                                        (.data circles))
                                                (* 10)
                                                (+ 30)))}))
        drag (-> d3 
                 .-behavior 
                 .drag
                 (.on "drag" dragmove)
                 (.on "dragend" dragmoveend))]
    (-> circle 
        (.attr {:cx #(:x %) :cy #(:y %) :r #(:r %)})
        (.call drag))))

(update [{:id "a" :x 100 :y 100 :r 20}
         {:id "b" :x 200 :y 200 :r 20}
         {:id "c" :x 300 :y 300 :r 20}])
