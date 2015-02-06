(ns onyx-dashboard.components.main
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.grid :as g]
            [om-bootstrap.random :as r]
            [onyx-dashboard.components.deployment :refer [select-deployment deployment-indicator deployment-peers]]
            [onyx-dashboard.components.jobs :refer [job-selector job-info job-management]]
            [onyx-dashboard.components.log :refer [log-entries-pager]]
            [onyx-dashboard.controllers.api :refer [api-controller]]
            [cljs.core.async :as async :refer [<! >! put! chan]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go-loop]]))

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
                (let [{:keys [selected-job jobs]} deployment
                      job (and selected-job jobs (jobs selected-job))] 
                  (dom/div
                   (g/row {}
                          (r/page-header {:class "page-header, main-header"}
                                         (g/grid {}
                                                 (g/col {:xs 8 :md 2}
                                                        (dom/a {:href "https://github.com/MichaelDrogalis/onyx"}
                                                               (dom/img {:class "logo" :src "/img/high-res.png" :height "50%" :width "50%"})))
                                                 (g/col {:xs 12 :md 8}
                                                        (dom/div {:style {:margin-top "50px" :margin-left "200px" :font-size "50px"}}
                                                                 "Onyx Dashboard")))))
                    (g/grid {}
                            (g/row {}
                                   (g/col {:xs 6 :md 4}
                                          (dom/div {:class "left-nav-deployment"} 
                                                   (om/build select-deployment app {}))

                                          (if (:id deployment) 
                                            (dom/div {} 
                                                     (om/build deployment-peers deployment {})))

                                          (dom/div {} 
                                                   (om/build job-selector deployment {}))

                                          (if (:id deployment) 
                                            (dom/div {} 
                                                     (om/build deployment-indicator 
                                                               {:deployment deployment
                                                                :last-entry ((:entries deployment) (:message-id-max deployment))} 
                                                               {})))

                                          (dom/div {}
                                                   (if job 
                                                     (om/build job-management 
                                                               {:job job :visible (:job-management visible)} 
                                                               {:react-key (str "management-" (:id job))}))))
                                   (g/col {:xs 12 :md 8}
                                          (dom/div 
                                            (if job 
                                              (om/build job-info 
                                                        {:job job :visible visible} 
                                                        {:react-key (str "job-info-" (:id job))}))
                                            (om/build log-entries-pager 
                                                      {:entries (:entries deployment)
                                                       :job-filter (:id job) 
                                                       :visible (:log-entries visible)} 
                                                      {:react-key (str "log-" (:id deployment) "-filter-" (:id job))})))))))))
