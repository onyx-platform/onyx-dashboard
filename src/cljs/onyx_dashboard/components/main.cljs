(ns onyx-dashboard.components.main
  (:require [om.core :as om :include-macros true]
            ; om
            [om-tools.dom  :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            ; om bootstrap
            [om-bootstrap.grid   :as g]
            [om-bootstrap.button :as b]
            [om-bootstrap.nav    :as n]
            [om-bootstrap.random :as r]
            [om-bootstrap.panel  :as p]
            ; dashboard
            [onyx-dashboard.components.warnigns    :refer [zk-no-conn]]
            [onyx-dashboard.components.ui-elements :refer [please-select-tenancy please-select-job]]
            [onyx-dashboard.components.navbar-top  :refer [navbar-top]]
            [onyx-dashboard.components.navbar-left :refer [navbar-left]]
            [onyx-dashboard.components.deployment :refer [select-deployment select-deployment deployment-indicator
                                                          deployment-time-travel deployment-peers deployment-log-dump]]
            [onyx-dashboard.components.jobs :refer [job-selector job-info job-management]]
            [onyx-dashboard.components.log  :refer [log-entries-pager]]
            [onyx-dashboard.controllers.api :refer [api-controller]]
            [onyx-dashboard.state-query :as sq]
            [cljs.core.async :as async :refer [<! >! put! chan]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go-loop]]))

(defcomponent main-component [{:keys [deployment metrics zk-up? ui/is-dev? ui/select-deployment? ui/curr-page] :as app} owner]
  (did-mount [_] 
             (let [api-ch       (om/get-shared owner :api-ch)
                   into-server! (om/get-shared owner :into-server!)]
               (go-loop []
                        (let [msg (<! api-ch)]
                          (om/transact! app (partial api-controller msg into-server!))
                          (recur)))))

  (render-state [_ {:keys [api-chan]}]
                (let [{:keys [selected-job jobs replica-states]} deployment
                      job (and selected-job jobs (jobs selected-job))]
                  (dom/div {:class "wrapper lbar-open"}
                    (om/build navbar-top nil {})
                    (om/build navbar-left curr-page {})

                    (dom/section {:class "content-wrapper"}
                        (dom/div {:class "content"}
                            (g/grid {:fluid? true}
                                (om/build zk-no-conn zk-up? {})
                                (g/row {:class "no-gutter"}
                                  (g/col {:xs 12 :md 12 :lg 12}
                                    (when (= curr-page :page/tenancies)
                                          (om/build select-deployment app {}))
                                    (when (= curr-page :page/tenancy)
                                      (if deployment
                                          (dom/div
                                            (om/build deployment-peers deployment {})
                                            (om/build job-selector deployment {}))
                                          (om/build please-select-tenancy nil {})))
                                    (when (= curr-page :page/job)
                                      (if job
                                        (om/build job-info
                                                 {:replica (sq/deployment->latest-replica deployment)
                                                  :job-info job}
                                                 {:react-key (str "job-info-" (:id job))})
                                        (om/build please-select-job nil {})))
                                    (when (= curr-page :page/log-entries)
                                      (if job
                                        (om/build log-entries-pager
                                                  {:replica-states replica-states
                                                   :job-filter (:id job)}
                                                  {:react-key (str "log-" (:id deployment) "-filter-" (:id job))})
                                        (om/build please-select-job nil {})))
                                   (when (= curr-page :page/time-travel)
                                     (if deployment
                                       (om/build deployment-time-travel deployment)
                                       (om/build please-select-tenancy nil {}))))))))
                    (when is-dev?
                      (dom/a {:href "https://github.com/onyx-platform/onyx-dashboard"}
                          (dom/img {:id  "forkme"
                                    :src "https://s3.amazonaws.com/github/ribbons/forkme_right_green_007200.png"
                                    :alt "Fork me on GitHub"})))))))