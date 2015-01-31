(ns onyx-dashboard.components.code
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]))

(defcomponent clojure-block [data owner]
  (render-state [_ _] (dom/div (:input data)))

  (did-mount [_]
             (let [editor (.edit js/ace (om/get-node owner))]
               (.setOptions editor (clj->js {:maxLines 15}))
               (.setMode (.getSession editor) "ace/mode/clojure")
               (.setHighlightActiveLine editor false)
               (.setHighlightGutterLine editor false)
               (.setReadOnly editor true)
               (set! (.-opacity (.-style (.-element (.-$cursorLayer (.-renderer editor))))) 0)
               (om/set-state! owner :dom-editor editor)))
  (will-unmount [_]
                ; not sure if this helps with lost node issues yet
                (let [editor (om/get-state owner :dom-editor)] 
                  (.destroy editor)
                  #_(.. editor container remove))))
