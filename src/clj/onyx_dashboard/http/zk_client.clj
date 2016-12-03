(ns onyx-dashboard.http.zk-client
  (:require [clojure.core.async :refer [chan timeout thread <!! >!! alts!! go-loop <! >! close! go]]
            [com.stuartsierra.component :as component]
            [onyx.log.curator :as zk]
            [taoensso.timbre :as timbre :refer [info error spy]])
  (:import [java.util.concurrent TimeUnit]
           [org.apache.zookeeper CreateMode]
           [org.apache.zookeeper
            KeeperException
            KeeperException$NoNodeException
            KeeperException$NodeExistsException
            KeeperException$Code
            Watcher]
           [org.apache.curator.test TestingServer]
           [org.apache.zookeeper.data Stat]
           [org.apache.curator.framework CuratorFrameworkFactory CuratorFramework]
           [org.apache.curator.framework.api CuratorWatcher PathAndBytesable Versionable GetDataBuilder
            SetDataBuilder DeleteBuilder ExistsBuilder GetChildrenBuilder Pathable Watchable]
           [org.apache.curator.framework.state ConnectionStateListener ConnectionState]
           [org.apache.curator.framework.imps CuratorFrameworkState]
           [org.apache.curator RetryPolicy]
           [org.apache.curator.retry RetryOneTime BoundedExponentialBackoffRetry]))
  
; connection with fail fast settings
; retry only 1
; try to connect up to 5s
(defn ^CuratorFramework connect-1-retry
  ([connection-string]
   (connect-1-retry connection-string ""))
  ([connection-string ns]
   (connect-1-retry connection-string ns
            (RetryOneTime. 5000)))
  ([connection-string ns ^RetryPolicy retry-policy]
   (doto
       (.. (CuratorFrameworkFactory/builder)
           (namespace ns)
           (connectString connection-string)
           (retryPolicy retry-policy)
           (connectionTimeoutMs  5000)   ;  5s
           (sessionTimeoutMs    10000)   ; 10s
           (build))
     .start)))

(defn try-connect [zk-client]
  (println "Trying connect ZK 5s ...")
  (.. zk-client
      (blockUntilConnected 5 TimeUnit/SECONDS)))

; conn reconnect
(defn until-connected [zk-client restart-ch]
  (future (when-let [_ (<!! restart-ch)]
                    (loop []
                          (or (try-connect zk-client)
                              (recur))))))

(defn as-connection-listener [f]
  (reify ConnectionStateListener
    (stateChanged [_ zk-client newState]
      (f newState))))

(defn add-conn-watcher [zk-client listener-fn]
  (let [listener (as-connection-listener listener-fn)]
        (.. zk-client
            getConnectionStateListenable
            (addListener listener))
        listener))

(defn remove-conn-watcher [zk-client listener]
  (println "Removing listener" listener)
  (.. zk-client
      getConnectionStateListenable
      (removeListener listener)))

; add conn listener and notify state change
(defn notify-console [zk-client zk-state channels]
  (add-conn-watcher zk-client 
                    (fn [newState] 
                        (println "ZK connection state:" (str newState))
                        (reset! zk-state newState)

                        (when (or (= newState ConnectionState/RECONNECTED) (= newState ConnectionState/CONNECTED))
                              (go (>! (-> channels :cmds-deployments-ch) [:restart]))))))


; send connection status into browser
(defn zk-state->browser [into-br user-id newState] 
  (let [msg (condp = newState
                   ConnectionState/CONNECTED   {:zk-up? true}
                   ConnectionState/RECONNECTED {:zk-up? true}
                   ConnectionState/LOST        {:zk-up? false}
                   nil)]
        (when msg
              (do 
                (into-br user-id [:deployment/zk-conn-state msg])
                (println "Notify browser that ZK conn:" (str newState))))))

; add conn listener and notify state change
(defn notify-browser [zk-client into-br user-id]
  (add-conn-watcher zk-client 
                    (fn [newState] 
                        (zk-state->browser into-br user-id newState))))

; needs restart on conn lost
(defn notify-restarter [zk-client restart-ch]
  (add-conn-watcher zk-client 
                    (fn [newState]
                        ; try connect in bg when connection lost 
                        (when (= ConnectionState/LOST newState)
                              (go (>! restart-ch true))))))
                                        
; Responsibilities
; - watch Zookeeper connection
; - notify browser about connection state
; - notify console about connection state
; - notify restarter to start trying reconnect
; - notify to restart refresh-deployments-watch when connection reconnected
(defrecord ZKClient [peer-config]
  component/Lifecycle
  (start [{:keys [channels sente] :as component}]
    (println "Starting ZKClient")
    (let [into-br (-> sente :chsk-send!)

          ; ZK connection client
          zk-client (connect-1-retry (:zookeeper/address peer-config))
          zk-state (atom ConnectionState/LOST)
          ; show connection status on console
          ; force other components to restart after reconnect
          nc (notify-console zk-client zk-state channels)

          ; try connect when connection lost
          restarter-ch (chan 100)
          restarter    (until-connected  zk-client restarter-ch)
          nr           (notify-restarter zk-client restarter-ch)

          ; show conn status on launched dashboards
          nb (atom {})

          ; react on events from other components
          notify-zc-ch (chan 100)
          notify-zc (go-loop []
                        (when-let [[cmd data] (<! notify-zc-ch)]
                          (do 
                            (case cmd
                                  :attach-browser-notify     
                                  (let [user-id  (-> data :user-id)
                                        ; attach notifier
                                        listener (notify-browser zk-client into-br user-id)]
                                        ; save listener to be able to remove it during close components phase
                                        (swap! nb assoc (keyword user-id) listener)
                                        ; send curent zk conn state into browser 
                                        (zk-state->browser  into-br 
                                                            user-id 
                                                            @zk-state))
                                  :remove-browser-notify
                                  (let [user-id  (-> data :user-id keyword)
                                        listener (-> @nb user-id)]
                                        (when listener (remove-conn-watcher zk-client listener)))

                                  :browser-refresh-zk-conn 
                                  (let [user-id  (-> data :user-id keyword)]
                                        (zk-state->browser into-br
                                                           user-id
                                                           @zk-state))

                                  )
                            (recur))))

          ; try connect first time
          ; keep retrying if failed
          _ (or (try-connect     zk-client)
                (>!! restarter-ch true))

          ]
          (assoc component
                 :zk-client    zk-client
                 :zk-state     zk-state
                 :nc           nc
                 :nb           nb
                 :restarter-ch restarter-ch
                 :restarter    restarter
                 :nr           nr
                 :notify-zc-ch notify-zc-ch  
                 :notify-zc    notify-zc)))

  (stop [{:keys [zk-client nc nb nr notify-zc-ch notify-zc restarter-ch restarter] :as component}]
    (println "Stopping ZKClient")
    (when notify-zc    (close! notify-zc))
    (when notify-zc-ch (close! notify-zc-ch))
    (when restarter-ch (close! restarter-ch))
    (when restarter    (future-cancel restarter))

    (when nc (remove-conn-watcher zk-client nc))
    (when nr (remove-conn-watcher zk-client nr))

    (when nb (doseq [nb_ (vals @nb)]
                    (remove-conn-watcher zk-client nb_))) 
    (reset! nb {})
    (when (.. zk-client isStarted) 
          (zk/close zk-client))
    (assoc component  :zk-client    nil
                      :zk-state     nil
                      :nc           nil 
                      :nb           nil
                      :nr           nil
                      :notify-zc-ch nil
                      :notify-zc    nil
                      :restarter-ch nil 
                      :restarter    nil)))

(defn new-zk-client [peer-config]
  (map->ZKClient {:peer-config peer-config}))