(ns onyx-dashboard.components.main
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.grid :as g]
            [om-bootstrap.random :as r]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [cljs-uuid.core :as uuid]
            [onyx-dashboard.components.deployment :refer [select-deployment]]
            [onyx-dashboard.components.jobs :refer [job-selector job-info]]
            [onyx-dashboard.components.log :refer [log-entries-table]]
            [onyx-dashboard.controllers.api :refer [api-controller]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go go-loop]]))

(defn deployment->latest-log-entries [{:keys [entries message-id-max] :as deployment}]
  (let [num-displayed 100
        start-id (max 0 (- (inc message-id-max) num-displayed))
        displayed-msg-ids (range start-id (inc message-id-max))]
    (keep entries (reverse displayed-msg-ids))))

(defcomponent main-component [{:keys [deployment visible] :as app} owner]
  (did-mount [_] 
             (let [api-ch (om/get-shared owner :api-ch)
                   chsk-send! (om/get-shared owner :chsk-send!)] 
               (go-loop []
                        (let [msg (<! api-ch)]
                          (println "Handling msg: " msg)
                          (om/transact! app (partial api-controller msg chsk-send!))
                          (recur)))))

  (render-state [_ {:keys [api-chan]}]
                (dom/div 
                  (r/page-header {:class "page-header" 
                                  :style {:text-align "center"}} 
                                 "Onyx Dashboard")
                  (g/grid {}
                          (g/row {}
                                 (g/col {:xs 6 :md 4}
                                        (dom/div {:class "left-nav-deployment"} 
                                                 (om/build select-deployment app {}))
                                        (dom/div {} 
                                                 (om/build job-selector deployment {})))
                                 (g/col {:xs 12 :md 8}
                                        (dom/div (om/build job-info {:deployment deployment
                                                                     :visible visible} {})
                                                 (om/build log-entries-table {:entries (deployment->latest-log-entries deployment)
                                                                              :visible (:log-entries visible)} {}))))))))
