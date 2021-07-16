(ns doctor.ui.views.screenshots
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             ;; [clawe.screenshots :as clawe.screenshots]
             [clawe.scratchpad :as scratchpad]
             [clawe.awesome :as c.awm]
             ]
       :cljs [[wing.core :as w]
              [doctor.ui.connected :as connected]
              [clojure.string :as string]
              [uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn active-screenshots []
     ;; (->>
     ;;   (clawe.screenshots/all-screenshots)
     ;;   (filter :awesome/tag)
     ;;   (map #(dissoc % :rules/apply))
     ;;   )
     [{:name "hi"}]
     ))

(defhandler get-screenshots []
  (active-screenshots))

#?(:clj
   (defsys *screenshots-stream*
     :start (s/stream)
     :stop (s/close! *screenshots-stream*)))

#?(:clj
   (comment
     (sys/start! `*screenshots-stream*)
     ))

(defstream screenshots-stream [] *screenshots-stream*)


#?(:clj
   (defn update-dock []
     (println "pushing to screenshots stream (updating dock)!")
     (s/put! *screenshots-stream* (active-screenshots))))

#?(:clj
   (comment
     (->>
       (active-screenshots)
       first)

     (update-dock)
     ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-screenshots []
     (let [screenshots (plasma.uix/state [])
           handle-resp (fn [new-items]
                         (swap! screenshots
                                (fn [items]
                                  (->>
                                    (concat
                                      ;; TODO work around this keeping/merging with the 'old' ones
                                      ;; (or items [])
                                      new-items)
                                    (w/distinct-by :screenshot/title)))))]

       (with-rpc [@connected/connected?] (get-screenshots) handle-resp)
       (with-stream [@connected/connected?] (screenshots-stream) handle-resp)

       {:items @screenshots})))

#?(:cljs
   (defn ->actions [item]
     (let [{:keys []} item]
       (->>
         [{:action/label    "hide"
           :action/on-click #(js/alert item)}]
         (remove nil?)))))


#?(:cljs
   (defn screenshot-comp
     ([item] (screenshot-comp nil item))
     ([_opts item]
      (let [{:keys []} item
            hovering?  (uix/state false)]
        [:div
         {:class
          ["m-1" "p-4" "mt-auto"
           "border" "border-city-blue-600"
           "bg-yo-blue-700"
           "text-white"]
          :on-mouse-enter #(do (reset! hovering? true))
          :on-mouse-leave #(do (reset! hovering? false))}
         [:div
          {:class ["font-nes" "text-lg"]}
          "screenshot"]

         (when @hovering?
           [:div
            (for [ax (->actions item)]
              ^{:key (:action/label ax)}
              [:div
               {:class    ["cursor-pointer"
                           "hover:text-yo-blue-300"]
                :on-click (:action/on-click ax)}
               (:action/label ax)])])]))))

#?(:cljs
   (defn widget []
     (let [{:keys [items]} (use-screenshots)]
       [:div
        {:class ["flex" "flex-row"
                 "justify-center"
                 "min-h-screen"
                 "overflow-hidden"]}
        (for [[i it] (->> items (map-indexed vector))]
          ^{:key i}
          [screenshot-comp nil it])])))
