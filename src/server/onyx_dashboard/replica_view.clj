(ns onyx-dashboard.replica-view
  (:require [onyx.log.zookeeper :as zk-onyx]
            [taoensso.timbre :as t]
            [clojure.core.async :refer [go >!! <!! <! >! chan timeout]]
            [onyx.log.commands.common :as common]
            [onyx.log.curator :as zk]
            [lib-onyx.log-subscriber :as ls]
            [com.stuartsierra.component :as component]))

(def zkclient  (zk/connect "127.0.0.1:2181"))

(defn tenancies [zk-client]
  (zk/children zk-client (zk-onyx/prefix-path)))

(defn pulses
  "Fetch a list of nodes that are currently registered on a tenancy-id"
  [zk-client tenancy-id]
  (zk/children zk-client (zk-onyx/pulse-path tenancy-id)))

(defn stats
  "Get "
  [zk-client entry]
  (:stat (zk/data zk-client (zk-onyx/prefix-path entry))))

(defn watch-for-changes
  "Provides a future that will be delivered upon when the
  specified path in Zookeeper is changed"
  [zk-client path t]
  (let [p (promise)]
    (go (<! (timeout t))
        (t/info "Tenancy watcher timeout reached")
        (deliver p true))
    (zk/children zk-client path
                 :watcher (fn [_]
                            (t/info "Tenancy watcher triggered")
                            (deliver p true))) p))

(defn watch-tenancies
  "cb is called with each onyx tenancy listed under /onyx.
  returns a complete listing with the form
  {:onyx/tenancy-id {:created-at ... :modified-at ...}}"
  [zk-client cb]
  (future
    (loop [root-changed (atom true)]
      (cb (into {} (comp
                    (map (juxt identity (partial stats zk-client)))
                    (map (fn [[child stat]]
                           (vector child {:created-at (java.util.Date. (:ctime stat))
                                          :modified-at (java.util.Date. (:mtime stat))}))))
                (zk/children zk-client zk-onyx/root-path)))
      @root-changed
      (t/info "Tenancy watcher reading again")
      (recur (watch-for-changes zk-client zk-onyx/root-path 5000)))))

(defmulti log-notifications
  (fn [replica-manager _ {:keys [replica entry] :as state}]
    (:fn entry)))

(defmethod log-notifications :default
  [replica-manager _ {:keys [replica entry] :as state}]
  (t/info entry))

(defmethod log-notifications :submit-job
  [replica-manager _ {:keys [replica entry]}])

(defrecord ReplicaManager [config] ;; {:peer-config {:onyx/tenancy-id :zookeeper/address}}
  component/Lifecycle
  (start [this]
    (let [{:keys [peer-config]} config]
      (assoc this :log-subscriber
             (ls/start-log-subscriber peer-config
                                      {:callback-fn log-notifications} ))))
  (stop [this]
    (let [{:keys [log-subscriber]} this]
      (try
        (ls/stop-log-subscriber log-subscriber)
        (catch Exception e
          (t/error "There was an exception shutting down the ReplicaManager"
                   e))))))

(defn replica-manager
  "Constructs a ReplicaManager for the given peer-config.
  All that is required in the way of configuration is:
  {:peer-config
    {:onyx/tenancy-id ...
     :zookeeper/address ...}}"
  [config]
  (map->ReplicaManager {:config config}))



;(def abc (-> (component/start (replica-manager {:peer-config {:onyx/tenancy-id "1"
                                                              ;:zookeeper/address "127.0.0.1:2181"}}))))

                                        ;(def abc (future (watch-tenancies zkclient clojure.pprint/pprint)))

                                        ;(zk/create zkclient "/onyx/3" :persistent? true)

                                        ;(future-cancel abc)
;; Root
;;;; Tenancy
;;;;;; Job
;;;;;; Nodes
;;;;;; Bla
