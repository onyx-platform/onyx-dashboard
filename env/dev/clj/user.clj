(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [onyx-dashboard.system :as sys]))

(def system nil)

(defn init []
  (alter-var-root #'system (constantly (sys/get-system "127.0.0.1:2188" :onyx.job-scheduler/greedy))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))

;; (go)

