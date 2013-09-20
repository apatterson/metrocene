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
(def drag-chan (chan))
(def state-chan (chan))

(defn- drag-move [nodes e i]
  (go 
   (>! data-chan 
       {:nodes (-> (assoc-in nodes [i :x] (.-x e))
                   (assoc-in [i :y] (.-y e)))})))

(go
 (loop [tail nil target nil state nil]
   (let [{e :event node :node end-state :state} (<! drag-chan)
         end -1
         new-target 
         (if (and e node)
           (nth 
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
                       .data)) 2) end)]
     (.log js/console target new-target state end-state)
     (if end-state (do
                     (>! data-chan {:state end-state
                                    :tail nil
                                    :head nil})
                     (recur end end end-state)))
     (when-not (= target new-target) ;;target changed
       (cond (and (= tail end) (= state :seeking))
             (do
               (>! data-chan {:state :connecting
                              :tail new-target
                              :head end})
               (recur new-target new-target :connecting))
             (and (> tail end) (= state :connecting) (> new-target end))
             (do
               (>! data-chan {:state :connected
                              :tail tail
                              :head new-target
                              :weight 1})
               (recur end end :connected))))
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
                (-> d3 (.xhr "/json")
                    (.header "Content-Type" 
                             "application/x-www-form-urlencoded")
                    (.response #(.parse js/JSON (.-responseText %)))
                    (.post 
                     (str "data=" 
                          data #(go 
                                 (>! data-chan 
                                     (js->clj %2 :keywordize-keys true)))))))
         link (-> svg (.selectAll "g.link")
                  (.data links #(:id %)))
         new-link (-> link 
                      .enter
                      (.append "g")                 
                      (.attr "class" #(str "link " 
                                           (if (< weight 0) "neg" "pos"))))
         line (-> new-link
                  (.append "line"))
         dragmove #(drag-move nodes (.-event d3) %2)
         node (-> svg (.selectAll "g.node")
                  (.data nodes #(:id %)))
         drag-vote-move #(let [e (-> d3 .-event)]
                           (go (>! drag-chan {:event e 
                                              :node node}))
                           #_(drag-move nodes e %2))
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
                     (fn [d i] (coord (nth nodes (posn d)))))
         changes (if (= state :connected)
                   (assoc-in new-data 
                             [:links] 
                             (conj (:links new-data) 
                                   {:id (str tail head)
                                    :weight weight
                                    :tail tail
                                    :head head}))
                   new-data)]
     (-> svg (.selectAll "use")
         (.data [{:id "plus" :done false :new nil}])
         .enter
         (.append "use")
         (.call dragvote)
         (.attr "xlink:href" "#plus")
         (.attr "x" 300)
         (.attr "width" 30)
         (.attr "height" 30))
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
     (-> svg 
         (.attr "class" (name state)))
     (-> svg (.selectAll ".link line")
         (.attr {:x1 (find-node :x :tail)
                 :x2 (find-node :x :head)
                 :y1 (find-node :y :tail)
                 :y2 (find-node :y :head)}))
     (post changes)
     (recur changes))))

(-> d3 (.json "/json" #(go (>! data-chan (js->clj %1 :keywordize-keys true)))))

