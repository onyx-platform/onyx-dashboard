(ns onyx-dashboard.http.server
  (:require [clojure.core.async :refer [chan timeout thread <!! >!! alts!! go-loop <! >! close! go]]
            [onyx-dashboard.dev :refer [is-dev? inject-devmode-html #_browser-repl start-figwheel]]
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
            [onyx-dashboard.tenancy :as tenancy]
            [onyx.log.curator :as zk]
            [taoensso.timbre :as timbre :refer [info error spy]]))

(deftemplate page
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(def ring-defaults-config
  (assoc-in ring.middleware.defaults/site-defaults
            [:security :anti-forgery]
            {:read-token (fn [req] (-> req :params :csrf-token))}))

(defn event->uid [event]
  (get-in event [:ring-req :cookies "ring-session" :value]))

; WebSockets events send from browser
; Responsibilities
; - receive events from browser WS and call other components to handle it
(defn events-from-browser [sente peer-config channels-comp tracking zk-comp]
  (future 
     (loop []
      (when-let [event (<!! (:ch-chsk sente))]
        ;; TODO: more sophisticated tracking,
        ;; should track by cluster id rather than user
        ;; and count the number of users tracking it. When the user count drops to 0
        ;; then stop the future.
        (let [user-id (event->uid event)
              {:keys [zk-client notify-zc-ch]} zk-comp
              {:keys [cmds-deployments-ch]}    channels-comp]

          (case (:id event)
            :chsk/uidport-open  (go (>! notify-zc-ch [:attach-browser-notify {:user-id user-id}]))
            :chsk/uidport-close (do (swap! tracking tenancy/stop-tracking! user-id)
                                    (go (>! notify-zc-ch [:remove-browser-notify {:user-id user-id}])))

            :deployment/track (tenancy/start-tracking! (-> sente :into-br!)
                                                       peer-config
                                                       tracking
                                                       (:?data event)
                                                       user-id
                                                       zk-client)  

            :deployment/get-listing (do 
                                        ;((:chsk-send! sente) user-id [:deployment/listing @deployments])
                                        (go (>! cmds-deployments-ch [:deployment/listing {:user-id user-id}]))
                                        (go (>! notify-zc-ch [:browser-refresh-zk-conn {:user-id user-id}])))
            :job/kill (tenancy/kill-job peer-config 
                                        (:deployment-id (:?data event))
                                        (:job           (:?data event)))
            :job/start (tenancy/start-job peer-config 
                                          (:deployment-id (:?data event))
                                          (:job           (:?data event)))
            :job/restart (tenancy/restart-job peer-config 
                                              (:deployment-id (:?data event))
                                              (:job           (:?data event)))
            :chsk/ws-ping nil
            nil #_(println "Dunno what to do with: " event)))
        (recur)))))

(defrecord HttpServer [peer-config]
  component/Lifecycle
  (start [{:keys [channels sente zk deployments] :as component}]
    (println "Starting HTTP Server")
    (let []
      (defroutes routes
        (GET  "/" [] (page))
        (GET  "/chsk" req ((:ring-ajax-get-or-ws-handshake sente) req))
        (POST "/chsk" req ((:ring-ajax-post sente) req))
        (resources "/")
        (resources "/react" {:root "react"})
        (route/not-found "Page not found"))

      (let [tracking (atom {})

            ; server
            handler (ring.middleware.defaults/wrap-defaults routes ring-defaults-config)
            port (Integer. (or (System/getenv "PORT") "3000"))
            server (http-kit-server/run-server handler {:port port})
            uri (format "http://localhost:%s/" (:local-port (meta server)))
            
            zk-client (-> zk :zk-client)
            ; futures
            events-from-browser (events-from-browser sente peer-config channels tracking zk)
            ]
        (println "Http-kit server is running at" uri)
        (assoc component
               :zk-client           zk-client
               :server              server
               :events-from-browser events-from-browser 
               :deployments         deployments 
               :tracking            tracking))))

  (stop [{:keys [server tracking deployments zk-client events-from-browser deployments-watch] :as component}]
    (println "Stopping HTTP Server")
    (try
      (server :timeout 100)
      (finally 
        (try 
          (swap! tracking tenancy/stop-all-tracking!)
          (finally
            (try
              (when events-from-browser (future-cancel events-from-browser)))))))
    (assoc component 
           :zk-client           nil 
           :server              nil 
           :events-from-browser nil 
           :deployments-watch   nil
           :deployments         nil 
           :tracking            nil)))

(defn new-http-server [peer-config]
  (map->HttpServer {:peer-config peer-config}))
