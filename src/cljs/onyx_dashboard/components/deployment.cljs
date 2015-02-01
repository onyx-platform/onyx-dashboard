(ns onyx-dashboard.components.deployment
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.button :as b]
            [cljs.core.async :as async :refer [put!]]))

(defcomponent select-deployment [{:keys [deployments deployment]} owner]
  (render [_] 
          (dom/div 
            (dom/div {:class "btn-group btn-group-justified" :role "group"} 
                     (apply (partial b/dropdown {:bs-style "default" 
                                                 :title (or (:id deployment) "Select Deployment")})
                            (for [[id info] (reverse (sort-by (comp :created-at val) 
                                                              deployments))]
                              (b/menu-item {:key id
                                            :on-select (fn [_] 
                                                         (put! (om/get-shared owner :api-ch) 
                                                               [:track-deployment id]))} 
                                           id))))
            (if (:id deployment) 
              (dom/div (str "Selected deployment log fresh? " (:up-to-date? deployment)))))))
