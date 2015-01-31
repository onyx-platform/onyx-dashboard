(ns onyx-dashboard.components.ui-elements
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :as async :refer [put!]]))

(defcomponent section-header [{:keys [text visible type]} owner]
  (render [_]
          ; should this handler should be on the panel header itself? There's
          ; some unclickable margin / buffer space in there
          (dom/div {:on-click (fn [_] (put! (om/get-shared owner :api-ch) 
                                            [:visibility type (not visible)]))}
                   (dom/h4 {:class "unselectable"} 
                           text
                           (dom/i {:style {:float "right"}
                                   :class (if visible 
                                            "fa fa-caret-square-o-up"
                                            "fa fa-caret-square-o-down")})))))
