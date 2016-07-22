(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [onyx-dashboard.system :as sys]))

(def system nil)

(defn init [zk-addr]
  (alter-var-root #'system (constantly (sys/get-system zk-addr))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go [zk-addr]
  (init zk-addr)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))

;; (go)
