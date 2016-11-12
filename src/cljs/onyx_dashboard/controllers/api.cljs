(ns onyx-dashboard.controllers.api
  (:require [cljs-uuid-utils.core :as uuid]))

(defn new-deployment-state [tracking-id tenancy-id]
  {:tracking-id tracking-id
   :id tenancy-id
   :selected-job nil
   :view-index nil
   :message-id-max nil
   :time-travel-message-id nil
   :replica-states {}})

; user actions
(defmulti api-controller (fn [[cmd] _ _] cmd))

(defmethod api-controller :select-job [[_ id] _ state]
  (assoc-in state [:deployment :selected-job] id))

(defmethod api-controller :track-deployment [[_ deployment-id] chsk-send! state]
  (let [tracking-id (uuid/make-random-uuid)] 
    (chsk-send! [:deployment/track {:deployment-id deployment-id
                                    :tracking-id tracking-id}])
    (assoc state :deployment (new-deployment-state tracking-id deployment-id))))

(defmethod api-controller :time-travel [[_ message-id] chsk-send! state]
  (assoc-in state [:deployment :time-travel-message-id] (if-not (= message-id (:message-id-max (:deployment state)))
                                                          message-id)))

(defmethod api-controller :start-job [[_ job-info] chsk-send! state]
  (chsk-send! [:job/start job-info])
  state)

(defmethod api-controller :restart-job [[_ job-id] chsk-send! state]
  (let [job-info (get-in state [:deployment :jobs job-id :job]) 
        deployment-id (:id (:deployment state))]
    (chsk-send! [:job/restart {:deployment-id deployment-id :job job-info}]))
  state)

(defmethod api-controller :kill-job [[_ job-id] chsk-send! state]
  (chsk-send! [:job/kill {:deployment-id (:id (:deployment state))
                          :job {:id job-id}}])
  state)

; Currently unused
(defmethod api-controller :track-cancel [[_ deployment-id] chsk-send! state]
  (chsk-send! [:deployment/track-cancel deployment-id])
  state)



