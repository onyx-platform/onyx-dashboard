(ns onyx-dashboard.prod
  (:require [onyx-dashboard.core :as core]))

(def is-dev? false)

(core/main is-dev?)
