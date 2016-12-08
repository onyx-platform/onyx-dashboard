(ns onyx-dashboard.http.deployments
  (:require [clojure.core.async :refer [close! chan go-loop <!]]
            [com.stuartsierra.component :as component]
            [onyx.log.curator           :as zk]
            [onyx.log.zookeeper         :as zk-onyx]))

(defn zk-deployment-entry-stat [zk-client entry]
  (:stat (zk/data zk-client (zk-onyx/prefix-path entry))))

(defn distribute-deployment-listing [send-all-fn! listing]
  (send-all-fn! [:deployment/listing listing]))

; stops when ZK unreachable
(defn deployments-watch [send-all-fn! zk-client deployments]
  (try
    (loop []
      (if-not (Thread/interrupted)
        (try
          (if-let [children (zk/children zk-client zk-onyx/root-path)]
            (do (->> children
                     (map (juxt identity
                                (partial zk-deployment-entry-stat zk-client)))
                     (map (fn [[child stat]]
                            (vector child
                                    {:created-at  (java.util.Date. (:ctime stat))
                                     :modified-at (java.util.Date. (:mtime stat))})))
                     (into {})
                     (reset! deployments)
                     (distribute-deployment-listing send-all-fn!))
                (Thread/sleep 1000))
            (do
              (println (format "Could not find deployments at %s. Retrying in 1s." zk-onyx/root-path))
              (Thread/sleep 1000)))
          ; if we connected to early try again
          (catch org.apache.zookeeper.KeeperException$NoNodeException e
                 (do
                   (println (format "Could not find deployments at %s. Retrying in 1s." zk-onyx/root-path))
                   (Thread/sleep 1000)))))
      (recur))
    (catch java.lang.IllegalStateException e)
    (catch java.lang.InterruptedException  e)
    (catch org.apache.zookeeper.KeeperException$ConnectionLossException e
      (println (format "ZK connection lost at %s. Deployments watch stopped." zk-onyx/root-path)))
    (catch Throwable t
      (println "Error :" t))
    )
  )

(defn start-deployments-watch [sente zk deployments]
  (future (deployments-watch (-> sente :send-mult)
                             (-> zk    :zk-client)
                             deployments)))
; Responsibilities
; - watch for deployments
; - restart watch after zk reconnected
(defrecord Deployments []
  component/Lifecycle
  (start [{:keys [channels sente zk] :as component}]
    (println "Starting Deployments")
    (let [into-br (-> sente :chsk-send!)
          cmds-ch (-> channels :cmds-deployments-ch)
          deployments       (atom {})
          deployments-watch nil
          cmds (go-loop []
                        (when-let [[cmd data] (<! cmds-ch)]
                               (do (case cmd
                                     :deployment/listing
                                      (let [user-id  (-> data :user-id)]
                                        (into-br user-id [:deployment/listing @deployments]))

                                      :restart
                                      (do (when deployments-watch (future-cancel deployments-watch))
                                          (assoc component
                                            :deployments-watch (start-deployments-watch sente zk deployments))))
                                   (recur))))]
      (assoc component
        :deployments       deployments
        :deployments-watch deployments-watch
        :cmds              cmds)))

  (stop [{:keys [deployments-watch cmds] :as component}]
    (println "Stopping Deployments")

    (when cmds              (close! cmds))
    (when deployments-watch (future-cancel deployments-watch))

    (assoc component :cmds              nil
                     :deployments       nil
                     :deployments-watch nil)))

(defn new-deployments []
  (map->Deployments {}))
