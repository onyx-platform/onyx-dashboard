(ns onyx-dashboard.components.deployment
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.button :as b]
            [om-bootstrap.table :as t]
            [onyx-dashboard.components.ui-elements :refer [section-header]]
            [cljs.core.async :as async :refer [put!]]))

(defcomponent peer-table [peers owner]
  (render [_]
          (if (empty? peers) 
            (dom/div "No peers are currently running")
            (t/table {:striped? true :bordered? false :condensed? true :hover? true}
                     (dom/thead (dom/tr (dom/th "ID")))
                     (dom/tbody
                       (for [peer-id peers] 
                         (dom/tr {:class "peer-entry"}
                                 (dom/td (str peer-id)))))))))

(defcomponent deployment-indicator [{:keys [deployment last-entry]} owner]
  (render [_] 
          (let [crashed? (= :crashed (:status (:status deployment)))] 
            (dom/div
              (p/panel
                {:header (om/build section-header 
                                   {:text "Dashboard Status" 
                                    :hide-expander? true
                                    :type :job-management} 
                                   {})
                 :bs-style (if (or crashed? (not (:up-to-date? deployment)))  "danger" "primary")}
                (dom/div
                 (if crashed? 
                   (dom/div 
                    "Log replay crashed. Cluster probably died if the dashboard is using the same version of Onyx."
                    (dom/pre {} 
                             (:error (:status deployment)))))
                 (dom/div (str "Display status as of " (.fromNow (js/moment (str (js/Date. (:created-at last-entry)))))))))))))

(defcomponent deployment-peers [deployment owner]
  (render [_] 
          (dom/div
            (p/panel
              {:header (om/build section-header 
                                 {:text "Deployment Peers" 
                                  :hide-expander? true
                                  :type :deployment-peers} 
                                 {})
               :bs-style "primary"}
              (if (:id deployment) 
                (om/build peer-table (:peers deployment) {}))))))


(defcomponent select-deployment [{:keys [deployments deployment]} owner]
  (render [_] 
          (dom/div {:class "btn-group btn-group-justified" :role "group"} 
                   (apply (partial b/dropdown {:bs-style "default" 
                                               :title (or (:id deployment) "Select Deployment")})
                          (for [[id info] (reverse (sort-by (comp :created-at val) 
                                                            deployments))]
                            (b/menu-item {:key id
                                          :on-select (fn [_] 
                                                       (put! (om/get-shared owner :api-ch) 
                                                             [:track-deployment id]))} 
                                         id))))))
