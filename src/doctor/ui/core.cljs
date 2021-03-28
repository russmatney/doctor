(ns doctor.ui.core
  (:require
   [uix.dom.alpha :as uix.dom]
   [doctor.ui.views.core :as views]
   [doctor.ui.tubes :as tubes]
   [time-literals.data-readers]
   [time-literals.read-write]
   [tick.timezone]
   [tick.locale-en-us]))


(defn dev-setup []
  (enable-console-print!))

(defn ^:dev/after-load mount-root []
  (time-literals.read-write/print-time-literals-cljs!)
  (uix.dom/render
    [views/root]
    (.getElementById js/document "app")))

(defn ^:export init
  []
  (tubes/setup)
  (dev-setup)
  (mount-root))
