(ns onyx-dashboard.api
  (:require [om.next.server :as om]
            [taoensso.timbre :as timbre]))

(defmulti apimutate om/dispatch)

(defmethod apimutate :default [e k p]
  (timbre/error "Unrecognized mutation: " k p))

(defn api-read
  [e k p]
  (println (keys (:parser e)))
  (timbre/error "Unrecognized read: " k))
