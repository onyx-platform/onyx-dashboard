(ns onyx-dashboard.system
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [onyx-dashboard.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [onyx-dashboard.http.sente :refer [sente]]
            [onyx-dashboard.http.server :refer [new-http-server]]
            [onyx.system :refer [onyx-client]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]))

(def id "WHATEV")

(def remote? true)

(def zk-addr 
  (if remote?
    "54.169.229.123:2181,54.169.240.52:2181"
    "172.31.5.32:2181,172.31.5.33:2181"))

(def env-config 
  {:hornetq/mode :standalone
   :hornetq.standalone/host "54.169.41.99"
   :hornetq.standalone/port 5445
   :zookeeper/address zk-addr
   :onyx.peer/job-scheduler :onyx.job-scheduler/round-robin
   :onyx/id id})

(defn get-system []
  (component/system-map
    :onyx-env (onyx-client env-config)
    :sente (component/using (sente) [])
    :http (component/using (new-http-server env-config) [:sente])))

(defn -main [& [port]]
  (component/start (get-system)))
