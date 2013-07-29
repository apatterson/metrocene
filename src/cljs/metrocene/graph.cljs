(ns metrocene.graph
  (:require [strokes :refer [d3]]))

(strokes/bootstrap)

(def width 960)
(def height 500)

(def svg (-> d3 (.select "body") (.append "svg")
      (.attr {:width width :height height})))

(defn update [data]
  (let [circle (-> svg (.selectAll "circle") 
                   (.data data)
                   (.enter)
                   (.append "circle"))]
    (-> circle (.attr {:cx 350 :cy #(str %1) :r 30}))))

(update [100 200])

(.setInterval 
 js/window 
 (fn []
   (update [100 200 300]))
 2000)