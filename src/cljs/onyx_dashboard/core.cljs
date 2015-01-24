(ns onyx-dashboard.core
  (:require [cljs.reader :refer [read-string]]
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
         :deployment {:id nil :replica nil}}))

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
  (swap! app-state assoc-in [:deployment :id] deployment-id)
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
          (println "Unhandled recv-type: " recv-type)))
      :chsk/state (when (:first-open? msg)
                    (chsk-send! [:deployment/get-listing])
                    (swap! app-state assoc :ready? true)
                    (println "First opened: " event)))))

(sente/start-chsk-router! ch-chsk event-handler)

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 "Select a deployment")
          (om/build select-deployment app {}))))
    app-state
    {:target (. js/document (getElementById "app"))}))
