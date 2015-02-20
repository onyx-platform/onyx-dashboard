(ns onyx-dashboard.dev
 (:require [onyx-dashboard.core :as core]
            [figwheel.client :as figwheel :include-macros true]
            [cljs.core.async :refer [put!]]
            ;[weasel.repl :as weasel]
            ))

(enable-console-print!)

(def is-dev? true)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3428/figwheel-ws"
  :jsload-callback (fn []
                     (core/main is-dev?)))

;(weasel/connect "ws://localhost:9001" :verbose true :print #{:repl :console})

(core/main is-dev?)
