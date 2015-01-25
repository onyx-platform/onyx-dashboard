(ns onyx-dashboard.http.server
  (:require [clojure.core.async :refer [chan thread <!! alts!!]]
            [onyx-dashboard.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [org.httpkit.server :as http-kit-server]
            [com.stuartsierra.component :as component]
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

(def deployments (atom {}))
(def tracking (atom {}))

;(deref tracking)

; Improve fn name
(defn job-peer-allocated-task [job-allocations job-id peer-id]
  (ffirst 
    (filter (fn [[task-id peer-ids]]
              (not-empty (filter #{peer-id} peer-ids))) 
            job-allocations)))

; Improve fn name
(defn task-allocated-to-peer [allocations peer-id]
  (some identity 
        (map (fn [job-id] 
               (if-let [task (job-peer-allocated-task (allocations job-id)
                                                      job-id
                                                      peer-id)]
                 {:job job-id :task task}))
             (keys allocations))))

; May want to track events in a deployment atom
; including chunks that are read e.g. catalog. Then if we end up
; with multiple clients viewing the same deployment we can only subscribe once.
; Probably not worth the complexity!
(defn track-deployment [send-fn! subscription ch uid]
  (let [log (:log (:env subscription))]
    (loop [replica (:replica subscription)]
      (when-let [position (<!! ch)]
        (let [entry (extensions/read-log-entry log position)
              new-replica (extensions/apply-log-entry entry replica)
              diff (extensions/replica-diff entry replica new-replica)
              job-id (:job (:args entry))
              ;complete-tasks (get (:completions new-replica) job-id)
              tasks (get (:tasks new-replica) job-id)]
          (case (:fn entry)
            :submit-job (let [job-id (:id (:args entry))
                              catalog (extensions/read-chunk log :catalog job-id)]
                          (send-fn! uid [:job/submitted-job {:id job-id
                                                             :entry entry
                                                             :catalog catalog
                                                             :created-at (:created-at entry)}]))
            ; TODO: check if newly submitted jobs will result in peer assigned messages being sent.
            :volunteer-for-task (let [peer-id (:id (:args entry))
                                      task (task-allocated-to-peer (:allocations new-replica) peer-id)]  
                                  (if task 
                                    (send-fn! uid 
                                              [:job/peer-assigned {:job (:job task)
                                                                   :task (:task task)
                                                                   :peer peer-id}])))

            :complete-task (let [task (extensions/read-chunk log :task (:task (:args entry)))
                                 task-name (:name task)]
                             (send-fn! uid [:job/completed-task {:name task-name}]))
            (info "Unable to custom handle entry"))
          (send-fn! uid [:job/entry entry])
          ;(send-fn! uid [:deployment/replica {:replica new-replica :diff diff}])
          (recur new-replica))))))


(defrecord LogSubscription [peer-config]
  component/Lifecycle
  (start [component]
    ; Maybe don't need a whole component for this since it already pretty much is one?
    (info "Start log subscription")
    (let [sub-ch (chan 100)
          subscription (onyx.api/subscribe-to-log peer-config sub-ch)] 
      (assoc component :subscription subscription :subscription-ch sub-ch)))
  (stop [component]
    (info "Shutting down log subscription")
    (onyx.api/shutdown-env (:env (:subscription component)))
    (assoc component :subscription nil :channel nil)))


(defrecord TrackDeploymentManager [send-fn! peer-config user-id]
  component/Lifecycle
  (start [component]
    ; Convert to a system?
    (info "Starting Track Deployment manager " send-fn! peer-config user-id)
    (let [subscription (component/start (map->LogSubscription {:peer-config peer-config}))]
      (assoc component 
             :subscription subscription
             :tracking-fut (future 
                             (track-deployment send-fn!
                                               (:subscription subscription)
                                               (:subscription-ch subscription)
                                               user-id)))))
  (stop [component]
    (info "Stopping Track Deployment manager.")
    (assoc component 
           :subscription (component/stop (:subscription component))
           :tracking-fut (future-cancel (:tracking-fut component)))))

(defn new-track-deployment-manager [send-fn! peer-config user-id]
  (map->TrackDeploymentManager {:send-fn! send-fn! 
                                :peer-config peer-config 
                                :user-id user-id}))

(defn stop-tracking! [user-id]
  (when-let [tracking-manager (@tracking user-id)]
    (component/stop tracking-manager)
    (swap! tracking dissoc user-id)))

(defn start-tracking! [send-fn! peer-config deployment-id user-id]
  (stop-tracking! user-id)
  (swap! tracking 
         assoc 
         user-id
         (component/start (new-track-deployment-manager send-fn! 
                                                        (assoc peer-config :onyx/id deployment-id)
                                                        user-id))))

(defn stop-all-tracking! []
  (map stop-tracking! (keys @tracking)))

(defn zk-deployment-entry-stat [client entry]
  (:stat (zk/data client (zk-onyx/prefix-path entry))))

(defn distribute-deployments [send-all-fn! deployments]
  (send-all-fn! [:deployment/listing deployments]))

(defn refresh-deployments-watch [send-all-fn! zk-client]
  (->> (zk/children zk-client zk-onyx/root-path :watcher (fn [_] (refresh-deployments-watch send-all-fn! zk-client)))
       (map (juxt identity 
                  (partial zk-deployment-entry-stat zk-client)))
       (map (fn [[child stat]]
              (vector child
                      {:created-at (java.util.Date. (:ctime stat))
                       :modified-at (java.util.Date. (:mtime stat))})))
       (into {})
       (reset! deployments)
       (distribute-deployments send-all-fn!)))

(defn event->uid [event]
  (get-in event [:ring-req :cookies "ring-session" :value]))

(defn start-event-handler [sente peer-config]
  (future
    (loop []
      (when-let [event (<!! (:ch-chsk sente))]
        ;(info "EVENT:" event)
        ;; TODO: more sophisticated tracking,
        ;; should track by cluster id rather than user
        ;; and count the number of users tracking it. When the user count drops to 0
        ;; then stop the future.
        (let [user-id (event->uid event)] 
          (case (:id event) 
            :deployment/track (start-tracking! (:chsk-send! sente)
                                               peer-config
                                               (:?data event)
                                               user-id)
            :deployment/get-listing ((:chsk-send! sente) user-id [:deployment/listing @deployments])
            :chsk/uidport-close (stop-tracking! user-id)
            :chsk/ws-ping nil
            (println "Dunno what to do with: " event)))
        (recur)))))

(defn send-mult-fn [send-fn! connected-uids msg]
  (doseq [uid (:any @connected-uids)]
    (send-fn! uid msg)))

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
    
    (let [event-handler-fut (start-event-handler sente peer-config)
          ; TODO: no way to currently stop this watch
          _ (refresh-deployments-watch (partial send-mult-fn 
                                                (:chsk-send! sente) 
                                                (:connected-uids sente)) 
                                       (zk/connect (:zookeeper/address peer-config)))
          my-ring-handler (ring.middleware.defaults/wrap-defaults my-routes ring-defaults-config)
          server (http-kit-server/run-server my-ring-handler {:port 3000})
          uri (format "http://localhost:%s/" (:local-port (meta server)))]
      (println "Http-kit server is running at" uri)
      (assoc component :server server :event-handler-fut event-handler-fut)))
  (stop [{:keys [server] :as component}]
    (println "Stopping HTTP Server")
    (stop-all-tracking!)
    (future-cancel (:event-handler-fut component))
    (server :timeout 100)
    (assoc component :server nil :event-handler-fut nil)))

(defn new-http-server [peer-config]
  (map->HttpServer {:peer-config peer-config}))

