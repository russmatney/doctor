(ns doctor.ui.views.wallpapers
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             [clawe.wallpapers :as c.wallpapers]]
       :cljs [[wing.core :as w]
              [uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn active-wallpapers []
     (let [all     (c.wallpapers/all-wallpapers)
           example {:name          "Example item"
                    :file/filename "some filepath"}]
       (->>
         (conj all example)
         (take 30)
         (into [])))))

(defhandler get-wallpapers []
  (active-wallpapers))

#?(:clj
   (defsys *wallpapers-stream*
     :start (s/stream)
     :stop (s/close! *wallpapers-stream*)))

#?(:clj
   (comment
     (sys/start! `*wallpapers-stream*)
     ))

(defstream wallpapers-stream [] *wallpapers-stream*)


#?(:clj
   (defn update-wallpapers []
     (println "pushing to wallpapers stream (updating wallpapers)!")
     (s/put! *wallpapers-stream* (active-wallpapers))))

#?(:clj
   (comment
     (->>
       (active-wallpapers)
       first)

     (update-wallpapers)
     ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-wallpapers []
     (let [wallpapers  (plasma.uix/state [])
           handle-resp (fn [new-items]
                         (println "new-items" (count new-items))
                         (swap! wallpapers
                                (fn [items]
                                  (->>
                                    (concat
                                      ;; TODO work around this keeping/merging with the 'old' ones
                                      ;; (or items [])
                                      new-items)
                                    (w/distinct-by :file/filename)))))]

       (with-rpc [] (get-wallpapers) handle-resp)
       (with-stream [] (wallpapers-stream) handle-resp)

       {:items @wallpapers})))

#?(:cljs
   (defn ->actions [item]
     (let [{:keys []} item]
       (->>
         [{:action/label    "js/alert"
           :action/on-click #(js/alert item)}]
         (remove nil?)))))


#?(:cljs
   (defn screenshot-comp
     ([item] (screenshot-comp nil item))
     ([_opts item]
      (let [{:keys [name
                    file/filename
                    file/web-asset-path
                    ]} item
            hovering?  (uix/state false)]
        [:div
         {:class
          ["m-1" "p-4"
           "border" "border-city-blue-600"
           "bg-yo-blue-700"
           "text-white"]
          :on-mouse-enter #(do (reset! hovering? true))
          :on-mouse-leave #(do (reset! hovering? false))}
         (when web-asset-path
           [:img {:src   web-asset-path
                  :class ["max-w-xl"
                          "max-h-72"]}])

         ;; [:div
         ;;  {:class ["font-nes" "text-lg"]}
         ;;  name]

         ;; [:div
         ;;  filename]

         [:div
          (for [ax (->actions item)]
            ^{:key (:action/label ax)}
            [:div
             {:class    ["cursor-pointer"
                         "hover:text-yo-blue-300"]
              :on-click (:action/on-click ax)}
             (:action/label ax)])]]))))

#?(:cljs
   (defn widget []
     (let [{:keys [items]} (use-wallpapers)]
       [:div
        {:class ["flex" "flex-row" "flex-wrap"
                 "min-h-screen"
                 "overflow-hidden"
                 "bg-yo-blue-700"
                 ]}
        (for [[i it] (->> items (map-indexed vector))]
          ^{:key i}
          [screenshot-comp nil it])])))
