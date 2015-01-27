(ns onyx-dashboard.controllers.api)

(defmulti api-controller (fn [[cmd] state] cmd))

(defmethod api-controller :visibility [[_ type visible?] state]
  (assoc-in state [:visible type] visible?))


