(ns onyx-dashboard.onyx-deployment
  (:require [fipp.edn :refer [pprint] :rename {pprint fipp}]
            [onyx.system :as system :refer [onyx-client]]
            [onyx.extensions :as extensions]
            [onyx.api]
            [onyx.log.zookeeper :as zk-onyx]
            [onyx.log.commands.common :as common]
            [zookeeper :as zk]
            [clojure.core.async :refer [chan timeout thread <!! alts!!]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre :refer [info error spy]]))

(defn kill-job [peer-config deployment-id {:keys [id] :as job-info}]
  (onyx.api/kill-job (assoc peer-config :onyx/id deployment-id)
                     id))

(defn start-job [peer-config deployment-id {:keys [catalog workflow task-scheduler] :as job-info}]
  (onyx.api/submit-job (assoc peer-config :onyx/id deployment-id)
                       {:catalog catalog
                        :workflow workflow
                        :task-scheduler task-scheduler}))

(defn restart-job [peer-config deployment-id job-info]
  (kill-job peer-config deployment-id job-info)
  (start-job peer-config deployment-id job-info))

(defn zk-deployment-entry-stat [client entry]
  (:stat (zk/data client (zk-onyx/prefix-path entry))))

(defn distribute-deployment-listing [send-all-fn! listing]
  (send-all-fn! [:deployment/listing listing]))

(defn deployment-pulses [client deployment-id]
  (zk/children client (zk-onyx/pulse-path deployment-id)))

(defn refresh-deployments-watch [send-all-fn! zk-client deployments]
  (if-let [children (zk/children zk-client 
                                 zk-onyx/root-path 
                                 :watcher 
                                 (fn [_] (refresh-deployments-watch send-all-fn! zk-client deployments)))]
    (->> children
         (map (juxt identity 
                    (partial zk-deployment-entry-stat zk-client)))
         (map (fn [[child stat]]
                (vector child
                        {:created-at (java.util.Date. (:ctime stat))
                         :modified-at (java.util.Date. (:mtime stat))})))
         (into {})
         (reset! deployments)
         (distribute-deployment-listing send-all-fn!))
    (do
      (println (format "Could not find deployments at %s. Retrying in 1s." zk-onyx/root-path))
      (Thread/sleep 1000)
      (recur send-all-fn! zk-client deployments))))

(defn peer-allocated-to-task [job-allocations job-id peer-id]
  (ffirst 
    (filter (fn [[task-id peer-ids]]
              (not-empty (filter #{peer-id} peer-ids))) 
            job-allocations)))

(defn task-allocated-to-peer [allocations peer-id]
  (first
    (keep (fn [job-id] 
            (if-let [task-id (peer-allocated-to-task (allocations job-id) job-id peer-id)]
              {:job-id job-id :task-id task-id}))
          (keys allocations))))

(def freshness-timeout 100)

(defn apply-log-entry [send-fn! tracking-id entry replica]
  (try 
    (extensions/apply-log-entry entry replica)
    (catch Throwable t
      (send-fn! [:deployment/log-replay-crash {:tracking-id tracking-id :error (str t)}])
      nil)))

(defmulti log-notifications 
  (fn [send-fn! replica diff log entry tracking-id]
    ((:fn entry) {:complete-task :job/completed-task
                  :seal-task :job/completed-task
                  :volunteer-for-task :job/peer-assigned
                  :accept-join-cluster :deployment/peer-joined
                  :notify-join-cluster :deployment/peer-notify-joined-accepted
                  ;:prepare-join-cluster :deployment/peer-instant-joined
                  :leave-cluster :deployment/peer-left
                  :submit-job :deployment/submitted-job
                  :kill-job :deployment/kill-job})))

(defmethod log-notifications :deployment/submitted-job [send-fn! replica diff log entry tracking-id]
  (let [job-id (:id (:args entry))
        catalog (extensions/read-chunk log :catalog job-id)
        workflow (extensions/read-chunk log :workflow job-id)
        flow-conditions (extensions/read-chunk log :flow-conditions job-id)]
    (send-fn! [:deployment/submitted-job (cond-> {:tracking-id tracking-id
                                                  :id job-id
                                                  :entry entry
                                                  :task-scheduler (:task-scheduler (:args entry))
                                                  :catalog catalog
                                                  :workflow workflow
                                                  :pretty-catalog (with-out-str (fipp (into [] catalog)))
                                                  :pretty-workflow (with-out-str (fipp (into [] workflow)))
                                                  :created-at (:created-at entry)}
                                           flow-conditions (assoc :flow-conditions flow-conditions
                                                                  :pretty-flow-conditions (with-out-str (fipp (into [] flow-conditions)))))])))

; (defmethod log-notifications :deployment/peer-instant-joined [send-fn! replica diff _ entry tracking-id]
;     (when-let [peer (:instant-join diff)]
;       (send-fn! [:deployment/peer-instant-joined {:tracking-id tracking-id
;                                                   :log entry
;                                                   :replica replica
;                                                   :id peer}])))

(defmethod log-notifications :deployment/peer-notify-joined-accepted [send-fn! replica diff _ entry tracking-id]
  (when-let [peer (:accepted-joiner diff)]
    (send-fn! [:deployment/peer-notify-joined-accepted {:tracking-id tracking-id
                                                        :id peer}])))


(defmethod log-notifications :deployment/peer-joined [send-fn! replica _ _ entry tracking-id]
  (send-fn! [:deployment/peer-joined {:tracking-id tracking-id
                                      :replica replica
                                      :id (:subject (:args entry))}]))

(defmethod log-notifications :deployment/peer-left [send-fn! _ _ _  entry tracking-id]
  (send-fn! [:deployment/peer-left {:tracking-id tracking-id
                                    :id (:id (:args entry))}]))

(defmethod log-notifications :job/peer-assigned [send-fn! replica diff log entry tracking-id]
  (let [peer-id (:id (:args entry))
        {:keys [job-id task-id]} (task-allocated-to-peer (:allocations replica) peer-id)]
  (when task-id 
    (let [task (extensions/read-chunk log :task task-id)] 
      (send-fn! [:job/peer-assigned {:tracking-id tracking-id
                                     :job-id job-id 
                                     :peer-id peer-id
                                     :task (select-keys task [:id :name])}])))))

(defmethod log-notifications :deployment/kill-job [send-fn! replica diff log entry tracking-id]
  (send-fn! [:deployment/kill-job {:tracking-id tracking-id
                                   :entry entry
                                   :id (:job (:args entry))}]))

(defmethod log-notifications :job/completed-task [send-fn! replica diff log entry tracking-id]
  (let [task (extensions/read-chunk log :task (:task (:args entry)))]
    (send-fn! [:job/completed-task {:tracking-id tracking-id
                                    :job-id (:job (:args entry))
                                    :peer-id (:id (:args entry))
                                    :task (select-keys task [:id :name])}])))

(defmethod log-notifications :default [send-fn! replica diff log entry tracking-id])

(defn send-job-statuses [send-fn! tracking-id incomplete-jobs new-incomplete-jobs]
  (when (not= incomplete-jobs new-incomplete-jobs)
    (send-fn! [:deployment/job-statuses 
               {:tracking-id tracking-id 
                :finished-jobs (clojure.set/difference incomplete-jobs new-incomplete-jobs)
                :incomplete-jobs (clojure.set/difference new-incomplete-jobs incomplete-jobs)}])))

(defn send-log-entry [send-fn! tracking-id entry]
  (send-fn! [:deployment/log-entry (assoc entry :tracking-id tracking-id)]))

(defn track-deployment [send-fn! deployment-id subscription ch f-check-pulses tracking-id]
  (let [log (:log (:env subscription))]
    (loop [replica (:replica subscription)
           last-id nil
           up-to-date? false
           incomplete-jobs #{}]
      (if-let [position (first (alts!! (vector ch (timeout freshness-timeout))))]
        (let [entry (extensions/read-log-entry log position)]
          (if-let [new-replica (apply-log-entry send-fn! tracking-id entry replica)] 
            (let [diff (extensions/replica-diff entry replica new-replica)
                  new-incomplete-jobs (set (common/incomplete-jobs new-replica))]
              (send-job-statuses send-fn! tracking-id incomplete-jobs new-incomplete-jobs)
              (log-notifications send-fn! new-replica diff log entry tracking-id)
              (send-log-entry send-fn! tracking-id entry)
              (recur new-replica position false new-incomplete-jobs))))
        (let [has-no-pulse? (empty? (f-check-pulses))] 
          (when has-no-pulse?
            (send-fn! [:deployment/no-pulse {:tracking-id tracking-id}]))
          (send-fn! [:deployment/up-to-date {:tracking-id tracking-id :last-id last-id}])
          (recur replica last-id true incomplete-jobs))))))

(defrecord LogSubscription [peer-config]
  component/Lifecycle
  (start [component]
    (info "Start log subscription")
    (let [sub-ch (chan 100)
          subscription (onyx.api/subscribe-to-log peer-config sub-ch)] 
      (assoc component :subscription subscription :subscription-ch sub-ch)))

  (stop [component]
    (info "Shutting down log subscription")
    (onyx.api/shutdown-env (:env (:subscription component)))
    (assoc component :subscription nil :channel nil)))

(defrecord TrackDeploymentManager [send-fn! peer-config tracking-id user-id]
  component/Lifecycle
  (start [component]
    ; Convert to a system?
    (info "Starting Track Deployment manager " send-fn! peer-config user-id)
    (let [subscription (component/start (map->LogSubscription {:peer-config peer-config}))
          f-check-pulses (partial deployment-pulses 
                                  (zk/connect (:zookeeper/address peer-config)) 
                                  (:onyx/id peer-config))]
      (assoc component 
             :subscription subscription
             :tracking-fut (future 
                             (track-deployment (partial send-fn! user-id)
                                               (:onyx/id peer-config)
                                               (:subscription subscription)
                                               (:subscription-ch subscription)
                                               f-check-pulses
                                               tracking-id)))))
  (stop [component]
    (info "Stopping Track Deployment manager.")
    (assoc component 
           :subscription (component/stop (:subscription component))
           :tracking-fut (future-cancel (:tracking-fut component)))))

(defn new-track-deployment-manager [send-fn! peer-config user-id tracking-id]
  (map->TrackDeploymentManager {:send-fn! send-fn! 
                                :peer-config peer-config 
                                :user-id user-id
                                :tracking-id tracking-id}))

(defn stop-tracking! [tracking user-id]
  (if-let [manager (tracking user-id)]
    (do (component/stop manager)
        (dissoc tracking user-id))
    tracking))

(defn stop-all-tracking! [tr]
  (reduce stop-tracking! tr (keys tr)))

(defn start-tracking! [send-fn! peer-config tracking {:keys [deployment-id tracking-id]} user-id]
  (swap! tracking 
         (fn [tr]
           (-> tr
               (stop-tracking! user-id)
               (assoc user-id (component/start 
                                (new-track-deployment-manager send-fn! 
                                                              (assoc peer-config :onyx/id deployment-id)
                                                              user-id
                                                              tracking-id)))))))
