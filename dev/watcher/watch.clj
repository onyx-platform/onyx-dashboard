(ns watch
  (:require [juxt.dirwatch :as dw]
            [taoensso.timbre :as timbre]))


(def watcher (atom nil))
(def reset-fn (atom nil))

(defn start-watching []
  (if-not @watcher
    (reset! watcher
            (dw/watch-dir (fn [{file :file}]
                            (let [file-name (.getName file)]
                              (when (re-matches #".*\.clj$" file-name)
                                (timbre/info "Reload triggered by: " file-name)
                                (with-bindings {#'*ns* *ns*}
                                  (when @reset-fn (@reset-fn))))))
                          (clojure.java.io/file "src/server")))))

(defn stop-watching []
  (swap! watcher dw/close-watcher))
