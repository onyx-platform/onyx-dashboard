(ns onyx-dashboard.controllers.websocket)

(defmulti msg-controller (fn [[type _] _] type))

(defmethod msg-controller :deployment/listing [[_ msg] state]
  (assoc-in state [:deployments] msg))

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
                     (assoc :message-id-max (max (:message-id msg)
                                                 (:message-id-max deployment)))
                     (assoc-in [:entries (:message-id msg)] msg))))
    state))

(defmethod msg-controller :default [[type msg] state]
  (println "Unhandled msg type:" type "msg:" msg)
  state)

; (defn msg-controller [[type msg] state]
;   ; success notification currently notifies about bad tracking ids
;   ; probably going to need a better session management check
;   ; Disable for now as it's a bit distracting during dev
;   ;(success-notification type)
;   (if-let [tracking-id (:tracking-id msg)] 
;     (cond (= tracking-id (get-in state [:deployment :tracking-id]))
;           (case type
;             ;:deployment/replica
;             ;(assoc-in state [:deployment :replica] recv-msg)
;             :job/completed-task
;             (do (println "Task completed: " msg)
;                 state)
;             :job/submitted-job
;             (assoc-in state [:deployment :jobs (:id msg)] msg)
;             :job/entry
;             (update-in state 
;                        [:deployment] 
;                        (fn [deployment]
;                          (-> deployment
;                              (assoc :message-id-max (max (:message-id msg)
;                                                          (:message-id-max deployment)))
;                              (assoc-in [:entries (:message-id msg)] msg))))
;             state)
;           :else state)
;     (if (= :deployment/listing type)
;       (assoc-in state [:deployments] msg)
;       state)))
