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
            [clojure.string :refer [upper-case]]
            [taoensso.timbre :refer [info] :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor])
  (:gen-class))

; uncomment some deps from project.clj to see also logs from included jars
(let [file-log-lvl    :error   ; set to :trace to see more details
      console-log-lvl :info
      rotor-appender (rotor/rotor-appender {:path "dashboard.log"})
      rotor-appender (assoc rotor-appender :min-level file-log-lvl)]
      (timbre/merge-config!
          {:appenders
            {:println {:min-level console-log-lvl
                       :enabled? true}
             :rotor rotor-appender}}))

(defn get-system 
  ([zookeeper-addr]
   (component/system-map
     :sente (component/using (sente) [])
     :http (component/using (new-http-server {:zookeeper/address zookeeper-addr
                                              ;; Remove once schema is fixed
                                              :onyx.peer/job-scheduler :not-required/for-peer-sub
                                              :onyx.messaging/impl :aeron
                                              ;; Doesn't matter for the dashboard
                                              :onyx.messaging/bind-addr "localhost"}) 
                            [:sente]))))

(defn -main [zookeeper-addr]
  (component/start (get-system zookeeper-addr))
  ;; block forever
  (<!! (chan)))
