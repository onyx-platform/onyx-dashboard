(ns onyx-dashboard.core
  (:require [cljs.reader :refer [read-string]]
            [ankha.core :as ankha]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.grid :as g]
            [om-bootstrap.random :as r]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [onyx-dashboard.components.main :refer [main-component]]
            [onyx-dashboard.controllers.websocket :refer [msg-controller]]
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
                   :job-management true
                   :catalog true
                   :workflow true
                   :tasks true
                   :peers true
                   :statistics true
                   :log-entries true}
         :deployment {:tracking-id nil
                      :jobs []
                      :selected-job nil
                      :view-index nil
                      :message-id-max nil
                      :entries {}}}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn sente-event-handler [{:keys [event]}]
  (let [[msg-type msg] event]
    (case msg-type 
      :chsk/recv
      (swap! app-state (partial msg-controller msg))
      :chsk/state (when (:first-open? msg)
                    (chsk-send! [:deployment/get-listing])
                    (swap! app-state assoc :ready? true)
                    (println "First opened: " event)))))

(sente/start-chsk-router! ch-chsk sente-event-handler)

(defn main []
  (om/root ankha/inspector app-state {:target (js/document.getElementById "ankha")})
  (om/root main-component app-state {:shared {:chsk-send! chsk-send!
                                              :api-ch (chan)}
                                     :target (. js/document (getElementById "app"))}))
