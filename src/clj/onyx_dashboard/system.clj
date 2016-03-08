(ns onyx-dashboard.system
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [onyx-dashboard.dev :refer [is-dev? inject-devmode-html #_browser-repl start-figwheel]]
            [onyx-dashboard.http.sente :refer [sente]]
            [onyx-dashboard.http.server :refer [new-http-server]]
            [onyx.system :refer [onyx-client]]
            [onyx.messaging.aeron]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [clojure.core.async :refer [<!! chan]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [onyx.static.validation :refer [validate-peer-config]]
            [clojure.string :refer [upper-case]])
  (:gen-class))

(defn get-system 
  ([zookeeper-addr onyx-id job-scheduler]
   (get-system {:zookeeper/address zookeeper-addr
                :onyx.peer/job-scheduler (keyword job-scheduler)
                :onyx.messaging/impl :aeron
                :onyx/tenancy-id onyx-id
                ;; Doesn't matter for the dashboard
                :onyx.messaging/bind-addr "localhost"}))
  ([peer-config]
   (validate-peer-config peer-config)
   (component/system-map
     :sente (component/using (sente) [])
     :http (component/using (new-http-server peer-config) [:sente]))))

(defn -main [zookeeper-addr onyx-id job-scheduler]
  (component/start (get-system zookeeper-addr onyx-id job-scheduler))
  ;; block forever
  (<!! (chan)))
