(ns onyx-dashboard.dev
  (:require [environ.core :refer [env]]
            [net.cgrand.enlive-html :refer [set-attr prepend append html]]
            [cemerick.piggieback :as piggieback]
            ;[weasel.repl.websocket :as weasel]
            [leiningen.core.main :as lein]))

(def is-dev? (env :is-dev))

(def inject-devmode-html
  (comp
    (set-attr :class "is-dev")))

; (defn browser-repl []
;   (let [repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)]
;     (piggieback/cljs-repl :repl-env repl-env)
;     (piggieback/cljs-eval repl-env '(in-ns 'onyx-dashboard.core) {})))

(defn start-figwheel []
  (future
    (print "Starting figwheel.\n")
    (lein/-main ["figwheel"])))
