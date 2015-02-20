(ns onyx-dashboard.components.log
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-bootstrap.pagination :as pg]
            [om-bootstrap.panel :as p]
            [om-bootstrap.modal :as md]
            [om-bootstrap.button :as b]
            [om-bootstrap.table :as t]
            [cljsjs.moment]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [onyx-dashboard.components.ui-elements :refer [section-header-collapsible]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go-loop]]))

(defcomponent log-entry-row [entry owner {:keys [entry-ch] :as opts}]
  (render [_] 
          (dom/tr {:key (str "entry-" (:message-id entry))}
                  (dom/td {:on-click (fn [e] 
                                       (put! entry-ch {:id (:message-id entry)})
                                       true)}
                          (dom/i {:class "fa fa-external-link"})) 
                  (dom/td (str (:message-id entry)))
                  (dom/td (str (:id (:args entry))))
                  (dom/td (str (:fn entry)))
                  (dom/td (.fromNow (js/moment (str (js/Date. (:created-at entry)))))))))

(defcomponent log-entries-table [entries owner opts]
  (render [_]
          (t/table {:striped? true :bordered? true :condensed? true :hover? true}
                   (dom/thead (dom/tr (dom/th) (dom/th "#") (dom/th "ID") (dom/th "fn") (dom/th "Time")))
                   (dom/tbody (map (fn [entry]
                                     (om/build log-entry-row entry {:opts opts
                                                                    :react-key (str (:message-id entry))}))
                                   entries)))))

(def entries-per-page 20)
(def num-pages-to-show 10)

(defcomponent log-entry-modal [log-entry owner {:keys [entry-ch]}]
  (render [_]
          (md/modal {:header (dom/h4 "Log Entry " (:message-id log-entry))
                     :footer (dom/div (b/button {:on-click (fn [e]
                                                             (put! entry-ch {:id nil}))} 
                                                "Close"))
                     :visible? true}
                    (t/table {:striped? false :bordered? false :condensed? true :hover? false}
                             (dom/tbody 
                               (for [[k v] log-entry]
                                 (dom/tr (dom/td (str k)) 
                                         (dom/td (pr-str v)))))))))

(defn pagination-info [entries start-index]
  (let [num-pages (Math/ceil (/ (count entries) entries-per-page))] 
    {:displayed-entries (take entries-per-page
                              (keep entries 
                                    (reverse (range 0 
                                                    (inc start-index)))))
     :current-page (- num-pages (Math/ceil (* num-pages (/ start-index (count entries)))))
     :num-pages num-pages}))

(defn entry-about-job? [job-id {:keys [args] :as entry}]
  (or (= job-id (:job args))
      (= job-id (:id args))))



(defcomponent log-entries-pager [{:keys [job-filter entries] :as log} owner]
  (init-state [_]
              {:entry-index nil
               :visible-entry nil
               :entry-ch (chan)})
  (will-mount [_]
              (go-loop []
                       (let [entry (:id (<! (om/get-state owner :entry-ch)))]
                         (om/set-state! owner :visible-entry entry))
                       (recur)))
  (render-state [_ {:keys [entry-index visible-entry entry-ch]}]
                (let [filtered-entries (vec (cond->> (vals entries)
                                              job-filter (filter (partial entry-about-job? job-filter))
                                              true (sort-by :message-id)))] 
                  (p/panel {:header (om/build section-header-collapsible 
                                              {:text (str "Raw Cluster Activity" 
                                                          (if job-filter (str " - Job " job-filter)))}
                                              {})
                            :collapsible? true
                            :bs-style "primary"}
                           (if (empty? filtered-entries)
                             (dom/div "No log entries found.")
                             (let [max-id (dec (count filtered-entries))
                                   current-index (or entry-index max-id)
                                   {:keys [num-pages current-page displayed-entries]} (pagination-info filtered-entries current-index)
                                   pagination-start (max (- current-page 
                                                            (/ num-pages-to-show 2)) 
                                                         0)
                                   pages-window (take num-pages-to-show (range pagination-start num-pages))
                                   pages-to-show (cond-> pages-window
                                                   (not= 0 (first pages-window)) (conj 0)
                                                   (not= (dec num-pages) 
                                                         (last pages-window)) (concat [(dec num-pages)]))
                                   change-index (fn [index e]
                                                  (om/set-state! owner :entry-index index)    
                                                  (.preventDefault e)) 
                                   previous-handler (partial change-index 
                                                             (let [new-index (min max-id 
                                                                                  (+ current-index entries-per-page))]
                                                               (if (= new-index max-id)
                                                                 nil
                                                                 new-index)))
                                   next-handler (partial change-index
                                                         (max 0 (- current-index entries-per-page)))]
                               (dom/div

                                 (if visible-entry 
                                   (om/build log-entry-modal (entries visible-entry) {:opts {:entry-ch entry-ch}}))


                                 (dom/div
                                   (om/build log-entries-table displayed-entries {:opts {:entry-ch entry-ch}})

                                   (pg/pagination {}
                                                  (pg/previous 
                                                    (if (zero? current-page) 
                                                      {:disabled? true}
                                                      {:on-click previous-handler}))
                                                  (for [pg pages-to-show]
                                                    (pg/page (if (= current-page pg) 
                                                               {:active? true}
                                                               {:on-click (partial change-index
                                                                                   (let [new-index (max 0 
                                                                                                        (min max-id 
                                                                                                             (- max-id 
                                                                                                                (* pg entries-per-page))))]
                                                                                     (if (= new-index max-id) nil new-index)))}) 
                                                             (str (inc pg))))
                                                  (pg/next 
                                                    (if (= current-page (dec num-pages))
                                                      {:disabled? true}
                                                      {:on-click next-handler}))))


                                 )))))))
