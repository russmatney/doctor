(ns doctor.ui.views.dock
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             [clawe.workspaces :as clawe.workspaces]]
       :cljs [[wing.core :as w]
              [doctor.ui.connected :as connected]
              [clojure.string :as string]
              [uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn active-workspaces []
     (->>
       (clawe.workspaces/all-workspaces)
       (filter :awesome/tag)
       (map #(dissoc % :rules/apply))) ))

(defhandler get-workspaces []
  (active-workspaces))

(defhandler get-clients []
  ;; (->>
  ;;   (clawe.clients/all-clients)
  ;;   (filter :awesome/tag))
  []
  )

#?(:clj
   (defsys *workspaces-stream*
     :start (s/stream)
     :stop (s/close! *workspaces-stream*)))

#?(:clj
   (comment
     (sys/start! `*workspaces-stream*)
     ))


(defstream workspaces-stream [] *workspaces-stream*)

#?(:clj
   (defn update-dock []
     (s/put! *workspaces-stream* (active-workspaces))))

#?(:clj
   (comment
     (->>
       (active-workspaces)
       (sort-by :awesome/index)
       first)

     (update-dock)
     ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-workspaces []
     (let [workspaces  (plasma.uix/state [])
           handle-resp (fn [new-wsps]
                         (println "use-wsps recd" (count new-wsps))
                         (println (->> new-wsps first :name))
                         (println (->> new-wsps last :name))
                         (swap! workspaces
                                (fn [wsps]
                                  (->>
                                    (concat
                                      ;; TODO work around this keeping the 'old' ones
                                      ;; (or wsps [])
                                      new-wsps)
                                    (w/distinct-by :workspace/title)
                                    (sort-by :awesome/index)))))]

       (with-rpc [@connected/connected?] (get-workspaces) handle-resp)
       (with-stream [@connected/connected?] (workspaces-stream) handle-resp)

       {:items @workspaces})))

#?(:cljs
   (defn workspace-comp
     ([wsp] (workspace-comp nil wsp))
     ([_opts wsp]
      (let [{:keys [workspace/title
                    git/repo
                    workspace/directory
                    workspace/color
                    workspace/title-hiccup
                    awesome/index
                    workspace/scratchpad
                    awesome/clients
                    awesome/selected
                    ]} wsp
            dir-path   (string/replace (or repo directory) "/home/russ" "~")
            hovering?  (uix/state false)]
        [:div
         {:class          ["m-1" "p-4"
                           "border" "border-city-blue-600"
                           "bg-yo-blue-700"
                           "text-white"]
          :on-mouse-enter #(reset! hovering? true)
          :on-mouse-leave #(reset! hovering? false)}
         [:div
          {:class ["font-nes"]
           :style (when color {:color color})}
          (str "(" index ")")
          (when selected
            [:span "#*!"])]

         [:div
          {:class ["font-mono" "text-lg"]
           :style (when color {:color color})}
          title]

         (when (or (not scratchpad) @hovering?)
           [:div
            (when scratchpad
              (str "#scratchpad"))

            (when repo
              (str "#repo"))])

         (when (or (not scratchpad) @hovering?)
           (when (seq clients)
             [:div
              (for [c (->> clients)]
                (let [c-name (->> c :name (take 10) (apply str))]
                  ^{:key (:window c)}
                  [:div c-name]))]))

         (when (or (not scratchpad) @hovering?)
           (when title-hiccup
             [:div title-hiccup]))

         (when (or (not scratchpad) @hovering?)
           (when dir-path
             [:div dir-path]))]))))

#?(:cljs
   (defn widget []
     (let [{:keys [items]} (use-workspaces)]
       (println (count items) "workspaces received")
       (println (->> items first :name))
       (println (->> items last :name))
       [:div
        {:class ["flex" "flex-row"
                 "justify-center"
                 "overflow-hidden"]}
        (for [[i it] (->> items (map-indexed vector))]
          ^{:key i}
          [workspace-comp nil it])])))
