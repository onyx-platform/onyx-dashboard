(ns onyx-dashboard.system
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [onyx-dashboard.dev :refer [is-dev? inject-devmode-html #_browser-repl start-figwheel]]
            [onyx-dashboard.http.sente :refer [sente]]
            [onyx-dashboard.http.server :refer [new-http-server]]
            [onyx.system :refer [onyx-client]]
            [onyx.messaging.core-async]
            [onyx.messaging.netty-tcp]
            [onyx.messaging.aeron]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [clojure.core.async :refer [<!! chan]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [clojure.string :refer [upper-case]])
  (:gen-class))

(defn env-throw [v]
  (or (env v)
      (throw (Exception. (format "Please set %s environment variable via shell, or via env map %s" 
                                 (clojure.string/replace (upper-case (name v)) "-" "_")
                                 v)))))

(defn get-system []
  (let [env-config {:zookeeper/address (env-throw :zookeeper-addr)
                    :onyx.messaging/impl :core.async}]
    (component/system-map
      :sente (component/using (sente) [])
      :http (component/using (new-http-server env-config) [:sente]))))

(defn -main [& [port]]
  (component/start (get-system))
  ;; block forever
  (<!! (chan)))
