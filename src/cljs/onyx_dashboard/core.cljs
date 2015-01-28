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
            [onyx-dashboard.controllers.api :refer [api-controller]]
            [taoensso.sente  :as sente :refer [cb-success?]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go go-loop]]))

(enable-console-print!)

(defonce app-state 
  (atom {:ready? false
         :deployments {}
         ; Maybe these should be in component local state
         ; The advantage is they can be controlled from elsewhere but it does
         ; complicate the code a bit and may not gain much for now
         ; Component state will also handle lifecycle stuff inc default
         ; state when new job is loaded (assuming they're keyed differently)
         :visible {:job true
                   :catalog true
                   :workflow true
                   :task false
                   :peers false
                   :statistics true
                   :log-entries true}
         :deployment {:tracking-id nil
                      :jobs []
                      :selected-job nil
                      :message-id-max nil
                      :entries {}}}))

(def left-bar-width 350)

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

; (defn stop-tracking! [deployment-id]
;   (chsk-send! [:deployment/track-cancel deployment-id]))


;; Controller type stuff to split out

(defn start-tracking! [deployment-id]
  (let [tracking-id (uuid/make-random)] 
    (swap! app-state assoc :deployment {:tracking-id tracking-id
                                        :id deployment-id
                                        :entries {}})
    (chsk-send! [:deployment/track {:deployment-id deployment-id
                                    :tracking-id tracking-id}])
    false))

(defn select-job [id]
  (swap! app-state assoc-in [:deployment :selected-job] id))

(defn msg-controller [type msg]
  ; success notification currently notifys about bad tracking ids
  ; probably going to need a better session management check
  ; Disable for now as it's a bit distracting during dev
  ;(success-notification type)
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
        ;(println "Recv event " event)
        (msg-controller recv-type recv-msg))
      :chsk/state (when (:first-open? msg)
                    (chsk-send! [:deployment/get-listing])
                    (swap! app-state assoc :ready? true)
                    (println "First opened: " event)))))

;; TODO
;; Components to start splitting out

(defcomponent clojure-block [data owner]
  (render-state [_ _] (dom/div (:input data)))

  (did-mount [_]
             (let [editor (.edit js/ace (om/get-node owner))]
               (.setOptions editor (clj->js {:maxLines 15}))
               (.setMode (.getSession editor) "ace/mode/clojure")
               (.setHighlightActiveLine editor false)
               (.setHighlightGutterLine editor false)
               (.setReadOnly editor true)
               (set! (.-opacity (.-style (.-element (.-$cursorLayer (.-renderer editor))))) 0)
               (om/set-state! owner :dom-editor editor)))
  (will-unmount [_]
                ; not sure if this helps with lost node issues yet
                (let [editor (om/get-state owner :dom-editor)] 
                  (.destroy editor)
                  #_(.. editor container remove))))

(defcomponent select-deployment [{:keys [deployments deployment]} owner]
  (render [_] 
          (dom/div {:class "btn-group btn-group-justified" :role "group"} 
                     (apply (partial b/dropdown {:bs-style "info" 
                                                 :title (or (:id deployment) "Select Deployment")})
                            (for [[id info] (reverse (sort-by (comp :created-at val) 
                                                              deployments))]
                              (b/menu-item {:key id
                                            :on-select (fn [_] 
                                                         ;(stop-tracking! id)
                                                         (start-tracking! id))} 
                                           id))))))

(defn is-job-entry? [job-id entry]
  (if-let [entry-job-id (if (= (:fn entry) :submit-job)
                          (:id entry)
                          (:job-id entry))]
    (= entry-job-id job-id)))

(defcomponent log-entry-row [entry owner]
  (render [_] 
          (dom/tr {:key (str "entry-" (:message-id entry))
                   :title (str (om/value entry))}
                  (dom/td (str (:id (:args entry))))
                  (dom/td (str (:fn entry)))
                  (dom/td (.fromNow (js/moment (str (js/Date. (:created-at entry)))))))))

(defcomponent section-header [{:keys [text visible type]} owner]
  (render [_]
          ; should this handler should be on the panel header itself? There's
          ; some unclickable margin / buffer space in there
          (dom/div {:on-click (fn [_] (put! (om/get-shared owner :api-ch) 
                                            [:visibility type (not visible)]))}
                   (dom/h4 {:class "unselectable"} 
                           text
                           (dom/i {:style {:float "right"}
                                   :class (if visible 
                                            "fa fa-caret-square-o-up"
                                            "fa fa-caret-square-o-down")})))))

(defcomponent log-entries-table [{:keys [entries visible]} owner]
  (render [_]
          (p/panel
            {:header (om/build section-header 
                               {:text "Cluster Activity" 
                                :visible visible 
                                :type :log-entries} {})
             :bs-style "primary"}
           (if visible
             (table {:striped? true :bordered? true :condensed? true :hover? true}
                    (dom/thead (dom/tr (dom/th "ID") (dom/th "fn") (dom/th "Time")))
                    (dom/tbody (map (fn [entry]
                                      (om/build log-entry-row entry {}))
                                    entries)))))))

(defcomponent job-selector [{:keys [selected-job jobs]} owner]
  (render [_]
          (p/panel {:header (dom/h4 "Jobs") :bs-style "primary" }
                   (table {:striped? true :bordered? false :condensed? true :hover? true}
                          ;;                   (dom/thead (dom/tr (dom/th "ID") (dom/th "Time")))
                          (dom/tbody
                            (for [job (reverse (sort-by :created-at (vals jobs)))] 
                              (let [job-id (:id job)] 
                                (println job-id selected-job)
                                (dom/tr {:style {:background-color 
                                                 (if (= job-id selected-job)
                                                   "lightblue") }
                                         :class (str "job-entry")}
                                        (dom/td {:on-click (fn [_] (select-job job-id))} 
                                                (str job-id))
                                        (dom/td {}
                                                (.fromNow (js/moment (str (js/Date. (:created-at job))))))))))))))

(defcomponent catalog-view [catalog owner]
  (render [_]
          (dom/div (om/build ankha/collection-view catalog {:opts {:open? true}}))))

(defcomponent job-info [{:keys [deployment visible]} owner]
  (render [_]
          (let [{:keys [selected-job jobs]} deployment] 
            (if-let [job (and selected-job jobs (jobs selected-job))]
              (dom/div
                (p/panel
                  {:header (om/build section-header 
                                     {:text "Job Management" 
                                      :visible (:job-management visible) 
                                      :type :job-management} 
                                     {})
                   :bs-style "primary"}
                  (g/grid {} 
                          ; Show operations based on current status of job)
                          (g/row {} 
                                 (g/col {:xs 4 :md 2} (dom/i {:class "fa fa-heartbeat"} "Running"))
                                 (g/col {:xs 4 :md 2} (dom/i {:class "fa fa-repeat"} "Restart"))
                                 (g/col {:xs 4 :md 2} (dom/i {:class "fa fa-times-circle-o"} "Kill")))))

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
                  (om/build clojure-block {:input (:pretty-workflow job)}))))))))

(defn success-notification [msg]
  (js/noty (clj->js {:text msg
                     :type "success"
                     :layout "bottomRight"
                     :timeout 8000
                     :closeWith ["click" "button"]})))

(sente/start-chsk-router! ch-chsk event-handler)

(defn deployment->latest-log-entries [{:keys [entries message-id-max] :as deployment}]
  (let [num-displayed 100
        start-id (max 0 (- (inc message-id-max) num-displayed))
        displayed-msg-ids (range start-id (inc message-id-max))]
    (keep entries (reverse displayed-msg-ids))))

(defcomponent main-component [{:keys [deployment visible] :as app} owner]
  (did-mount [_] 
             (let [api-ch (om/get-shared owner :api-ch)] 
               (go-loop []
                        (let [msg (<! api-ch)]
                          (println "Handling msg: " msg)
                          (om/transact! app (partial api-controller msg))
                          (recur)))))

  (render-state [_ {:keys [api-chan]}]
                (dom/div 
                  (r/page-header {:class "page-header" :style {:text-align "center"}} 
                                "Onyx Dashboard")
                  (g/grid {}
                          (g/row {}
                                 (g/col {:xs 6 :md 4 ;:md-offset 
                                         }
                                        (dom/div {:class "left-nav-deployment"} 
                                                 (om/build select-deployment app {}))
                                        (dom/div {} 
                                                 (om/build job-selector deployment {})))
                                 (g/col {:xs 12 :md 8; :md-push 1 
                                         }
                                        (dom/div (om/build job-info {:deployment deployment
                                                                     :visible visible} {})
                                                 (om/build log-entries-table {:entries (deployment->latest-log-entries deployment)
                                                                              :visible (:log-entries visible)} {}))))))))

(defn main []
  (om/root ankha/inspector app-state {:target (js/document.getElementById "ankha")})
  (om/root main-component app-state {:shared {:api-ch (chan)}
                                     :target (. js/document (getElementById "app"))}))
