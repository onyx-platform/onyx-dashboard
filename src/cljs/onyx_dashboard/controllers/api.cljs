(ns onyx-dashboard.controllers.api
  (:require [cljs-uuid.core :as uuid]))

(defmulti api-controller (fn [[cmd] _ _] cmd))

(defmethod api-controller :select-job [[_ id] _ state]
  (assoc-in state [:deployment :selected-job] id))

(defmethod api-controller :track-deployment [[_ deployment-id] chsk-send! state]
  (let [tracking-id (uuid/make-random)] 
    (chsk-send! [:deployment/track {:deployment-id deployment-id
                                    :tracking-id tracking-id}])
    (assoc state :deployment {:tracking-id tracking-id
                              :id deployment-id
                              :entries {}})))

(defmethod api-controller :start-job [[_ job-info] chsk-send! state]
  (chsk-send! [:job/start job-info])
  state)

(defmethod api-controller :restart-job [[_ job-id] chsk-send! state]
  (let [job-info 
        (-> state 
            (get-in [:deployment :jobs job-id])
            (select-keys [:id :catalog :workflow :task-scheduler]))
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



