(ns onyx-dashboard.components.ui-elements
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :as async :refer [put!]]))

(defcomponent section-header-collapsible [{:keys [text]} owner]
  (init-state [_]
              {:collapsed? false})
  (render-state [_ {:keys [collapsed?]}]
                (dom/div {:on-click (fn [e] (om/update-state! owner :collapsed? not))}
                         (dom/h4 {:class "unselectable"} 
                                 text
                                 ;; Not colllapsible by default is broken in latest bootstrap
                                 ;; So disabling collapsibility all together for now
                                 #_(dom/i {:style {:float "right"}
                                         :class (if collapsed? 
                                                  "fa fa-caret-square-o-down"
                                                  "fa fa-caret-square-o-up")})))))

(defcomponent please-select-job [_ owner]
  (render [_]
    (dom/div {}
      (dom/h4 {} "No job selected")
      (dom/p {} "Please go to Tenancy page and select a job."))))

(defcomponent please-select-tenancy [_ owner]
  (render [_]
    (dom/div {}
       (dom/h4 {} "No tenancy selected")
       (dom/p {} "Please go to Tenancies page and select a tenancy."))))

