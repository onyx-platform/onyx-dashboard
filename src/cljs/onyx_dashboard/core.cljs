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
         :deployment {:tracking-id nil
                      :jobs []
                      :selected-job nil
                      :message-id-max nil
                      :entries {}}}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

; (defn stop-tracking! [deployment-id]
;   (chsk-send! [:deployment/track-cancel deployment-id]))

(defn start-tracking! [deployment-id]
  (let [tracking-id (uuid/make-random)] 
    (swap! app-state assoc :deployment {:tracking-id tracking-id
                                        :id deployment-id
                                        :entries {}})
    (chsk-send! [:deployment/track {:deployment-id deployment-id
                                    :tracking-id tracking-id}])
    false))

(defcomponent clojure-block [data owner]
  (render-state [_ _] (dom/pre (:input data)))
  (did-mount [_]
             (let [editor (.edit js/ace (om/get-node owner))]
               (.setOptions editor
                            (clj->js {:maxLines 15}))
               (.setMode (.getSession editor) "ace/mode/clojure")
               (.setHighlightActiveLine editor false)
               (.setHighlightGutterLine editor false)
               (.setReadOnly editor true)
               (set! (.-opacity (.-style (.-element (.-$cursorLayer (.-renderer editor))))) 0))))

(defcomponent select-deployment [{:keys [deployments deployment]} owner]
  (render [_] 
          (dom/div
            (b/toolbar {}
                       (apply (partial b/dropdown {:bs-style "primary" 
                                                   :title (or (:id deployment) 
                                                              "Deployments")})
                              (for [[id info] (reverse (sort-by (comp :created-at val) 
                                                                deployments))]
                                (b/menu-item {:key id
                                              :on-select (fn [_] 
                                                           ;(stop-tracking! id)
                                                           (start-tracking! id))} 
                                             id)))))))

(defn is-job-entry? [job-id entry]
  (if-let [entry-job-id (if (= (:fn entry) :submit-job)
                          (:id entry)
                          (:job-id entry))]
    (= entry-job-id job-id)))

(defcomponent deployment-entries [{:keys [selected-job entries message-id-max]} owner]
  (render [_]
          (let [num-displayed 100
                start-id (max 0 (- (inc message-id-max) num-displayed))
                displayed-msg-ids (range start-id (inc message-id-max))]
            (dom/pre
             (dom/h4 "Cluster Activity")
             (table {:striped? true :bordered? true :condensed? true :hover? true}
                    (dom/thead
                     (dom/tr
                      (dom/th "ID")
                      (dom/th "Time")
                      (dom/th "fn")))
                    (dom/tbody ;{:height "500px" :position "absolute" :overflow-y "scroll"}
                                        ; Entries may not exist if they have come in out of order from sente, 
                                        ; thus we only keep the not nil entries
                     (for [entry (keep entries (reverse displayed-msg-ids))] 
                       (dom/tr {:key (str "entry-" (:message-id entry))
                                :title (str (om/value entry))}
                               (dom/td (str (:id (:args entry))))
                               (dom/td (str (js/Date. (:created-at entry))))
                               (dom/td (str (:fn entry)))))))))))

(defn select-job [id]
  (println "Selecting job " id)
  (swap! app-state assoc-in [:deployment :selected-job] id))

(defcomponent job-selector [{:keys [selected-job jobs]} owner]
  (render [_]
          (dom/pre
           (dom/h4 "Jobs")
           (table {:striped? true :bordered? true :condensed? true :hover? true}
                  (dom/thead
                   (dom/tr
                    (dom/th "ID")
                    (dom/th "Time")))
                  (dom/tbody
                   (for [job (reverse (sort-by :created-at (vals jobs)))] 
                     (let [job-id (:id job)] 
                       (dom/tr {        ; make this a class
                                :style {:background-color (if (= job-id selected-job)
                                                            "lightblue")}}
                               (dom/td {:on-click (fn [_] (select-job job-id))} 
                                       (str job-id))
                               (dom/td {}
                                       (str (js/Date. (:created-at job))))))))))))

(defcomponent catalog-view [catalog owner]
  (render [_]
          (dom/pre
           (om/build ankha/collection-view catalog {:opts {:open? true}}))))

(defcomponent job-info [{:keys [selected-job jobs]} owner]
  (render [_]
          (if-let [job (and selected-job jobs (jobs selected-job))]
            (dom/div
             (dom/pre
              (dom/h4 "Catalog")
              (om/build clojure-block {:input (:pretty-catalog job)}))

             (dom/pre
              (dom/h4 "Workflow")
              (om/build clojure-block {:input (:pretty-workflow job)}))))))

(defn msg-controller [type msg]
  (swap! app-state 
         (fn [state]
           (if-let [tracking-id (:tracking-id msg)] 
             (cond (= tracking-id (get-in state [:deployment :tracking-id]))
                   (case type
                     ;:deployment/replica
                     ;(assoc-in state [:deployment :replica] recv-msg)
                     :job/completed-task
                     (do (println "Task completed: " msg)
                         state)
                     :job/submitted-job
                     (assoc-in state [:deployment :jobs (:id msg)] msg)
                     :job/entry
                     (update-in state [:deployment] (fn [deployment]
                                                      (-> deployment
                                                          (assoc :message-id-max (max (:message-id msg)
                                                                                      (:message-id-max deployment)))
                                                          (assoc-in [:entries (:message-id msg)] msg))))
                     state)
                   :else state)
             (if (= :deployment/listing type)
               (assoc-in state [:deployments] msg)
               state)))))

(defn event-handler [{:keys [event]}]
  (let [[msg-type msg] event]
    (case msg-type 
      :chsk/recv
      (let [[recv-type recv-msg] msg]
        (println "Recv event " event)
        (msg-controller recv-type recv-msg))
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
           (g/grid
            {}
            (om/build select-deployment app {})
            (dom/aside {}
                       (dom/nav (om/build job-selector (:deployment app) {}))
                       (dom/nav (om/build job-info (:deployment app) {}))
                       (dom/nav (om/build deployment-entries (:deployment app) {}))))))))
    app-state
    {:target (. js/document (getElementById "app"))}))
