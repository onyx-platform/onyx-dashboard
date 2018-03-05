(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [onyx-dashboard.system :as sys]))

(def system nil)

(defn init [zk-addr enable-job-management]
  (alter-var-root #'system (constantly (sys/get-system zk-addr enable-job-management))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go [zk-addr enable-job-management]
  (init zk-addr enable-job-management)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))

;; (go)

