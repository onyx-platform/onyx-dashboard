(ns onyx-dashboard.components.warnigns
  (:require [om.core :as om :include-macros true]
    ; om
    [om-tools.dom  :as dom :include-macros true]
    [om-tools.core :refer-macros [defcomponent]]
    ; om bootstrap
    [om-bootstrap.grid :as g]))

(defcomponent zk-no-conn [zk-up? owner]
  (render [_]
    (when-not zk-up?
      (g/row {:class "no-gutter"}
        (g/col {:xs 12 :md 12 :lg 12}
          (dom/div {:class "alert alert-danger"}
            "ZooKeeper connection problem. Trying to reconnect..."))))))