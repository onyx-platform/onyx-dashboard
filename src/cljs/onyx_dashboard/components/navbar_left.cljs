(ns onyx-dashboard.components.navbar-left
  (:require [om.core :as om :include-macros true]
    ; om
    [om-tools.dom  :as dom :include-macros true]
    [om-tools.core :refer-macros [defcomponent]]
    ; om bootstrap
    [om-bootstrap.nav :as n]
    [cljs.core.async :as async :refer [<! >! put! chan]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go-loop]]))

(defn menu-item-class [page-kw current-page-kw]
  (if (= page-kw current-page-kw)
    "menu-active"
    "menu"))

(defcomponent navbar-left [curr-page owner]
  (render [_]
    (let [api-ch (om/get-shared owner :api-ch)]
      (dom/aside {:class "navbar-left"}
         (dom/div {:class "navbar-left-inner"}
            (dom/ul {:class "nav"}
              (dom/li {:class (menu-item-class :page/tenancies curr-page)
                       :on-click (fn [_] (put! api-ch [:menu-tenancies nil]))}
                (dom/a {} "Tenancies"))
              (dom/li {:class (menu-item-class :page/tenancy curr-page)
                       :on-click (fn [_] (put! api-ch [:menu-tenancy nil]))}
                (dom/a {} "Tenancy"))
              (dom/li {:class (menu-item-class :page/job curr-page)
                       :on-click (fn [_] (put! api-ch[:menu-job nil]))}
                (dom/a {} "Job"))
              (dom/li {:class (menu-item-class :page/log-entries curr-page)
                       :on-click (fn [_] (put! api-ch [:menu-log-entries nil]))}
                (dom/a {} "Log entries"))
              (dom/li {:class (menu-item-class :page/time-travel curr-page)
                       :on-click (fn [_] (put! api-ch [:menu-time-travel nil]))}
                (dom/a {} "Time travel"))))))))