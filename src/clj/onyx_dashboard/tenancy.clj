(ns onyx-dashboard.tenancy
  (:require [onyx.system :as system :refer [onyx-client]]
            [onyx.extensions :as extensions]
            [onyx.api]
            [onyx.log.zookeeper       :as zk-onyx]
            [onyx.log.commands.common :as common]
            [onyx.log.curator         :as zk]
            [clojure.core.async :refer [chan timeout thread <!! alts!! go-loop close! <!]]
            [com.stuartsierra.component :as component]
	          [lib-onyx.log-subscriber :as s]
            [lib-onyx.job-query      :as jq]
            [lib-onyx.replica-query  :as rq]
	          [timothypratley.patchin :as patchin]
            [taoensso.timbre :as timbre :refer [info error spy]]))

(defn kill-job [peer-config deployment-id job-info]
  (let [id (get-in job-info [:metadata :job-id])]
        (println "Kill job" id)
        (onyx.api/kill-job (assoc peer-config :onyx/tenancy-id deployment-id) id)))

(defn start-job [peer-config deployment-id job-info]
  (let [id (get-in job-info [:metadata :job-id])]
        (println "Start job" id)
        (onyx.api/submit-job (assoc peer-config :onyx/tenancy-id deployment-id)
                             job-info)))

(defn restart-job [peer-config deployment-id {:keys [id] :as job-info}]
  (println "Restart job" (get-in job-info [:metadata :job-id]))
  (kill-job  peer-config deployment-id job-info)
  (start-job peer-config deployment-id job-info))


(defn deployment-pulses [zk-client deployment-id]
  (zk/children zk-client (zk-onyx/pulse-path deployment-id)))


(def freshness-timeout 100)

(defmulti log-notifications 
  (fn [into-br! log-sub replica diff entry tracking-id]
    ((:fn entry) {:submit-job :deployment/submitted-job
                  :kill-job :deployment/kill-job})))

(defmethod log-notifications :deployment/submitted-job [into-br! log-sub replica diff entry tracking-id]
  (let [job-id (:id (:args entry))
        tasks (:tasks (:args entry))
        task-name->id (zipmap tasks tasks)
        job (jq/job-information log-sub replica job-id)]
    (into-br! [:deployment/submitted-job {:tracking-id tracking-id
                                          :job {:task-name->id task-name->id
                                                :message-id (:message-id entry)
                                                :created-at (:created-at entry)
                                                :id job-id
                                                :job job}}])))

(defmethod log-notifications :deployment/kill-job [into-br! log-sub replica diff entry tracking-id]
  (when-let [exception (jq/exception log-sub (:job (:args entry)))]
    (into-br! [:deployment/kill-job {:tracking-id tracking-id
                                     :id (:job (:args entry))
                                     :exception (pr-str exception)}])))

(defmethod log-notifications :default [into-br! log-sub replica diff entry tracking-id] )

(defn process-subscription-event [into-br! tenancy-id tracking-id !last-replica 
                                  sub {:keys [replica entry] :as state}]
  (try 
   (let [patch (patchin/diff @!last-replica replica)]
     (into-br! [:deployment/log-entry {:tracking-id tracking-id
                                       :entry entry
                                       :diff patch}])
     (log-notifications into-br! sub replica patch entry tracking-id))
   (catch Throwable t
     (println "Error in process-subscription-event:" t))))

(defrecord TrackTenancyManager [into-br! peer-config tracking-id user-id zk-client]
  component/Lifecycle
  (start [component]
    (println "Starting Track Tenancy Manager")
    (let [tenancy-id (:onyx/tenancy-id peer-config)
          _ (info "Starting Track Tenancy manager for tenancy " tenancy-id peer-config user-id)
          f-check-pulses (partial deployment-pulses 
                                  zk-client 
                                  tenancy-id)
          last-replica (atom {})
          callback-fn (partial process-subscription-event 
                               (partial into-br! user-id)
                               tenancy-id
                               tracking-id
                               last-replica)
          log-subscriber (s/start-log-subscriber peer-config {:callback-fn callback-fn})]
      (assoc component :log-subscriber log-subscriber)))
  (stop [component]
    (info "Stopping Track Tenancy Manager.")
    (assoc component 
           :log-subscriber (s/stop-log-subscriber (:log-subscriber component)))))

(defn new-track-tenancy-manager [into-br! peer-config user-id tracking-id zk-client]
  (map->TrackTenancyManager {:into-br! into-br! 
                             :peer-config peer-config 
                             :user-id user-id
                             :tracking-id tracking-id
                             :zk-client zk-client}))

(defn stop-tracking! [tracking user-id]
  (if-let [manager (tracking user-id)]
    (do (component/stop manager)
        (dissoc tracking user-id))
    tracking))

(defn stop-all-tracking! [tr]
  (reduce stop-tracking! tr (keys tr)))

(defn start-tracking! [into-br! peer-config tracking {:keys [deployment-id tracking-id]} user-id zk-client]
  (try 
    (swap! tracking 
           (fn [tr]
             (-> tr
                 (stop-tracking! user-id)
                 (assoc user-id (component/start 
                                  (new-track-tenancy-manager into-br! 
                                                             (assoc peer-config :onyx/tenancy-id deployment-id)
                                                             user-id
                                                             tracking-id
                                                             zk-client))))))
    (catch Throwable t
      (println t))))
