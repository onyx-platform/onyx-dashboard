(ns onyx-dashboard.system
  (:require
   [untangled.server.core :as core]
   [om.next.server :as om]
   [taoensso.timbre :as timbre]
   [onyx-dashboard.api :as api]
   [untangled.server.core :as c]
   [lib-onyx.log-subscriber :as ls]))

(defn logging-mutate [env k params]
  (timbre/info "Mutation Request: " k)
  (api/apimutate env k params))

(defn make-system []
  (let [config-path "/usr/local/etc/app.edn"]
    (core/make-untangled-server
     :config-path "/usr/local/etc/app.edn"
     :parser (om/parser {:read api/api-read :mutate logging-mutate})
     :components {:replica (ls/log-subscriber-component {:onyx/tenancy-id "1"
                                                         :zookeeper/address "127.0.0.1:2181"} 1000)}
     :parser-injections [:replica])))
