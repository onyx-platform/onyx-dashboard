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
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs-uuid.core :as uuid]
            [taoensso.sente  :as sente :refer (cb-success?)])
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"
                          :replica {}}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(def cluster-id "WHATEV")

(defn event-handler [{:keys [event]}]
  (let [[msg-type msg] event]
    (case msg-type 
      :chsk/recv
      (println "Recv event " event)
      :chsk/state (when (:first-open? msg)
                    (chsk-send! [:cluster/track cluster-id])
                    (println "First opened: " event)))))

; (defn- event-handler [{:keys [event]}]
;   (match event
;          [:chsk/state new-state] 
;          (match [new-state]
;                 [{:first-open? true}] 
;                 (chsk-send! [:onyx.job/list])
;                 :else 
;                 (println "Unmatched state change: " new-state))
;          [:chsk/recv payload] (handle-payload payload)
;          :else (print "Unmatched event: %s" event)))

(sente/start-chsk-router! ch-chsk event-handler)

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 (:text app)))))
    app-state
    {:target (. js/document (getElementById "app"))}))
