(ns onyx-dashboard.components.jobs
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.grid :as g]
            [om-bootstrap.button :as b]
            [om-bootstrap.table :as t]
            [cljsjs.moment]
            [fipp.edn :as fipp]
            [onyx-dashboard.components.code :refer [clojure-block]]
            [onyx-viz.core :as viz]
            [lib-onyx.replica-query :as rq]
            [onyx-dashboard.state-query :as sq]
            [onyx-dashboard.components.ui-elements :refer [section-header-collapsible]]
            [cljs.core.async :as async :refer [put!]]))

(defcomponent job-selector [{:keys [selected-job jobs] :as deployment} owner]
  (render [_]
          (let [replica (sq/deployment->latest-replica deployment)] 
            (p/panel {:header (dom/h4 "Jobs") 
                      :bs-style "primary"}
                     (t/table {:striped? true :bordered? false :condensed? true :hover? true :class "job-selector"}
                              (dom/thead (dom/tr (dom/th) (dom/th "ID") (dom/th "State") (dom/th "Time")))
                              (dom/tbody
                                (cons (dom/tr {:on-click (fn [_] (put! (om/get-shared owner :api-ch) [:select-job nil]))}
                                              (dom/td (dom/i {:class (if (nil? selected-job) "fa fa-dot-circle-o" "fa fa-circle-o")}))
                                              (dom/td {} (dom/a {:class "job-entry" :href "#"} "None"))
                                              (dom/td {})
                                              (dom/td {}))
                                      (for [job (reverse (sort-by :created-at (vals jobs)))] 
                                        (let [job-id (:id job)
                                              selected? (= job-id selected-job)
                                              job-state (rq/job-state replica job-id)] 
                                          (if-not (= job-state :GCd) 
                                            (dom/tr {:on-click (fn [_] 
                                                                 (put! (om/get-shared owner :api-ch) 
                                                                       [:select-job job-id]))}
                                                    (dom/td (dom/i {:class (if selected? "fa fa-dot-circle-o" "fa fa-circle-o")}))
                                                    (dom/td {} 
                                                            (dom/a {:class "job-entry uuid" :href "#"} (str job-id)))
                                                    (dom/td {}
                                                            (dom/i (case job-state
                                                                     :running
                                                                     {:class "fa fa-cog fa-spin"}
                                                                     :completed
                                                                     {:style {:color "green"} :class "fa fa-check"}
                                                                     :killed
                                                                     (if (:exception job)
                                                                       {:style {:color "red"} :class "fa fa-exclamation"} 
                                                                       {:style {:color "orange"} :class "fa fa-remove"}))))
                                                    (dom/td {} (.fromNow (js/moment (str (js/Date. (:created-at job)))))))))))))))))

(defcomponent task-table [{:keys [job-info replica]} owner]
  (render [_]
          (let [task-hosts (sq/job-info->task-hosts replica job-info)] 
            (if (empty? task-hosts) 
              (dom/div "No tasks are currently being processed for this job")
              (t/table {:striped? true :bordered? false :condensed? true :hover? true}
                       (dom/thead (dom/tr (dom/th "Name") (dom/th "Host Allocation")))
                       (dom/tbody
                         (for [[task peer-hosts] task-hosts] 
                           (dom/tr {:class "task-entry"}
                                   (dom/td (str task))
                                   (dom/td (dom/table {}
                                                (dom/tbody
                                                  (for [[host host-peers] (group-by val peer-hosts)]
                                                    (dom/tr
                                                      (dom/td (str host ": " 
                                                                   (count host-peers) 
                                                                   " peer" 
                                                                   (if (> 1 (count host-peers)) "s"))))))))))))))))

; (defcomponent job-management [{:keys [id status] :as job} owner]
;   (render [_]
;           (let [api-ch (om/get-shared owner :api-ch)
;                 restart-job-handler (fn [_] 
;                                       (when (js/confirm "Are you sure you want to restart this job?")
;                                         (put! api-ch [:restart-job id])))
;                 kill-job-handler (fn [_] 
;                                    (when (js/confirm "Are you sure you want to kill this job?")
;                                      (put! api-ch [:kill-job id])))] 
;             (dom/div
;               (p/panel
;                 {:header (om/build section-header-collapsible {:text "Job Management"} {})
;                  :collapsible? true
;                  :bs-style "primary"}
;                 (g/grid {} 
;                         (g/row {} 
;                                (g/col {:xs 2 :md 1}
;                                       (dom/button {:on-click restart-job-handler
;                                                    :type "button"
;                                                    :class "btn btn-warning"}
;                                                   (dom/i {:class "fa fa-repeat"} " Restart")))
;                                (g/col {:xs 2 :md 1}
;                                       (if (= status :incomplete)
;                                         (dom/button {:on-click kill-job-handler
;                                                      :type "button"
;                                                      :class "btn btn-danger"}
;                                                     (dom/i {:class "fa fa-times-circle-o"
;                                                             :style {:padding-right "10px"}} " Kill")))))))))))

(defcomponent job-overview-panel [{:keys [replica job-info]} owner]
  (render [_]
          (let [id (:id job-info)
                job-state (rq/job-state replica id)] 
            (p/panel
              {:header (om/build section-header-collapsible {:text "Job Status"} {})
               ;:collapsible? true
               :bs-style "primary"}
              (g/grid {} 
                      (g/row {} 
                             (g/col {:xs 2 :md 2} "Job Status")
                             (g/col {:xs 2 :md 4} (name job-state)))
                      (g/row {} 
                             (g/col {:xs 2 :md 2} "Task Scheduler")
                             (g/col {:xs 2 :md 4} (name (:task-scheduler (:job job-info))))))))))

(defcomponent task-panel [job-replica owner]
  (render [_]
          (p/panel
            {:header (om/build section-header-collapsible {:text "Running Tasks"} {})
             ;:collapsible? true
             :bs-style "primary"}
            (om/build task-table job-replica {}))))

(defcomponent exception-panel [exception owner]
  (render [_]
          (p/panel
            {:header (om/build section-header-collapsible {:text "Exception Thrown"} {})
             ;:collapsible? true
             :bs-style "primary"}
            (dom/pre exception))))

(defcomponent job-visualisation [job-info owner]
  (render [_]
          (p/panel
            {:header (om/build section-header-collapsible {:text "Job Visualization"} {})
             ;:collapsible? true
             :bs-style "primary"}
            (om/build viz/job-dag {:job (:job job-info) :width 640 :height 640}))))

(defcomponent job-info [{:keys [replica job-info]} owner]
  (render [_]
          (let [{:keys [job id metrics exception]} job-info
                {:keys [catalog workflow flow-conditions triggers windows metadata]} job] 
            (if (= :GCd (rq/job-state replica id))
              (p/panel
                {:header (om/build section-header-collapsible {:text "Job State"} {})
                 ;:collapsible? true
                 :bs-style "primary"}
                (dom/div "Selected job is unknown to the cluster at the selected message id"))

              (dom/div
                (om/build job-overview-panel {:replica replica :job-info job-info})
                (if exception (om/build exception-panel exception {}))
                (om/build task-panel {:replica replica :job-info job-info} {})
                (om/build job-visualisation job-info {})

                (p/panel
                 {:header (om/build section-header-collapsible {:text "Metadata"} {})
                  ;:collapsible? true
                  :bs-style "primary"}
                 (om/build clojure-block {:input (with-out-str (fipp/pprint (om/value metadata)))}))

                (p/panel
                  {:header (om/build section-header-collapsible {:text "Workflow"} {})
                   ;:collapsible? true
                   :bs-style "primary"}
                  (om/build clojure-block {:input (with-out-str (fipp/pprint (om/value workflow)))}))

                (p/panel
                  {:header (om/build section-header-collapsible {:text "Catalog"} {})
                   ;:collapsible? true
                   :bs-style "primary"}
                  (om/build clojure-block {:input (with-out-str (fipp/pprint (om/value catalog)))}))

                (if-not (empty? flow-conditions) 
                  (p/panel
                    {:header (om/build section-header-collapsible {:text "Flow Conditions"} {})
                     ;:collapsible? true
                     :bs-style "primary"}
                    (om/build clojure-block {:input (with-out-str (fipp/pprint (om/value flow-conditions)))})))

                (if-not (empty? windows) 
                  (p/panel
                    {:header (om/build section-header-collapsible {:text "Windows"} {})
                     ;:collapsible? true
                     :bs-style "primary"}
                    (om/build clojure-block {:input (with-out-str (fipp/pprint (om/value windows)))})))

                (if-not (empty? triggers) 
                  (p/panel
                    {:header (om/build section-header-collapsible {:text "Triggers"} {})
                     ;:collapsible? true
                     :bs-style "primary"}
                    (om/build clojure-block {:input (with-out-str (fipp/pprint (om/value triggers)))}))))))))
