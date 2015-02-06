(ns onyx-dashboard.components.log
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.pagination :as pg]
            [om-bootstrap.panel :as p]
            [om-bootstrap.table :as t]
            [onyx-dashboard.components.ui-elements :refer [section-header]]))

(defcomponent log-entry-row [entry owner]
  (render [_] 
          (dom/tr {:key (str "entry-" (:message-id entry))
                   :title (str (om/value entry))}
                  (dom/td (str (:id (:args entry))))
                  (dom/td (str (:fn entry)))
                  (dom/td (.fromNow (js/moment (str (js/Date. (:created-at entry)))))))))

(defcomponent log-entries-table [entries owner]
  (render [_]
          (t/table {:striped? true :bordered? true :condensed? true :hover? true}
                   (dom/thead (dom/tr (dom/th "ID") (dom/th "fn") (dom/th "Time")))
                   (dom/tbody (map (fn [entry]
                                     (om/build log-entry-row entry {}))
                                   entries)))))

(def entries-per-page 
  10)

(defn log-page-info [entries current-page]
  (let [displayed-entries (take entries-per-page 
                                (drop (* current-page entries-per-page) 
                                      (reverse (sort-by key entries))))]
    {:entries (map second displayed-entries) 
     :num-pages (Math/ceil (/ (count entries) entries-per-page))}))

(defcomponent log-entries-pager [{:keys [entries visible] :as log} owner]
  (init-state [_]
              {:current-page 0})
  (render-state [_ {:keys [current-page]}]
                (let [entries-selection (log-page-info entries current-page)
                      num-pages (:num-pages entries-selection)] 
                  (p/panel {:header (om/build section-header 
                                              {:text "Raw Cluster Activity" 
                                               :visible visible 
                                               :type :log-entries} {})
                            :bs-style "primary"}
                           (if visible
                             (dom/div
                               (om/build log-entries-table (:entries entries-selection) {})
                               (pg/pagination {}
                                              (pg/previous 
                                                (if (zero? current-page) 
                                                  {:disabled? true}
                                                  {:on-click (fn [_]
                                                               (om/update-state! owner :current-page dec))}))
                                              (for [pg (range 0 num-pages)]
                                                (pg/page (if (= current-page pg) 
                                                           {:active? true}
                                                           {:on-click (fn [_]
                                                                        (om/set-state! owner :current-page pg))}) 
                                                         (str (inc pg))))
                                              (pg/next 
                                                (if (= current-page num-pages)
                                                  {:disabled? true}
                                                  {:on-click (fn [_]
                                                               (om/update-state! owner :current-page inc))})))))))))
