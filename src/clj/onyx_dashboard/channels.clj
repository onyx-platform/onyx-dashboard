(ns onyx-dashboard.channels
  (:require [clojure.core.async :refer [close! chan]]
            [com.stuartsierra.component :as component]))

; Responsibilities
; - hold channels
; Useable to make duplex comunication between components
; CompA -> chan1 -> CompB
; CompB <- chan2 <- CompB
(defrecord Channels []
  component/Lifecycle
  (start [component]
    (println "Starting Channels")
    (let [cmds-deployments-ch (chan 100)]

      (assoc component
        :cmds-deployments-ch cmds-deployments-ch)))

  (stop [{:keys [cmds-deployments-ch] :as component}]
    (println "Stopping Channels")
    (when cmds-deployments-ch (close! cmds-deployments-ch))
    (assoc component :cmds-deployments-ch nil)))

(defn new-channels []
  (map->Channels {}))
