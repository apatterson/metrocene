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

(defn post [data] 
  (-> d3 (.xhr "/json")
      (.header "Content-Type" "application/x-www-form-urlencoded")
      (.response #(.parse js/JSON (.-responseText %)))
      (.post data #(update (js->clj %2 :keywordize-keys true)))))

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
        new-link (-> link 
                     .enter 
                     (.append "g")                 
                     (.attr "class" #(str "link " (:colour %))))
        line (-> new-link
                 (.append "line"))
        c (chan)
        drag-move #(let [old-data (-> d3 (.select %) .data first)]
                     (when-not (or (:new old-data) (:done old-data))
                       (-> d3 
                           (.select %)
                           (.data [(merge old-data
                                          {:x (-> d3 .-event .-x)
                                           :y (-> d3 .-event .-y)})])
                           (.attr {:transform (str "translate(" 
                                                   (-> d3 .-event .-x) ","
                                                   (-> d3 .-event .-y) ")")}))))
        dragmove #(this-as 
                   this
                   (drag-move this)
                   (update {:nodes (-> d3 (.selectAll "g.node") .data)
                            :links links}))
        over (fn 
               [[e this]]
               (let [old-data (-> d3 (.select this) .data first)]
                 (-> node 
                     (.each 
                      (fn [d i] (let [dx  (- (:x d) (.-x e))
                                      dy  (- (:y d) (.-y e))
                                      close (< (.sqrt js/Math
                                                      (+
                                                       (.pow js/Math dx 2)
                                                       (.pow js/Math dy 2)))
                                               20)]
                                  (when (and close (not (:done old-data)))
                                    (when (not= (:new old-data) i)
                                      (do
                                        (-> d3 (.select this) 
                                            (.data 
                                             [(merge old-data
                                                     {:new (when-not 
                                                               (:new old-data)
                                                             i)
                                                      :done (:new old-data)})])
                                            (.attr {:transform 
                                                    "translate(10,20)"}))
                                        (if (:new old-data)
                                          #_(.log js/console "2 " (:id d) (:new old-data))
                                          (update 
                                           {:nodes (-> d3 (.selectAll "g.node")
                                                       .data) 
                                            :links (conj links 
                                                         {:id (str (:id d) 
                                                                   (:new old-data))
                                                          :tail (:new old-data)
                                                          :head i
                                                          :weight 1
                                                    :colour "pos"})})
                                          (.log js/console (-> d3 (.select this) 
                                                               .data first :new))))))))))))
        drag-vote-move #(this-as 
                         this
                         (let [e (-> d3 .-event)]
                           (drag-move this)
                           (go (>! c [e this]))))
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
        (.data [{:id "plus" :done False :new nil}])
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
    (-> line
        (.attr {:x1 (find-node :x :tail)
                :x2 (find-node :x :head)
                :y1 (find-node :y :tail)
                :y2 (find-node :y :head)}))
    (-> svg (.selectAll ".link line")
        (.attr {:x1 (find-node :x :tail)
                :x2 (find-node :x :head)
                :y1 (find-node :y :tail)
                :y2 (find-node :y :head)}))
    (go (while true
          (over (<! c))))))

(-> d3 (.json "/json" #(update (js->clj %1 :keywordize-keys true))))