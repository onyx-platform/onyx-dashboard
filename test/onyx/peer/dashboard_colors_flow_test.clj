(ns onyx.peer.dashboard-colors-flow-test
  (:require [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [clojure.test :refer :all]

            [com.stuartsierra.component :as component]
            [onyx-dashboard.system :as sys]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.api]))
