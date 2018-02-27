(ns onyx-dashboard.components.navbar-top
  (:require [om.core :as om :include-macros true]
    ; om
    [om-tools.dom  :as dom :include-macros true]
    [om-tools.core :refer-macros [defcomponent]]
    ; om bootstrap
    [om-bootstrap.nav    :as n]
    [cljs.core.async :as async :refer [<! >! put! chan]])
  (:require-macros [cljs.core.async.macros :as asyncm :refer [go-loop]]))

(defcomponent navbar-top [_ owner]
  (render [_]
    (let [api-ch (om/get-shared owner :api-ch)]
      (n/navbar {:class "navbar-top"
                 :fluid true
                 :brand (dom/a {:on-click (fn [_] (put! api-ch [:menu-tenancies nil]))
                                :href "#"}
         (dom/div {:class "logo-app"}
            (dom/img {:class  "logo"
                      :src    "/img/high-res.png"
                      :height 40})
            (dom/span {} "Onyx dashboard")))}))))