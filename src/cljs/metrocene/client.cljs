(ns hello-clojurescript)

(defn handle-click []
  (js/alert "Hello, Anthony"))

(def clickable (.getElementById js/document "clickable"))
(.addEventListener clickable "click" handle-click)
