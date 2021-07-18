(ns doctor.ui.views.dock
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             [clawe.workspaces :as clawe.workspaces]
             [clawe.scratchpad :as scratchpad]
             [clawe.awesome :as c.awm]
             ]
       :cljs [[wing.core :as w]
              [clojure.string :as string]
              [uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]
              [hiccup-icons.octicons :as octicons]
              [hiccup-icons.fa :as fa]
              [hiccup-icons.fa4 :as fa4]
              [hiccup-icons.mdi :as mdi]
              ])))

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
     (println "pushing to workspaces stream (updating dock)!")
     (s/put! *workspaces-stream* (active-workspaces))))

#?(:clj
   (comment
     (->>
       (active-workspaces)
       (sort-by :awesome/index)
       first)

     (update-dock)
     ))

(defhandler hide-workspace [item]
  (println "hide wsp" (:name item))
  (->
    item
    ;; :name
    ;; clawe.workspaces/for-name
    clawe.workspaces/merge-awm-tags
    scratchpad/toggle-scratchpad)
  (update-dock))

(defhandler show-workspace [item]
  (println "show wsp" (:name item))
  (->
    item
    ;; :name
    ;; clawe.workspaces/for-name
    clawe.workspaces/merge-awm-tags
    scratchpad/toggle-scratchpad)
  (update-dock))

(defhandler bring-dock-above []
  (println "bring dock above")
  (c.awm/awm-fnl
    '(->
       (client.get)
       (lume.filter (fn [c] (= c.name "clover/doctor-dock")))
       (lume.first)
       ((fn [c]
          (tset c :above true)
          (tset c :below false))))))

(defhandler push-dock-below []
  (println "push dock below")
  (c.awm/awm-fnl
    '(->
       (client.get)
       (lume.filter (fn [c] (= c.name "clover/doctor-dock")))
       (lume.first)
       ((fn [c]
          (tset c :above false)
          (tset c :below true)
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-workspaces []
     (let [workspaces  (plasma.uix/state [])
           handle-resp (fn [new-wsps]
                         (swap! workspaces
                                (fn [wsps]
                                  (->>
                                    (concat
                                      ;; TODO work around this keeping the 'old' ones
                                      ;; (or wsps [])
                                      new-wsps)
                                    (w/distinct-by :workspace/title)
                                    (sort-by :awesome/index)))))]

       (with-rpc [] (get-workspaces) handle-resp)
       (with-stream [] (workspaces-stream) handle-resp)

       {:items @workspaces})))

#?(:cljs
   (defn ->actions [item]
     (let [{:keys [workspace/title
                   git/repo
                   workspace/directory
                   workspace/color
                   workspace/title-hiccup
                   awesome/index
                   workspace/scratchpad
                   awesome/clients
                   awesome/urgent
                   awesome/selected]} item]
       (->>
         [(when selected
            {:action/label    "hide"
             :action/on-click #(hide-workspace item)})
          (when-not selected
            {:action/label    "show"
             :action/on-click #(show-workspace item)})]
         (remove nil?)))))

#?(:cljs
   (defn client->icon [client]
     ;; TODO namespace client keys
     ;; TODO get filepaths for class == emacs clients
     ;; i.e. emacs clients could have types for clojure project, react/python project, org file, etc
     (let [{:keys [class]} client]
       (cond
         (= "Emacs" class)
         {:color "text-city-blue-400"
          :icon  octicons/beaker}

         (= "Alacritty" class)
         {:color "text-city-green-600"
          :icon  octicons/terminal16}

         (= "Spotify" class)
         {:color "text-city-green-400"
          :icon  fa/music-solid}

         (= "firefox" class)
         {:color "text-city-green-400"
          :icon  fa4/firefox}

         (= "firefoxdeveloperedition" class)
         {:color "text-city-green-600"
          :icon  fa4/firefox}

         (= "Slack" class)
         {:color "text-city-green-400"
          :icon  fa4/slack}

         (= "Rofi" class)
         {:color "text-city-green-400"
          :icon  octicons/terminal}

         :else
         (println "missing icon for client" client)))))

#?(:cljs
   (defn client-icons [clients]
     (when (seq clients)
       [:div
        {:class ["flex" "flex-row"]}
        (for [c (->> clients
                     (remove (comp #(= "clover/doctor-dock" %) :name)))]
          (let [c-name               (->> c :name (take 15) (apply str))
                {:keys [urgent]}     c
                {:keys [color icon]} (client->icon c)]
            ^{:key (:window c)}
            [:div
             {:on-click #(js/alert c)
              :class    ["flex" "flex-row" "items-center"]}
             [:div
              {:class [(cond urgent "text-city-red-400"
                             color  color
                             :else  "text-city-blue-400")
                       "text-3xl"
                       "p-2"]}
              (or icon c-name)]]))])))

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
                    awesome/urgent
                    ]} wsp
            dir-path   (string/replace (or repo directory) "/home/russ" "~")
            hovering?  (uix/state false)]
        [:div
         {:class
          ["m-1" "p-4" "mt-auto"
           "border" "border-city-blue-600"
           "bg-yo-blue-700"
           "text-white"
           "transform"
           "hover:scale-110"
           "duration-300"]
          :on-mouse-enter #(do (reset! hovering? true)
                               (bring-dock-above))
          :on-mouse-leave #(do (reset! hovering? false)
                               (push-dock-below))}
         [:div
          {:class ["flex" "flex-row"
                   "items-center"]}
          [client-icons clients]

          [:div
           {:class ["font-nes" "text-lg" "px-2"
                    (cond
                      urgent   "text-city-red-400"
                      selected "text-city-orange-400"
                      color    ""
                      :else    "text-yo-blue-300")]
            :style (when (and (not selected) (not urgent) color) {:color color})}
           (when (#{0} (count clients))
             (str "(" index ") "))

           title]]

         (when @hovering?
           (str "(" index ")"))

         (when @hovering?
           [:div
            (when scratchpad
              (str "#scratchpad"))

            (when repo
              (str "#repo"))])

         (when @hovering?
           (when title-hiccup
             [:div title-hiccup]))

         (when @hovering?
           (when dir-path
             [:div dir-path]))

         (when @hovering?
           [:div
            (for [ax (->actions wsp)]
              ^{:key (:action/label ax)}
              [:div
               {:class    ["cursor-pointer"
                           "hover:text-yo-blue-300"]
                :on-click (:action/on-click ax)}
               (:action/label ax)])])]))))

#?(:cljs
   (defn widget []
     (let [{:keys [items]} (use-workspaces)]
       [:div
        {:class ["flex" "flex-row"
                 "justify-center"
                 "min-h-screen"
                 "overflow-hidden"]}
        (for [[i it] (->> items (map-indexed vector))]
          ^{:key i}
          [workspace-comp nil it])])))
