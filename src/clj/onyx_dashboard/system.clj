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
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(def env-config 
  {:hornetq/mode :standalone
   :hornetq.standalone/host (env :hornetq-host) 
   :hornetq.standalone/port (env :hornetq-port)
   :zookeeper/address (env :zookeeper-addr)
   :onyx.peer/job-scheduler :onyx.job-scheduler/round-robin})


(defn get-system []
  (component/system-map
    :sente (component/using (sente) [])
    :http (component/using (new-http-server env-config) [:sente])))

(defn -main [& [port]]
  (component/start (get-system)))
