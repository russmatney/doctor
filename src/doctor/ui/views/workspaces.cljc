(ns doctor.ui.views.workspaces
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [
             [tick.alpha.api :as t]
             [systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             ]
       :cljs [
              [wing.core :as w]
              [doctor.ui.connected :as connected]
              [plasma.uix :refer [with-rpc with-stream]]
              [tick.alpha.api :as t]
              ])
   ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler get-workspaces []
  (->> []))

#?(:clj
   (defsys *workspaces-stream*
     :start (s/stream)
     :stop (s/close! *workspaces-stream*)))

#?(:clj
   (comment
     (sys/start! `*workspaces-stream*)

     ))

(defstream workspaces-stream [] *workspaces-stream*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-workspaces []
     (let [workspaces  (plasma.uix/state [])
           handle-resp (fn [new-wsps]
                         (swap! workspaces
                                (fn [wsps]
                                  ;; (println (str "received " (count wsps) " journals."))
                                  ;; (println wsps)
                                  (->>
                                    (concat (or wsps {}) new-wsps)
                                    (w/index-by :org/id)
                                    vals))))]

       (with-rpc [@connected/connected?] (get-workspaces) handle-resp)
       (with-stream [@connected/connected?] (workspaces-stream) handle-resp)

       {:items @workspaces})))

#?(:cljs
   (defn workspace-comp
     ([wsp] (workspace-comp nil wsp))
     ([opts wsp]
      [:div "Workspaces" (str wsp)]
      ))
   )

#?(:cljs
   (defn widget []
     (let [{:keys [items]} (use-workspaces)]
       [:div
        {:class ["p-4"]}
        [:h1
         {:class ["font-nes" "text-2xl" "text-white"
                  "pb-2"]}
         (str "Workspaces (" (count items) ")")]

        (for [it items]
          [workspace-comp nil it])]
       )))
