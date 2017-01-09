(ns onyx-dashboard.core
  (:require [cljs.reader :refer [read-string]]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [ankha.core :as ankha]   ; required only for dev
            ; om
            [om.core       :as om  :include-macros true]
            [om-tools.dom  :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            ; om bootstrap
            [om-bootstrap.grid   :as g]
            [om-bootstrap.random :as r]
            ; dashboard
            [onyx-dashboard.components.main       :refer [main-component]]
            [onyx-dashboard.controllers.websocket :refer [msg-controller]]
            ; websocket
            [taoensso.sente  :as sente :refer [cb-success?]]
            [taoensso.sente.packers.transit :as sente-transit])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go go-loop]]))

(enable-console-print!)

(defonce app-state
  (atom {:ui/select-deployment? true
         :ui/curr-page :page/tenancies
         :ready? false
         :deployments {}
         :zk-up? true}))

(def packer (sente-transit/get-flexi-packer :edn))

(let [{:keys [chsk ch-recv send-fn state] :as frontend-ws}
      (sente/make-channel-socket! "/chsk" {:type :auto :packer packer})]
  (def chsk         chsk)
  (def ch-chsk      ch-recv)
  (def into-server! send-fn)
  (def chsk-state   state))

(defn sente-event-handler [{:keys [event]}]
  (let [[msg-type msg] event]
    (case msg-type 
      :chsk/recv
        (swap! app-state (partial msg-controller msg))

      :chsk/state
      (when (:first-open? msg)
            (into-server! [:deployment/get-listing])
            (swap! app-state assoc :ready? true)
            (println "First opened:" event))

      (println "Unhandled event:" event))))

(sente/start-chsk-router! ch-chsk sente-event-handler)

(defn main [is-dev?]
  (swap! app-state merge {:ui/is-dev? is-dev?})
  (when is-dev?
    (om/root ankha/inspector app-state {:target (js/document.getElementById "ankha")}))
  (om/root main-component
           app-state
           {:shared {:into-server! into-server!
                     :api-ch (chan)}
            :target (. js/document (getElementById "app"))}))
