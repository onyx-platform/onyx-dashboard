(ns onyx-dashboard.components.log
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.panel :as p]
            [om-bootstrap.table :as t]
            [onyx-dashboard.components.ui-elements :refer [section-header]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go go-loop]]))

(defcomponent log-entry-row [entry owner]
  (render [_] 
          (dom/tr {:key (str "entry-" (:message-id entry))
                   :title (str (om/value entry))}
                  (dom/td (str (:id (:args entry))))
                  (dom/td (str (:fn entry)))
                  (dom/td (.fromNow (js/moment (str (js/Date. (:created-at entry)))))))))

(defcomponent log-entries-table [{:keys [entries visible]} owner]
  (render [_]
          (p/panel
            {:header (om/build section-header 
                               {:text "Cluster Activity" 
                                :visible visible 
                                :type :log-entries} {})
             :bs-style "primary"}
           (if visible
             (t/table {:striped? true :bordered? true :condensed? true :hover? true}
                      (dom/thead (dom/tr (dom/th "ID") (dom/th "fn") (dom/th "Time")))
                      (dom/tbody (map (fn [entry]
                                        (om/build log-entry-row entry {}))
                                      entries)))))))
