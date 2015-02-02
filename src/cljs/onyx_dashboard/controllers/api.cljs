(ns onyx-dashboard.controllers.api
  (:require [cljs-uuid.core :as uuid]))

(defmulti api-controller (fn [[cmd] _ _] cmd))

(defmethod api-controller :visibility [[_ type visible?] _ state]
  (assoc-in state [:visible type] visible?))

(defmethod api-controller :select-job [[_ id] _ state]
  (assoc-in state [:deployment :selected-job] id))

(defmethod api-controller :track-deployment [[_ deployment-id] chsk-send! state]
  (let [tracking-id (uuid/make-random)] 
    (chsk-send! [:deployment/track {:deployment-id deployment-id
                                    :tracking-id tracking-id}])
    (assoc state :deployment {:tracking-id tracking-id
                              :id deployment-id
                              :entries {}})))

; Currently unused
(defmethod api-controller :track-cancel [[_ deployment-id] chsk-send! state]
  (chsk-send! [:deployment/track-cancel deployment-id])
  state)
