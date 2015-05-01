(ns onyx-dashboard.controllers.websocket
  (:require [clojure.set :refer [union]]))

(defmulti msg-controller (fn [[type _] _] type))

(defmethod msg-controller :deployment/listing 
  [[_ msg] state]
  (assoc-in state [:deployments] msg))

(defn is-tracking? [msg state]
  (= (:tracking-id msg)
     (get-in state [:deployment :tracking-id])))

(defmethod msg-controller :deployment/up-to-date 
  [[_ {:keys [tracking-id last-id] :as msg}] state]
  (if (and (is-tracking? msg state)
           (= last-id (:message-id-max (:deployment state))))
    (assoc-in state [:deployment :up-to-date?] true)
    state))

(defmethod msg-controller :deployment/submitted-job 
  [[_ {:keys [id] :as msg}] state]
  (if (is-tracking? msg state)
    (assoc-in state [:deployment :jobs id] (assoc msg :status :submitted)) 
    state))

(defmethod msg-controller :job/peer-assigned 
  [[_ {:keys [peer-id task job-id] :as msg}] state]
  ;(println "Peer assigned " msg)
  (if (is-tracking? msg state)
    (assoc-in state [:deployment :jobs job-id :tasks peer-id] task)
    state))

(defmethod msg-controller :deployment/peer-joined 
  [[_ {:keys [id] :as msg}] state]
  ;(println "Peer joined " msg)
  (if (is-tracking? msg state)
    (update-in state [:deployment :peers] union #{id}) 
    state))

; (defmethod msg-controller :deployment/peer-instant-joined [[_ {:keys [id] :as msg}] state]
;   ;(println "Peer instant joined " msg)
;   (if (is-tracking? msg state)
;     (update-in state [:deployment :peers] union #{id}) 
;     state))

(defmethod msg-controller :deployment/peer-notify-joined-accepted 
  [[_ {:keys [id] :as msg}] state]
  ;(println "Peer immediate notify joined " msg)
  (if (is-tracking? msg state)
    (update-in state [:deployment :peers] union #{id}) 
    state))

(defn remove-dead-peer [jobs peer-id]
  (zipmap (keys jobs) 
          (map (fn [job-info]
                 (update-in job-info [:tasks] dissoc peer-id))
               (vals jobs))))


(defmethod msg-controller :deployment/log-replay-crash 
  [[_ {:keys [id] :as msg}] state]
  ;(println "Log replay crashed" msg)
  (if (is-tracking? msg state)
    (assoc-in state [:deployment :status] {:status :crashed :error (:error msg)})
    state))

(defmethod msg-controller :deployment/peer-left 
  [[_ {:keys [id] :as msg}] state]
  ;(println "Peer left" msg)
  (if (is-tracking? msg state)
    (-> state 
        (update-in [:deployment :peers] disj id)
        (update-in [:deployment :jobs] remove-dead-peer id)) 
    state))

(defmethod msg-controller :job/completed-task 
  [[_ {:keys [peer-id job-id task] :as msg}] state]
  ;(println "Completed task " msg)
  (if (is-tracking? msg state)
    (update-in state [:deployment :jobs job-id :tasks] dissoc peer-id) 
    state))

(defmethod msg-controller :deployment/kill-job 
  [[_ {:keys [id] :as msg}] state]
  ;; Managed to create a kill-job entry with a nil job. Should fix this upstream
  ;; {:entry {:args {:job nil}, :fn :kill-job, :message-id 63, :created-at 1423218732944}}}
  (if (and (is-tracking? msg state) id)
    (assoc-in state [:deployment :jobs id :status] :killed)
    state))

(defn update-statuses [state job-ids status]
  (reduce (fn [st job-id]
            (assoc-in state [:deployment :jobs job-id :status] status))
          state
          job-ids))

(defmethod msg-controller :deployment/job-statuses 
  [[_ {:keys [finished-jobs incomplete-jobs] :as msg}] state]
  (if (is-tracking? msg state)
    (-> state
        (update-statuses finished-jobs :finished)
        (update-statuses incomplete-jobs :incomplete))
    state))

(defmethod msg-controller :deployment/log-entry [[_ msg] state]
  (if (is-tracking? msg state)
    (update-in state 
               [:deployment] 
               (fn [deployment]
                 (-> deployment
                     (assoc :up-to-date? false)
                     (assoc :up? true)
                     (update-in [:message-id-max] max (:message-id msg))
                     (assoc-in [:entries (:message-id msg)] (dissoc msg :tracking-id)))))
    state))

(defmethod msg-controller :deployment/no-pulse [[_ msg] state]
  (if (is-tracking? msg state)
    (assoc-in state [:deployment :up?] false)
    state))

(defmethod msg-controller :metrics/event [[_ msg] state]
  (cond (and (= (:metric msg) :throughput))
        (assoc-in state [:metrics (:job-id msg) (:task-name msg) (:metric msg) (:window msg) (:peer-id msg)] (:value msg))
        (and (= (:metric msg) :latency))
        (assoc-in state [:metrics (:job-id msg) (:task-name msg) (:metric msg) (:window msg) (:quantile msg) (:peer-id msg)] (:value msg))
        :else state))

(defmethod msg-controller :default [[type msg] state]
  (println "Unhandled msg type:" type "msg:" msg)
  state)
