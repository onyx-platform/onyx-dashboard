(ns cljs.user
  (:require
   [cljs.pprint :refer [pprint]]
   [devtools.core :as devtools]
   ;[untangled-todomvc.core :as core]
    ;untangled-todomvc.mutations
   [untangled.client.logging :as log]
   ;[untangled-todomvc.ui :as ui]
   ;[untangled-todomvc.routing :refer [configure-routing!]]
   [untangled.client.core :as uc]
   [cljs.reader :as reader]))

(enable-console-print!)

(defonce cljs-build-tools
  (do (devtools/enable-feature! :sanity-hints)
      (devtools.core/install!)))

(log/set-level :debug)
(reset! core/app (uc/mount @core/app ui/Root "app"))

(defn log-app-state
  "Helper for logging the app-state, pass in top-level keywords from the app-state and it will print only those
  keys and their values."
  [& keywords]
  (pprint (let [app-state @(:reconciler @core/app)]
            (if (= 0 (count keywords))
              app-state
              (select-keys app-state keywords)))))
