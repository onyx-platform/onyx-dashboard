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

(defn pagination-info [entries start-index]
  (let [num-pages (Math/ceil (/ (count entries) entries-per-page))] 
    {:displayed-entries (reverse 
                          (keep entries 
                                (range (max 0 (inc (- start-index entries-per-page))) 
                                       (inc start-index))))
     :current-page (- num-pages (Math/ceil (* num-pages (/ start-index (count entries)))))
     :num-pages num-pages}))

(defcomponent log-entries-pager [{:keys [job-filter entries visible] :as log} owner]
  (init-state [_]
              {:entry-index nil})
  (render-state [_ {:keys [entry-index]}]
                (let [filtered-entries (if job-filter
                                         (into {} 
                                               (filter (comp (partial = job-filter)
                                                             :job
                                                             :args
                                                             val) 
                                                       entries))
                                         entries)] 
                  (if (empty? filtered-entries)
                    (dom/div {} "")
                    (let [max-id (apply max (keep :message-id (vals filtered-entries)))
                          current-index (or entry-index max-id)
                          {:keys [num-pages current-page displayed-entries]} (pagination-info filtered-entries current-index)]
                      (p/panel {:header (om/build section-header 
                                                  {:text (str "Raw Cluster Activity" 
                                                              (if job-filter (str " - Job " job-filter))) 
                                                   :visible visible 
                                                   :type :log-entries} {})
                                :bs-style "primary"}
                               (if visible
                                 (dom/div
                                   (om/build log-entries-table displayed-entries {})
                                   (pg/pagination {}
                                                  (pg/previous 
                                                    (if (zero? current-page) 
                                                      {:disabled? true}
                                                      {:on-click (fn [_]
                                                                   (om/set-state! owner 
                                                                                  :entry-index 
                                                                                  (min max-id 
                                                                                       (+ current-index entries-per-page))))}))
                                                  (for [pg (range 0 num-pages)]
                                                    (pg/page (if (= current-page pg) 
                                                               {:active? true}
                                                               {:on-click (fn [_]
                                                                            (om/set-state! owner 
                                                                                           :entry-index 
                                                                                           (max 0 
                                                                                                (min max-id 
                                                                                                     (- max-id 
                                                                                                        (* pg entries-per-page))))))}) 
                                                             (str (inc pg))))
                                                  (pg/next 
                                                    (if (= current-page (dec num-pages))
                                                      {:disabled? true}
                                                      {:on-click (fn [_]
                                                                   (om/set-state! owner 
                                                                                  :entry-index 
                                                                                  (max 0 
                                                                                       (- current-index entries-per-page))))})))))))))))
