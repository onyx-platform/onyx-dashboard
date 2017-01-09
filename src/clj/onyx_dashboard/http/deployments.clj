(ns onyx-dashboard.http.deployments
  (:require [clojure.core.async :refer [close! chan go-loop <!]]
            [com.stuartsierra.component :as component]
            [onyx.log.curator           :as zk]
            [onyx.log.zookeeper         :as zk-onyx]
            [taoensso.timbre :as timbre :refer [info error spy]]))

(defn zk-deployment-entry-stat [zk-client entry]
  (:stat (zk/data zk-client (zk-onyx/prefix-path entry))))

(defn distribute-deployment-listing [into-all-br! listing]
  (into-all-br! [:deployment/listing listing]))

; stops when ZK unreachable
(defn tenancies-watch [into-all-br! zk-client deployments]
  (try
    (loop []
      (if-not (Thread/interrupted)
        (try
          (if-let [children (zk/children zk-client zk-onyx/root-path)]
            (let [dps (->> children
                           (map (juxt identity
                                      (partial zk-deployment-entry-stat zk-client)))
                           (map (fn [[child stat]]
                                  (vector child
                                          {:created-at  (java.util.Date. (:ctime stat))
                                           :modified-at (java.util.Date. (:mtime stat))})))
                           (into {}))]
                (when (not= @deployments dps)
                      (do (println "Updated Onyx tenancies list")
                          (println dps)
                          (->> dps
                               (reset! deployments)
                               (distribute-deployment-listing into-all-br!))))
                (Thread/sleep 1000))
            (do
              (println (format "Could not find tenancies at %s. Retrying in 1s." zk-onyx/root-path))
              (Thread/sleep 1000)))
          ; if we connected to early try again
          (catch org.apache.zookeeper.KeeperException$NoNodeException e
                 (do
                   (println (format "Could not find tenancy at %s. Retrying in 1s." zk-onyx/root-path))
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

(defn start-tenancies-watch [sente zk deployments]
  (future (tenancies-watch (-> sente :into-all-br!)
                             (-> zk    :zk-client)
                             deployments)))
; Responsibilities
; - watch for Onyx tenancies list
; - restart watch after zk reconnected
(defrecord Deployments []
  component/Lifecycle
  (start [{:keys [channels sente zk] :as component}]
    (println "Starting Deployments (tenancies)")
    (let [into-br! (-> sente :into-br!)
          cmds-ch  (-> channels :cmds-deployments-ch)
          deployments     (atom {})
          tenancies-watch nil
          cmds (go-loop []
                        (when-let [[cmd data] (<! cmds-ch)]
                               (do (case cmd
                                     :deployment/listing
                                      (let [user-id  (-> data :user-id)
                                            _ (println "Send deployments (tenancies)... " user-id)]
                                        (into-br! user-id [:deployment/listing @deployments]))

                                      :restart
                                      (do (when tenancies-watch (future-cancel tenancies-watch))
                                          (assoc component
                                            :tenancies-watch (start-tenancies-watch sente zk deployments))))
                                   (recur))))]
      (assoc component
        :deployments     deployments
        :tenancies-watch tenancies-watch
        :cmds            cmds)))

  (stop [{:keys [tenancies-watch cmds] :as component}]
    (println "Stopping Deployments (tenancies)")

    (when cmds              (close! cmds))
    (when tenancies-watch (future-cancel tenancies-watch))

    (assoc component :cmds              nil
                     :deployments       nil
                     :tenancies-watch nil)))

(defn new-deployments []
  (map->Deployments {}))
