(ns onyx-dashboard.components.deployment
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.button :as b]
            [onyx-dashboard.components.ui-elements :refer [section-header]]
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
            (dom/div
              (p/panel
                {:header (om/build section-header 
                                   {:text "Dashboard Fresh?" 
                                    :hide-expander? true
                                    :type :job-management} 
                                   {})
                 :bs-style "primary"}
                (if (:id deployment) 
                  (dom/i {:style {:align "center"}
                          :class (if (:up-to-date? deployment)
                                   "fa fa-thumbs-o-up"
                                   "fa fa-thumbs-o-down")})))))))
