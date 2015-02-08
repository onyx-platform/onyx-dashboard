(ns onyx-dashboard.http.sente
  (:require [clojure.core.async :refer [close!]]
            [com.stuartsierra.component :as component]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.sente :refer [make-channel-socket!]]))

(defn user-id-fn [req]
  "generates unique ID for request"
  (let [uid (get-in req [:cookies "ring-session" :value])]
    (println "Connected: " (:remote-addr req) uid)
    uid))

(def packer (sente-transit/get-flexi-packer :edn))

(defrecord Sente []
  component/Lifecycle
  (start [component]
    (println "Starting Sente")
    (let [x (make-channel-socket! {:user-id-fn user-id-fn :packer packer})]
      (assoc component
        :ring-ajax-post (:ajax-post-fn x)
        :ring-ajax-get-or-ws-handshake (:ajax-get-or-ws-handshake-fn x)
        :ch-chsk (:ch-recv x)
        :chsk-send! (:send-fn x)
        :connected-uids (:connected-uids x))))

  (stop [component]
    (println "Stopping Sente")
    (close! (:ch-chsk component))
    (assoc component :server nil)))

(defn sente []
  (map->Sente {}))

