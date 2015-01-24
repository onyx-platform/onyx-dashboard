(ns onyx-dashboard.http.server
  (:require [clojure.core.async :refer [chan thread <!! alts!!]]
            [onyx-dashboard.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [org.httpkit.server :as http-kit-server]
            [clojure.java.io :as io]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.defaults]
            [ring.util.response :refer [resource-response response content-type]]
            [compojure.core :as comp :refer (defroutes GET POST)]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [onyx.system :as system :refer [onyx-client]]
            [onyx.extensions :as extensions]
            [onyx.api]
            [onyx.log.zookeeper :as zk-onyx]
            [zookeeper :as zk]
            [taoensso.timbre :as timbre :refer [info error spy]]))

(deftemplate page
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(def ring-defaults-config
  (assoc-in ring.middleware.defaults/site-defaults
            [:security :anti-forgery]
            {:read-token (fn [req] (-> req :params :csrf-token))}))

(def replica-state (atom {:replica nil :diff nil}))

(defn track-deployment [send-fn! peer-config uid]
  (let [ch (chan 100)
        client (component/start (system/onyx-client peer-config))]
    (loop [replica (extensions/subscribe-to-log (:log client) ch)]
      (let [position (<!! ch)
            entry (extensions/read-log-entry (:log client) position)
            new-replica (extensions/apply-log-entry entry replica)
            diff (extensions/replica-diff entry replica new-replica)]
        (send-fn! uid [:deployment/replica {:replica new-replica :diff diff}])
        (recur new-replica)))))

(def env-conf
  {:hornetq/mode :standalone, 
   :hornetq.standalone/host "54.169.41.99", 
   :hornetq.standalone/port 5445, 
   :zookeeper/address "54.169.229.123:2181,54.169.240.52:2181", 
   :onyx.peer/job-scheduler :onyx.job-scheduler/round-robin 
   :onyx/id :SUPPLY_ME})

(defn zk-deployment-entry-stat [client entry]
  (:stat (zk/data client (zk-onyx/prefix-path entry))))

(def deployments (atom {}))
(def tracking (atom {}))

(defn distribute-deployments [send-all-fn! deployments]
  (send-all-fn! [:deployment/listing deployments]))

(defn refresh-deployments-watch [send-all-fn! zk-client]
  (->> (zk/children zk-client zk-onyx/root-path :watcher (fn [_] (refresh-deployments-watch send-all-fn! zk-client)))
       (map (juxt identity 
                  (partial zk-deployment-entry-stat zk-client)))
       (map (fn [[child stat]]
              (vector child
                      {:created (java.util.Date. (:ctime stat))
                       :modified (java.util.Date. (:mtime stat))})))
       (into {})
       (reset! deployments)
       (distribute-deployments send-all-fn!)))

(defn event->uid [event]
  (get-in event [:ring-req :cookies "ring-session" :value]))

(defn launch-event-handler [sente peer-config]
  (thread
    (loop []
      (when-let [event (<!! (:ch-chsk sente))]
        (info "EVENT:" event)
        ;; TODO: more sophisticated tracking,
        ;; should track by cluster id rather than user
        ;; and count the number of users tracking it. When the user count drops to 0
        ;; then stop the future.
        (let [user-id (event->uid event)] 
          (case (:id event) 
            :deployment/track (do (when-let [fut (@tracking user-id)]
                                    (future-cancel fut))
                                  (swap! tracking 
                                         assoc 
                                         user-id
                                         (future (track-deployment (:chsk-send! sente)
                                                                   (assoc env-conf :onyx/id (:?data event)) 
                                                                   user-id))))
            :deployment/get-listing ((:chsk-send! sente) user-id [:deployment/listing @deployments])
            :chsk/uidport-close (do
                                  (future-cancel (@tracking user-id))
                                  (swap! tracking dissoc user-id)) 
            (println "Dunno what to do with: " event)))
        (recur)))))

(defn send-mult-fn [send-fn! connected-uids msg]
  (doseq [uid (:any @connected-uids)]
    (send-fn! uid msg)))

; no need for env-config and peer-config
(defrecord HttpServer [peer-config]
  component/Lifecycle
  (start [{:keys [sente] :as component}]
    (println "Starting HTTP Server")

    (defroutes my-routes
      (GET  "/" [] (page))
      (GET  "/chsk" req ((:ring-ajax-get-or-ws-handshake sente) req))
      (POST "/chsk" req ((:ring-ajax-post sente) req))
      ;(GET "/cluster/:deployment-id" req (get-job-output req))
      ;(GET "/job/:job-id" req (get-job-output req))
      (resources "/")
      (resources "/react" {:root "react"})
      (route/not-found "Page not found"))
    (launch-event-handler sente peer-config)
    (refresh-deployments-watch (partial send-mult-fn 
                                        (:chsk-send! sente) 
                                        (:connected-uids sente)) 
                               (zk/connect (:zookeeper/address env-conf)))

    (let [my-ring-handler (ring.middleware.defaults/wrap-defaults my-routes ring-defaults-config)
          server (http-kit-server/run-server my-ring-handler {:port 3000})
          uri (format "http://localhost:%s/" (:local-port (meta server)))]
      (println "Http-kit server is running at" uri)
      (assoc component :server server)))
  (stop [{:keys [server] :as component}]
    (println "Stopping HTTP Server")
    (server :timeout 100)
    (assoc component :server nil)))

(defn new-http-server [peer-config]
  (map->HttpServer {:peer-config peer-config}))

