(ns onyx-dashboard.controllers.websocket)

(defmulti msg-controller (fn [[type _] _] type))

(defmethod msg-controller :deployment/listing [[_ msg] state]
  (assoc-in state [:deployments] msg))

(defmethod msg-controller :deployment/up-to-date [[_ msg] state]
  (println "Got deployment up to date")
  (assoc-in state [:deployment :up-to-date?] true))

(defn is-tracking? [msg state]
  (= (:tracking-id msg)
     (get-in state [:deployment :tracking-id])))

(defmethod msg-controller :job/completed-task [[_ msg] state]
  (when (is-tracking? msg state)
    (println "Task completed: " msg))
  state)

(defmethod msg-controller :job/submitted-job [[_ msg] state]
  (if (is-tracking? msg state)
    (assoc-in state [:deployment :jobs (:id msg)] msg) 
    state))

(defmethod msg-controller :job/entry [[_ msg] state]
  (if (is-tracking? msg state)
    (update-in state 
               [:deployment] 
               (fn [deployment]
                 (-> deployment
                     (assoc :up-to-date? false)
                     (assoc :message-id-max (max (:message-id msg)
                                                 (:message-id-max deployment)))
                     (assoc-in [:entries (:message-id msg)] msg))))
    state))

(defmethod msg-controller :default [[type msg] state]
  (println "Unhandled msg type:" type "msg:" msg)
  state)
