(ns metrocene.graph
  (:require [strokes :refer [d3]]))

(strokes/bootstrap)

(def svg (-> d3 (.select "body") (.append "svg")))

(defn update [data]
  (let [circle (-> svg (.selectAll "circle")
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
                                            (-> (filter (fn [c] (< (:x c) 
                                                                   (:x d))) 
                                                        (.data circles))
                                                count
                                                (* 10)
                                                (+ 10)))}))
        drag (-> d3 
                 .-behavior 
                 .drag
                 (.on "drag" dragmove)
                 (.on "dragend" dragmoveend))]
    (-> circle 
        (.attr {:cx #(:x %) :cy #(:y %) :r #(:r %)})
        (.call drag))))

(update [{:id "a" :x 100 :y 100 :r 20}
         {:id "b" :x 200 :y 200 :r 20}])
