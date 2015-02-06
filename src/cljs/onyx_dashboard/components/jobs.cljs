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

(defcomponent job-management [{:keys [job visible] :as app} owner]
  (render [_]
          (let [api-ch (om/get-shared owner :api-ch)
                job-id (:id job)
                restart-job-handler (fn [_] (put! api-ch [:restart-job job-id]))
                kill-job-handler (fn [_] (put! api-ch [:kill-job job-id]))] 
            (dom/div
              (p/panel
                {:header (om/build section-header 
                                   {:text "Job Management" 
                                    :visible visible
                                    :hide-expander? true
                                    :type :job-management} 
                                   {})
                 :bs-style "primary"}
                (if visible 
                  (g/grid {} 
                          (g/row {} 
                                 (g/col {:xs 2 :md 1} 
                                        (dom/div {:on-click restart-job-handler}
                                                               (dom/i {:class "fa fa-repeat"} 
                                                                      " Restart")))
                                 (g/col {:xs 2 :md 1} 
                                        (dom/div {:on-click kill-job-handler}
                                                 (dom/i {:class "fa fa-times-circle-o"} " Kill")))))))))))

(defcomponent task-panel [{:keys [job visible]} owner]
  (render [_]
          (p/panel
            {:header (om/build section-header 
                               {:text "Running Tasks" 
                                :visible visible
                                :hide-expander? true
                                :type :tasks} 
                               {})
             :footer (if-not (empty? (:tasks job)) 
                       (dom/div {:style {:color "rgb(197, 6, 11)"}} 
                                (str "Scheduler " (:task-scheduler job))))
             :bs-style "primary"}
            (if visible 
              (om/build task-table (:tasks job) {})))))

(defcomponent job-info [{:keys [job visible]} owner]
  (render [_]
          (dom/div
            (om/build task-panel {:job job :visible (:tasks visible)} {})
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
                                  :hide-expander? true
                                  :type :workflow} 
                                 {})
               :bs-style "primary"}
              (if (:workflow visible)
                (om/build clojure-block {:input (:pretty-workflow job)}))))))
