(ns onyx-dashboard.system
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [onyx-dashboard.dev :refer [is-dev? inject-devmode-html #_browser-repl start-figwheel]]
            [onyx-dashboard.http.sente       :refer [sente]]
            [onyx-dashboard.http.server      :refer [new-http-server]]
            [onyx-dashboard.http.zk-client   :refer [new-zk-client]]
            [onyx-dashboard.http.deployments :refer [new-deployments]]
            [onyx-dashboard.channels         :refer [new-channels]]
            [onyx.system :refer [onyx-client]]
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


(defn get-system 
  ([zookeeper-addr]
    (let [file-log-lvl   :error  ; set to :trace to see more details
         console-log-lvl :info
         rotor-appender (rotor/rotor-appender {:path "dashboard.log"})
         rotor-appender (assoc rotor-appender :min-level file-log-lvl)]
         (timbre/merge-config!
            {:appenders
              {:println {:min-level console-log-lvl
                         :enabled? true}
               :rotor rotor-appender}}))
    (println "=================================")
    (println "Starting Dashboard components ...")
   (component/system-map
     :channels (component/using (new-channels) [])
     :sente    (component/using (sente) [])
     :zk (component/using (new-zk-client {:zookeeper/address zookeeper-addr
                                          ;; Remove once schema is fixed
                                          :onyx.peer/job-scheduler :not-required/for-peer-sub})
                          [:channels :sente])
     :deployments (component/using (new-deployments) [:channels :sente :zk])
     :http (component/using (new-http-server {:zookeeper/address zookeeper-addr
                                              ;; Remove once schema is fixed
                                              :onyx.peer/job-scheduler :not-required/for-peer-sub
                                              :onyx.messaging/impl :aeron
                                              ;; Doesn't matter for the dashboard
                                              :onyx.messaging/bind-addr "localhost"}) 
                            [:channels :sente :zk :deployments]))))

(defn -main [zookeeper-addr]
  (component/start (get-system zookeeper-addr))
  ;; block forever
  (<!! (chan)))
