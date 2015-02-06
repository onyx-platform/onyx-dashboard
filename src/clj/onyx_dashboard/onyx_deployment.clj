(ns onyx-dashboard.onyx-deployment
  (:require [fipp.edn :refer [pprint] :rename {pprint fipp}]
            [onyx.system :as system :refer [onyx-client]]
            [onyx.extensions :as extensions]
            [onyx.api]
            [onyx.log.zookeeper :as zk-onyx]
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

(defn refresh-deployments-watch [send-all-fn! zk-client deployments]
  (if-let [children (zk/children zk-client zk-onyx/root-path :watcher (fn [_] (refresh-deployments-watch send-all-fn! zk-client)))]
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

(def freshness-timeout 50)

(defn apply-log-entry [send-fn! tracking-id entry replica]
  (try 
    (extensions/apply-log-entry entry replica)
    (catch Throwable t
      (send-fn! [:deployment/log-replay-crash {:tracking-id tracking-id :error (str t)}])
      nil)))

; May want to track events in a deployment atom
; including chunks that are read e.g. catalog. Then if we end up
; with multiple clients viewing the same deployment we can only subscribe once.
; Probably not worth the complexity!
(defn track-deployment [send-fn! subscription ch tracking-id]
  (let [log (:log (:env subscription))
        up-to-date? (atom false)
        last-id (atom nil)]
    (loop [replica (:replica subscription)]
      (if-let [position (first (alts!! (vector ch (timeout freshness-timeout))))]
        (let [entry (extensions/read-log-entry log position)]
          (reset! last-id position)
          (if-let [new-replica (apply-log-entry send-fn! tracking-id entry replica)] 
            (let [diff (extensions/replica-diff entry replica new-replica)
                  job-id (:job (:args entry))
                  ;complete-tasks (get (:completions new-replica) job-id)
                  tasks (get (:tasks new-replica) job-id)]
              ; Split into multimethods
              (cond (= :submit-job (:fn entry)) 
                    (let [job-id (:id (:args entry))
                          catalog (extensions/read-chunk log :catalog job-id)
                          workflow (extensions/read-chunk log :workflow job-id)]
                      (send-fn! [:job/submitted-job {:tracking-id tracking-id
                                                     :id job-id
                                                     :entry entry
                                                     :task-scheduler (:task-scheduler (:args entry))
                                                     :catalog catalog
                                                     :workflow workflow
                                                     :pretty-catalog (with-out-str (fipp (into [] catalog)))
                                                     :pretty-workflow (with-out-str (fipp (into [] workflow)))
                                                     :created-at (:created-at entry)}]))

                    (= :accept-join-cluster (:fn entry))
                    (send-fn! [:deployment/peer-joined {:tracking-id tracking-id
                                                        :id (:subject (:args entry))}])

                    (= :leave-cluster (:fn entry))
                    (send-fn! [:deployment/peer-left {:tracking-id tracking-id
                                                      :id (:id (:args entry))}])

                    ; TODO: check if newly submitted jobs will result in peer assigned messages being sent.
                    ; This might be doing more work than required
                    (= :volunteer-for-task (:fn entry)) 
                    (let [peer-id (:id (:args entry))
                          {:keys [job-id task-id]} (task-allocated-to-peer (:allocations new-replica) peer-id)]
                      (when task-id 
                        (let [task (extensions/read-chunk log :task task-id)] 
                          (send-fn! [:job/peer-assigned {:tracking-id tracking-id
                                                         :job-id job-id 
                                                         :peer-id peer-id
                                                         :task (select-keys task [:id :name])}]))))

                    (#{:seal-task :complete-task} (:fn entry)) 
                    (let [task (extensions/read-chunk log :task (:task (:args entry)))]
                      (send-fn! [:job/completed-task {:tracking-id tracking-id
                                                      :job-id (:job (:args entry))
                                                      :peer-id (:id (:args entry))
                                                      :task (select-keys task [:id :name])}]))
                    :else nil #_(info "Unable to custom handle entry " entry))
              (println entry)
              (println "Replica is " replica " \n " diff)
              (send-fn! [:deployment/log-entry (assoc entry :tracking-id tracking-id)])
              (reset! up-to-date? false)
              ;(send-fn! [:deployment/replica {:replica new-replica :diff diff}])
              (recur new-replica))))
        (do 
          (send-fn! [:deployment/up-to-date {:tracking-id tracking-id :last-id @last-id}])
          (reset! up-to-date? true)
          (recur replica))))))

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
    (let [subscription (component/start (map->LogSubscription {:peer-config peer-config}))]
      (assoc component 
             :subscription subscription
             :tracking-fut (future 
                             (track-deployment (partial send-fn! user-id)
                                               (:subscription subscription)
                                               (:subscription-ch subscription)
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
  (reduce stop-tracking!
          tr
          (keys tr)))

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


