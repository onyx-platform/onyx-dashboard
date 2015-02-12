(ns onyx-dashboard.components.log
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.pagination :as pg]
            [om-bootstrap.panel :as p]
            [om-bootstrap.table :as t]
            [cljsjs.moment]
            [onyx-dashboard.components.ui-elements :refer [section-header-collapsible]]))

(defcomponent log-entry-row [entry owner]
  (render [_] 
          (dom/tr {:key (str "entry-" (:message-id entry))
                   :title (str (om/value entry))}
                  (dom/td (str (:message-id entry)))
                  (dom/td (str (:id (:args entry))))
                  (dom/td (str (:fn entry)))
                  (dom/td (.fromNow (js/moment (str (js/Date. (:created-at entry)))))))))

(defcomponent log-entries-table [entries owner]
  (render [_]
          (t/table {:striped? true :bordered? true :condensed? true :hover? true}
                   (dom/thead (dom/tr (dom/th "#") (dom/th "ID") (dom/th "fn") (dom/th "Time")))
                   (dom/tbody (map (fn [entry]
                                     (om/build log-entry-row entry {:react-key (str (:message-id entry))}))
                                   entries)))))

(def entries-per-page 20)
(def num-pages-to-show 10)

(defn pagination-info [entries start-index]
  (let [num-pages (Math/ceil (/ (count entries) entries-per-page))] 
    {:displayed-entries (take entries-per-page
                              (keep entries 
                                    (reverse (range 0 
                                                    (inc start-index)))))
     :current-page (- num-pages (Math/ceil (* num-pages (/ start-index (count entries)))))
     :num-pages num-pages}))


(defcomponent log-entries-pager [{:keys [job-filter entries] :as log} owner]
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
                          {:keys [num-pages current-page displayed-entries]} (pagination-info filtered-entries current-index)
                          pagination-start (max (- current-page 
                                                   (/ num-pages-to-show 2)) 
                                                0)
                          pages-window (take num-pages-to-show (range pagination-start num-pages))
                          pages-to-show (cond-> pages-window
                                          (not= 0 (first pages-window)) (conj 0)
                                          (not= (dec num-pages) 
                                                (last pages-window)) (concat [(dec num-pages)]))]
                      (p/panel {:header (om/build section-header-collapsible 
                                                  {:text (str "Raw Cluster Activity" 
                                                              (if job-filter (str " - Job " job-filter)))}
                                                  {})
                                :collapsible? true
                                :bs-style "primary"}
                               (dom/div
                                 (om/build log-entries-table displayed-entries {})
                                 (pg/pagination {}
                                                (pg/previous 
                                                  (if (zero? current-page) 
                                                    {:disabled? true}
                                                    {:on-click (fn [e]
                                                                 (om/set-state! owner 
                                                                                :entry-index 
                                                                                (let [new-index (min max-id 
                                                                                                     (+ current-index entries-per-page))]
                                                                                  (if (= new-index max-id)
                                                                                    nil
                                                                                    new-index)))
                                                                 (.preventDefault e))}))
                                                (for [pg pages-to-show]
                                                  (pg/page (if (= current-page pg) 
                                                             {:active? true}
                                                             {:on-click (fn [e]
                                                                          (om/set-state! owner 
                                                                                         :entry-index 
                                                                                         (let [new-index (max 0 
                                                                                                              (min max-id 
                                                                                                                   (- max-id 
                                                                                                                      (* pg entries-per-page))))]
                                                                                           (if (= new-index max-id) nil new-index)))
                                                                          (.preventDefault e))}) 
                                                           (str (inc pg))))
                                                (pg/next 
                                                  (if (= current-page (dec num-pages))
                                                    {:disabled? true}
                                                    {:on-click (fn [e]
                                                                 (om/set-state! owner 
                                                                                :entry-index 
                                                                                (max 0 
                                                                                     (- current-index entries-per-page)))
                                                                 (.preventDefault e))}))))))))))
