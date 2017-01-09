(ns onyx-dashboard.http.sente
  (:require [clojure.core.async :refer [close!]]
            [com.stuartsierra.component :as component]
            [taoensso.sente.server-adapters.http-kit]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.sente :refer [make-channel-socket!]]))

(defn user-id-fn [req]
  "generates unique ID for request"
  (let [uid (get-in req [:cookies "ring-session" :value])]
    (println "Connected: " (:remote-addr req) uid)
    uid))

(def packer (sente-transit/get-flexi-packer :edn))

(defn into-all-browsers! [into-br! connected-uids msg]
  (doseq [uid (:any @connected-uids)]
    (into-br! uid msg)))

; Responsibilities
; create websocket for backend
; create functions for send/receive data via websocket
(defrecord Sente []
  component/Lifecycle
  (start [component]
    (println "Starting Sente")
    (let [x (make-channel-socket!
             taoensso.sente.server-adapters.http-kit/http-kit-adapter
             {:user-id-fn user-id-fn :packer packer})

          ring-ajax-post                (-> x :ajax-post-fn)
          ring-ajax-get-or-ws-handshake (-> x :ajax-get-or-ws-handshake-fn)
          ch-chsk                       (-> x :ch-recv)
          into-br!                      (-> x :send-fn)
          connected-uids                (-> x :connected-uids)

          into-all-br! (partial into-all-browsers!
                                into-br!
                                connected-uids)]
      (assoc component
        :ring-ajax-post ring-ajax-post
        :ring-ajax-get-or-ws-handshake ring-ajax-get-or-ws-handshake
        :ch-chsk        ch-chsk
        :into-br!       into-br!
        :connected-uids connected-uids
        :into-all-br!   into-all-br!)))

  (stop [component]
    (println "Stopping Sente")
    (close! (-> component :ch-chsk))
    (assoc component :server nil)))

(defn sente []
  (map->Sente {}))

