(ns onyx-dashboard.components.jobs
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.grid :as g]
            [om-bootstrap.table :as t]
            [onyx-dashboard.components.code :refer [clojure-block]]
            [onyx-dashboard.components.ui-elements :refer [section-header]]
            [cljs.core.async :as async :refer [put!]]))

(defcomponent job-selector [{:keys [selected-job jobs]} owner]
  (render [_]
          (p/panel {:header (dom/h4 "Job Selector") :bs-style "primary" }
                   (t/table {:striped? true :bordered? false :condensed? true :hover? true}
                          ;; (dom/thead (dom/tr (dom/th "ID") (dom/th "Time")))
                          (dom/tbody
                            (for [job (reverse (sort-by :created-at (vals jobs)))] 
                              (let [job-id (:id job)
                                    selected? (= job-id selected-job)] 
                                (dom/tr {:class "job-entry"}
                                        (dom/td (dom/i {:class (if selected? "fa fa-dot-circle-o" "fa fa-circle-o")}))
                                        (dom/td {:on-click (fn [_] 
                                                             (put! (om/get-shared owner :api-ch) 
                                                                   [:select-job job-id]))} 
                                                (str job-id))
                                        (dom/td {}
                                                (.fromNow (js/moment (str (js/Date. (:created-at job))))))))))))))

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

(defcomponent job-management [{:keys [job visible]} owner]
  (render [_]
          (dom/div
            (p/panel
              {:header (om/build section-header 
                                 {:text "Job Management" 
                                  :visible visible 
                                  :type :job-management} 
                                 {})
               :bs-style "primary"}
              (if visible 
                (g/grid {} 
                        ; Show operations based on current status of job)
                        (g/row {} 
                               (g/col {:xs 2 :md 1} (dom/i {:class "fa fa-heartbeat"} "Running"))
                               (g/col {:xs 2 :md 1} (dom/i {:class "fa fa-repeat"} "Restart"))
                               (g/col {:xs 2 :md 1} (dom/i {:class "fa fa-times-circle-o"} "Kill")))))))))

(defcomponent job-info [{:keys [job visible]} owner]
  (render [_]
          (dom/div
            (p/panel
              {:header (om/build section-header 
                                 {:text "Running Tasks" 
                                  :visible (:tasks visible) 
                                  :type :tasks} 
                                 {})
               :bs-style "primary"}
              (if (:tasks visible) 
                (om/build task-table (:tasks job) {})))

            (p/panel
              {:header (om/build section-header 
                                 {:text "Catalog" 
                                  :visible (:job visible) 
                                  :type :job} 
                                 {})
               :bs-style "primary"}
              (if (:job visible)
                (om/build clojure-block {:input (:pretty-catalog job)})))

            (p/panel
              {:header (om/build section-header 
                                 {:text "Workflow" 
                                  :visible (:workflow visible) 
                                  :type :workflow} 
                                 {})
               :bs-style "primary"}
              (if (:workflow visible)
                (om/build clojure-block {:input (:pretty-workflow job)}))))))
