(ns onyx-dashboard.controllers.websocket
  (:require [clojure.set :refer [union]]
	    [timothypratley.patchin :as p]))

; update app state
(defmulti msg-controller (fn [[type _] _] type))

(defmethod msg-controller :deployment/listing 
  [[_ msg] state]
  (assoc-in state [:deployments] msg))

(defmethod msg-controller :deployment/enable-job-management
  [[_ msg] state]
  (.log js/console "enable job management" (str msg))
  (assoc-in state [:enable-job-management] msg))

(defn is-tracking? [tracking-id state]
  (= tracking-id
     (get-in state [:deployment :tracking-id])))

(defmethod msg-controller :deployment/up-to-date 
  [[_ {:keys [tracking-id last-id] :as msg}] state]
  (if (is-tracking? tracking-id state) 
    (assoc-in state [:deployment :up-to-date?] true)
    state))

(defmethod msg-controller :deployment/submitted-job 
  [[_ {:keys [tracking-id job] :as msg}] state]
  (if (is-tracking? tracking-id state)
    (assoc-in state [:deployment :jobs (:id job)] job) 
    state))

(defmethod msg-controller :deployment/log-replay-crash 
  [[_ {:keys [tracking-id] :as msg}] state]
  ;(println "Log replay crashed" msg)
  (if (is-tracking? tracking-id state)
    (assoc-in state [:deployment :status] {:status :crashed :error (:error msg)})
    state))

(defmethod msg-controller :deployment/kill-job 
  [[_ {:keys [id tracking-id exception] :as msg}] state]
  (if (is-tracking? tracking-id state)
    (assoc-in state [:deployment :jobs id :exception] exception)
    state))

(defmethod msg-controller :deployment/log-entry [[_ {:keys [tracking-id entry diff]}] state]
  (if (is-tracking? tracking-id state)
    (update-in state 
               [:deployment] 
               (fn [deployment]
                 (let [previous-replica-state (get-in deployment [:replica-states (:message-id-max deployment)] {})] 
                   (-> deployment
                       (assoc :up-to-date? false)
                       (assoc :up? true)
                       (update :message-id-max max (:message-id entry))
                       (assoc-in [:replica-states (:message-id entry)] {:entry entry 
                                                                        :diff diff 
                                                                        :replica (p/patch previous-replica-state diff)})))))
    state))

(defmethod msg-controller :deployment/no-pulse [[_ msg] state]
  (if (is-tracking? msg state)
    (assoc-in state [:deployment :up?] false)
    state))

(defmethod msg-controller :deployment/zk-conn-state [[_ msg] state]
  (.log js/console "ZK connection" (str msg))
  (assoc-in state [:zk-up?] (-> msg :zk-up?)))


; (defmethod msg-controller :metrics/event [[_ msg] state]
;   (cond (and (= (:metric msg) :throughput))
;         (assoc-in state [:metrics (:job-id msg) (:task-name msg) (:metric msg) (:window msg) (:peer-id msg)] (:value msg))
;         (and (= (:metric msg) :batch-latency))
;         (assoc-in state [:metrics (:job-id msg) (:task-name msg) (:metric msg) (:window msg) (:quantile msg) (:peer-id msg)] (:value msg))
;         :else state))

(defmethod msg-controller :default [[type msg] state]
  (println "Unhandled msg type:" type "msg:" msg)
  state)
