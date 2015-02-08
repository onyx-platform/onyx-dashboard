(ns onyx-dashboard.components.jobs
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.grid :as g]
            [om-bootstrap.table :as t]
            [cljsjs.moment]
            [onyx-dashboard.components.code :refer [clojure-block]]
            [onyx-dashboard.components.ui-elements :refer [section-header-collapsible]]
            [cljs.core.async :as async :refer [put!]]))

(defcomponent job-selector [{:keys [selected-job jobs]} owner]
  (render [_]
          (p/panel {:header (dom/h4 "Jobs") 
                    :bs-style "primary"}
                   (t/table {:striped? true :bordered? false :condensed? true :hover? true :class "job-selector"}
                            ;; (dom/thead (dom/tr (dom/th "ID") (dom/th "Time")))
                            (dom/tbody
                              (cons (dom/tr {:class "job-entry"
                                             :on-click (fn [_] (put! (om/get-shared owner :api-ch) [:select-job nil]))}
                                            (dom/td (dom/i {:class (if (nil? selected-job) "fa fa-dot-circle-o" "fa fa-circle-o")}))
                                            (dom/td {} (dom/a {:href "#"} "None"))
                                            (dom/td {}))
                                    (for [job (reverse (sort-by :created-at (vals jobs)))] 
                                      (let [job-id (:id job)
                                            selected? (= job-id selected-job)] 
                                        (dom/tr {:class "job-entry"
                                                 :on-click (fn [_] 
                                                             (put! (om/get-shared owner :api-ch) 
                                                                   [:select-job job-id]))}
                                                (dom/td (dom/i {:class (if selected? "fa fa-dot-circle-o" "fa fa-circle-o")}))
                                                (dom/td {} 
                                                        (dom/a {:href "#"} (str job-id)))
                                                (dom/td {}
                                                        (.fromNow (js/moment (str (js/Date. (:created-at job)))))))))))))))

(defcomponent task-table [tasks owner]
  (render [_]
          (if (empty? tasks) 
            (dom/div "No tasks are currently being processed for this job")
            (t/table {:striped? true :bordered? false :condensed? true :hover? true}
                     (dom/thead (dom/tr (dom/th "Name") (dom/th "Task ID") (dom/th "Peer ID")))
                     (dom/tbody
                       (for [[peer-id task] (sort-by (comp :name val) tasks)] 
                         (dom/tr {:class "task-entry"}
                                 (dom/td (str (:name task)))
                                 (dom/td (str (:id task)))
                                 (dom/td (str peer-id)))))))))

(defcomponent peer-table [peers owner]
  (render [_]
          (if (empty? peers) 
            (dom/div "No peers are currently running")
            (t/table {:striped? true :bordered? false :condensed? true :hover? true}
                     (dom/thead (dom/tr (dom/th "ID")))
                     (dom/tbody
                       (for [peer peers] 
                         (dom/tr {:class "peer-entry"}
                                 (dom/td (str (:id peer))))))))))

(defcomponent job-management [{:keys [id status] :as job} owner]
  (render [_]
          (let [api-ch (om/get-shared owner :api-ch)
                restart-job-handler (fn [_] 
                                      (when (js/confirm "Are you sure you want to restart this job?")
                                        (put! api-ch [:restart-job id])))
                kill-job-handler (fn [_] 
                                   (when (js/confirm "Are you sure you want to kill this job?")
                                     (put! api-ch [:kill-job id])))] 
            (dom/div
              (p/panel
                {:header (om/build section-header-collapsible {:text "Catalog"} {})
                 :collapsible? true
                 :bs-style "primary"}
                (g/grid {} 
                        (g/row {} 
                               (g/col {:xs 2 :md 1}
                                      (dom/button {:on-click restart-job-handler
                                                   :type "button"
                                                   :class "btn btn-warning"}
                                                  (dom/i {:class "fa fa-repeat"} " Restart")))
                               (g/col {:xs 2 :md 1}
                                      (if (= status :incomplete)
                                        (dom/button {:on-click kill-job-handler
                                                     :type "button"
                                                     :class "btn btn-danger"}
                                                    (dom/i {:class "fa fa-times-circle-o"
                                                            :style {:padding-right "10px"}} " Kill")))))))))))

(defcomponent job-overview-panel [job owner]
  (render [_]
          (p/panel
            {:header (om/build section-header-collapsible {:text "Catalog"} {})
             :collapsible? true
             :bs-style "primary"}
            (g/grid {} 
                    (g/row {} 
                           (str "Job status is " (:status job)))
                    (g/row {} 
                           (str "Task Scheduler is " (:task-scheduler job)))))))

(defcomponent task-panel [{:keys [tasks] :as job} owner]
  (render [_]
          (p/panel
            {:header (om/build section-header-collapsible {:text "Running Tasks"} {})
             :collapsible? true
             :bs-style "primary"}
            (om/build task-table tasks {}))))

(defcomponent job-info [{:keys [pretty-catalog pretty-workflow] :as job} owner]
  (render [_]
          (dom/div
            (om/build job-overview-panel job)
            (om/build task-panel job {})
            (p/panel
              {:header (om/build section-header-collapsible {:text "Catalog"} {})
               :collapsible? true
               :bs-style "primary"}
              (om/build clojure-block {:input pretty-catalog}))

            (p/panel
              {:header (om/build section-header-collapsible {:text "Workflow"} {})
               :collapsible? true
               :bs-style "primary"}
              (om/build clojure-block {:input pretty-workflow})))))
