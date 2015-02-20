(ns onyx-dashboard.components.deployment
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.button :as b]
            [om-bootstrap.table :as t]
            [om-bootstrap.grid :as g]
            [shoreleave.browser.blob :as blob]
            [onyx-dashboard.components.ui-elements :refer [section-header-collapsible]]
            [cljs.core.async :as async :refer [put!]]
            [cljsjs.moment]
            [cljs.core.async :as async :refer [<! >! put! chan]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go-loop]]))

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
          (let [crashed? (= :crashed (:status (:status deployment)))
                onyx-logo-rotation (mod (* (:message-id last-entry) 
                                           10)
                                        360)
                rotation-css (str "rotate(" onyx-logo-rotation "deg)")] 
            (dom/div
              (p/panel
                {:header (dom/div {} (dom/h4 {:class "unselectable"} "Dashboard Status"))
                 :bs-style (if (or crashed? (not (:up-to-date? deployment)))  "danger" "primary")}
                (dom/div 
                  (if crashed? 
                    (dom/div "Log replay crashed. Cluster probably died if the dashboard is using the same version of Onyx."
                             (dom/pre {} 
                                      (:error (:status deployment)))))
                  (dom/div 
                    (dom/img {:style {:-ms-transform rotation-css
                                      :-webkit-transform rotation-css
                                      :transform rotation-css
                                      :margin-right "10px"}
                              :src "/img/high-res.png" :height 25 :width 25})
                    (if-let [entry-time (:created-at last-entry)] 
                      (str "Dashboard last updated " (.fromNow (js/moment (str (js/Date. entry-time)))))))))))))

(defcomponent deployment-peers [deployment owner]
  (render [_] 
          (p/panel
            {:header (om/build section-header-collapsible {:text "Deployment Peers"} {})
             :collapsible? true
             :bs-style "primary"}
            (if (and (:id deployment) 
                     (:up? deployment)) 
              (om/build peer-table (:peers deployment) {})
              (dom/div "Deployment has no pulse.")))))

(defn strip-catalog [catalog task-rename]
  (mapv (fn [entry]
          (-> entry 
              (update-in [:onyx/name] task-rename)
              (select-keys [:onyx/name :onyx/type :onyx/ident 
                            :onyx/medium :onyx/consumption 
                            :onyx/batch-size]))) 
          catalog))

(defn replace-in-workflow [workflow translation]
  (mapv (fn [[from to]]
          [(translation from) (translation to)])
        workflow))

(defn workflow->task-rename-map [workflow]
  (zipmap (distinct (flatten workflow)) 
          (map (comp keyword str) (range))))

(defn publicise-jobs [jobs]
  (mapv (fn [{:keys [workflow catalog] :as job}]
          (let [task-rename (workflow->task-rename-map workflow)] 
            (-> job
                (dissoc :pretty-workflow
                        :pretty-catalog
                        :tracking-id)
                (update-in [:workflow] replace-in-workflow task-rename)
                (update-in [:catalog] strip-catalog task-rename))))
        (vals jobs)))

(defn entries->log-dump [entries]
  (vec (sort-by :message-id (vals entries))))

(defn serialize [v]
  (vector (pr-str v)))

(defcomponent download-with-filename 
  "Gross hack to enable blobs to be saved with a particular filename.
  Unfortunately, the only way to provide a filename is with a link that gets clicked.
  window.open on the blob object-url doens't currently work"
  [{:keys [data filename]} owner {:keys [parent-ch]}]
  (did-mount [_]
             (.click (om/get-node owner))
             (put! parent-ch :done))
  (render [_]
          (dom/a {:href (blob/object-url! 
                          (blob/blob (serialize data)
                                     "application/octet-stream"))
                  :download filename} 
                 "Save")))

(defcomponent deployment-log-dump [{:keys [entries jobs id status] :as deployment} owner]
  (init-state [_] 
              {:download-type nil :download-ch (chan)})
  (will-mount [_]
              (go-loop []
                       (let [msg (<! (om/get-state owner :download-ch))]
                         (if (= msg :done)
                           (om/set-state! owner :download-type nil)))))
  (render-state [_ {:keys [download-type download-ch]}] 
                (dom/div
                  (case download-type 
                    :raw-dump (om/build download-with-filename 
                                        {:data {:deployment-status status
                                                :jobs jobs
                                                :log (entries->log-dump entries)}
                                         :filename (str "dump_" id "_raw.edn")}
                                        {:opts {:parent-ch download-ch}})
                    :stripped-dump (om/build download-with-filename 
                                             {:data {:deployment-status status
                                                     :jobs (publicise-jobs jobs)
                                                     :log (entries->log-dump entries)}
                                              :filename (str "dump_" id "_stripped.edn")}
                                             {:opts {:parent-ch download-ch}})
                    (dom/div))
                  (p/panel
                    {:header "Deployment Log Dump" 
                     :collapsible? true
                     :bs-style "primary"}
                    (t/table {:striped? true :bordered? false :condensed? true :hover? true}
                             (dom/thead (dom/tr (dom/th "Type") (dom/th)))
                             (dom/tbody
                               (dom/tr 
                                 (dom/td "Raw")
                                 (dom/td
                                   (dom/a {:on-click (fn [_] (om/set-state! owner :download-type :raw-dump))} 
                                          "Save"))) 
                               (dom/tr 
                                 (dom/td "Stripped of catalog parameterisations")
                                 (dom/td
                                   (dom/a {:on-click (fn [_] (om/set-state! owner :download-type :stripped-dump))}
                                          "Save")))))
                    "WARNING: We make a best effort attempt to strip catalog parameterisation and task names. " 
                    "If these may include private information, please inspect the :jobs value "
                    "in the dump before making it public."))))


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
