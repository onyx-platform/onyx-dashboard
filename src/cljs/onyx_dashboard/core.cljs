(ns onyx-dashboard.core
  (:require [cljs.reader :refer [read-string]]
            [ankha.core :as ankha]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.grid :as g]
            [om-bootstrap.button :as b]
            [om-bootstrap.random :as r]
            [om-bootstrap.input :as i]
            [om-bootstrap.table :refer [table]]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [cljs-uuid.core :as uuid]
            [taoensso.sente  :as sente :refer [cb-success?]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go go-loop]]))

(enable-console-print!)

(defonce app-state 
  (atom {:ready? false
         :deployments {}
         :deployment {:jobs []
                      :selected-job nil
                      :entries []}}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

;; SETUP A CONTROLLER HERE SOMEWHERE FOR ALL THE SWAPS
; (defn stop-tracking! [deployment-id]
;   (chsk-send! [:deployment/track-cancel deployment-id]))

(defn start-tracking! [deployment-id]
  (swap! app-state assoc :deployment {:id deployment-id
                                      :entries []})
  (chsk-send! [:deployment/track deployment-id])
  false)

(defcomponent select-deployment [{:keys [deployments deployment]} owner]
  (render [_] 
          (dom/div 
            "Some deployment: "
            (b/toolbar {}
                       (apply (partial b/dropdown {:bs-style "primary" 
                                                   :title (or (:id deployment) 
                                                              "Deployments")})
                              (for [[id info] deployments]
                                (b/menu-item {:key id
                                              :on-select (fn [_] 
                                                           ;(stop-tracking! id)
                                                           (start-tracking! id))} 
                                             id)))))))

(defn entry-for-job? [job-id entry]
  (if-let [entry-job-id (if (= (:fn entry) :submit-job)
                          (:id entry)
                          (:job-id entry))]
    (= entry-job-id job-id)))

(defcomponent deployment-entries [{:keys [selected-job entries]} owner]
  (render [_]
          (let [filtered-entries (filter (partial entry-for-job? selected-job) 
                                         entries)] 
            (table {:striped? true :bordered? true :condensed? true :hover? true}
                   (dom/thead
                     (dom/tr
                       (dom/th "ID")
                       (dom/th "Time")
                       (dom/th "fn")))
                   (dom/tbody ;{:height "500px" :position "absolute" :overflow-y "scroll"}
                              (for [entry (reverse (sort-by :created-at entries))] 
                                (dom/tr {:title (str (om/value entry))}
                                        (dom/td (str (:id (:args entry))))
                                        (dom/td (str (js/Date. (:created-at entry))))
                                        (dom/td (str (:fn entry))))))))))

(defn select-job [id]
  (println "Selecting job " id)
  (swap! app-state assoc-in [:deployment :selected-job] id))

(defcomponent job-selector [{:keys [selected-job jobs]} owner]
  (render [_]
          (table {:striped? true :bordered? true :condensed? true :hover? true}
                 (dom/thead
                   (dom/tr
                     (dom/th "ID")
                     (dom/th "Time")))
                 (dom/tbody
                   (for [job (reverse (sort-by :created-at (vals jobs)))] 
                     (let [job-id (:id job)] 
                       (dom/tr {; make this a class
                                :style {:background-color (if (= job-id selected-job)
                                                            "lightblue")}}
                         (dom/td {:on-click (fn [_] (select-job job-id))} 
                                 (str job-id))
                         (dom/td {}
                                 (str (js/Date. (:created-at job)))))))))))

(defcomponent catalog-view [catalog owner]
  (render [_]
          (om/build ankha/collection-view catalog {:opts {:open? true}})))

(defcomponent job-info [{:keys [selected-job jobs]} owner]
  (render [_]
          ; Maybe only pass in job
          (let [job (jobs selected-job)] 
            (dom/div 
              (dom/div (str (om/value job)))
              (if-let [catalog (:catalog job)]
                (om/build catalog-view catalog {}))))))

(defn event-handler [{:keys [event]}]
  (let [[msg-type msg] event]
    (case msg-type 
      :chsk/recv
      (let [[recv-type recv-msg] msg]
        (println "Recv event " event)
        (case recv-type
          :deployment/replica
          (swap! app-state assoc-in [:deployment :replica] recv-msg)
          :deployment/listing
          (swap! app-state assoc :deployments recv-msg)
          :job/completed-task
          (println "Task completed: " recv-msg)
          :job/submitted-job 
          (swap! app-state assoc-in [:deployment :jobs (:id recv-msg)] recv-msg)
          :job/entry
          (swap! app-state update-in [:deployment :entries] conj recv-msg)
          (println "Unhandled recv-type: " recv-type recv-msg)))
      :chsk/state (when (:first-open? msg)
                    (chsk-send! [:deployment/get-listing])
                    (swap! app-state assoc :ready? true)
                    (println "First opened: " event)))))

(sente/start-chsk-router! ch-chsk event-handler)

(defn main []
  (om/root
    ankha/inspector
    app-state
    {:target (js/document.getElementById "ankha")})
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/div 
            (om/build select-deployment app {})
            (dom/aside {}
                       (dom/nav (om/build job-selector (:deployment app) {}))
                       (dom/nav (om/build job-info (:deployment app) {}))
                       (dom/nav (om/build deployment-entries (:deployment app) {})))))))
    app-state
    {:target (. js/document (getElementById "app"))}))
