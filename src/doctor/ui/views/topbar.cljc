(ns doctor.ui.views.topbar
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             [clawe.workspaces :as clawe.workspaces]
             [clawe.scratchpad :as scratchpad]
             [ralphie.awesome :as awm]
             [ralphie.battery :as r.battery]
             [ralphie.pulseaudio :as r.pulseaudio]
             [ralphie.spotify :as r.spotify]
             [babashka.process :as process]
             [clojure.string :as string]
             ]
       :cljs [[wing.core :as w]
              [clojure.string :as string]
              [uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]
              [hiccup-icons.octicons :as octicons]
              [hiccup-icons.mdi :as mdi]
              [tick.alpha.api :as t]
              [tick.format :as t.format]
              ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Active workspaces
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn active-workspaces []
     (->>
       (clawe.workspaces/all-workspaces)
       (filter :awesome.tag/name)
       (map clawe.workspaces/apply-git-status)
       (map #(dissoc % :rules/apply :rules/is-my-client?)))))

(defhandler get-workspaces []
  (active-workspaces))

#?(:clj
   (defsys *workspaces-stream*
     :start (s/stream)
     :stop (s/close! *workspaces-stream*)))

#?(:clj
   (comment
     (sys/start! `*workspaces-stream*)))

(defstream workspaces-stream [] *workspaces-stream*)

#?(:clj
   (defn update-topbar []
     (println "pushing to workspaces stream (updating topbar)!")
     (s/put! *workspaces-stream* (active-workspaces))))

#?(:clj
   (comment
     (->>
       (active-workspaces)
       (sort-by :awesome.tag/index)
       first)

     (update-topbar)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Misc metadata
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn build-topbar-metadata []
     (->
       {:microphone/muted (r.pulseaudio/input-muted?)
        :spotify/volume   (r.spotify/spotify-volume-label)
        :audio/volume     (r.pulseaudio/default-sink-volume-label)
        :hostname         (-> (process/$ hostname) process/check :out slurp string/trim)}
       (merge (r.spotify/spotify-current-song)
              (r.battery/info))
       (dissoc :spotify/album-url :spotify/album))))

(defhandler get-topbar-metadata []
  (build-topbar-metadata))

#?(:clj
   (defsys *topbar-metadata-stream*
     :start (s/stream)
     :stop (s/close! *topbar-metadata-stream*)))

#?(:clj
   (comment
     (sys/start! `*topbar-metadata-stream*)))

(defstream topbar-metadata-stream [] *topbar-metadata-stream*)

#?(:clj
   (defn update-topbar-metadata []
     (println "pushing to topbar-metadata stream (updating topbar-metadata)!")
     (s/put! *topbar-metadata-stream* (build-topbar-metadata))))

#?(:clj
   (comment
     (->>
       (build-topbar-metadata)
       (sort-by :awesome.tag/index)
       first)

     (update-topbar-metadata)
     ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Workspace commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler hide-workspace [item]
  (println "hide wsp" (:name item))
  (->
    ;; TODO support non scratchpad workspaces
    item
    ;; :name
    ;; clawe.workspaces/for-name
    clawe.workspaces/merge-awm-tags
    scratchpad/toggle-scratchpad)
  (update-topbar))

(defhandler show-workspace [item]
  (println "show wsp" (:name item))
  (->
    item
    ;; :name
    ;; clawe.workspaces/for-name
    clawe.workspaces/merge-awm-tags
    scratchpad/toggle-scratchpad)
  (update-topbar))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Topbar behavior
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler bring-topbar-above []
  (println "bring topbar above")
  (awm/awm-fnl
    '(->
       (client.get)
       (lume.filter (fn [c] (= c.name "clover/doctor-topbar")))
       (lume.first)
       ((fn [c]
          (tset c :above true)
          (tset c :below false))))))

(defhandler push-topbar-below []
  (println "push topbar below")
  (awm/awm-fnl
    '(->
       (client.get)
       (lume.filter (fn [c] (= c.name "clover/doctor-topbar")))
       (lume.first)
       ((fn [c]
          (tset c :above false)
          (tset c :below true))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend Data contexts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-workspaces []
     (let [workspaces  (plasma.uix/state [])
           handle-resp (fn [new-wsps]
                         (swap! workspaces
                                (fn [_wsps]
                                  (->> new-wsps
                                       (w/distinct-by :workspace/title)
                                       (sort-by :awesome.tag/index)))))]

       (with-rpc [] (get-workspaces) handle-resp)
       (with-stream [] (workspaces-stream) handle-resp)

       {:items @workspaces})))

#?(:cljs
   (defn use-topbar-metadata []
     (let [topbar-metadata (plasma.uix/state [])
           handle-resp     #(reset! topbar-metadata %)]

       (with-rpc [] (get-topbar-metadata) handle-resp)
       (with-stream [] (topbar-metadata-stream) handle-resp)

       @topbar-metadata)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn ->actions [item]
     (let [{:keys [
                   ;; workspace/title
                   ;; git/repo
                   ;; workspace/directory
                   ;; workspace/color
                   ;; workspace/title-hiccup
                   ;; workspace/scratchpad
                   ;; awesome.tag/index
                   ;; awesome.tag/clients
                   ;; awesome.tag/urgent
                   awesome.tag/selected
                   ]} item]
       (->>
         [(when selected
            {:action/label    "hide"
             :action/on-click #(hide-workspace item)})
          (when-not selected
            {:action/label    "show"
             :action/on-click #(show-workspace item)})]
         (remove nil?)))))

#?(:cljs
   (defn client->icon [client workspace]
     ;; TODO namespace client keys
     ;; TODO get filepaths for class == emacs clients
     ;; i.e. emacs clients could have types for clojure project, react/python project, org file, etc
     (let [{:awesome.client/keys [class name]} client
           {:workspace/keys [title]}           workspace]
       (cond
         (= "Emacs" class)
         (cond
           (= "journal" title)
           {:color "text-city-blue-400"
            :src   "/assets/candy-icons/todo.svg"}

           (= "garden" title)
           {:color "text-city-blue-400"
            :src   "/assets/candy-icons/cherrytree.svg"}

           :else
           {:color "text-city-blue-400"
            :src   "/assets/candy-icons/emacs.svg"})

         (= "Alacritty" class)
         {:color "text-city-green-600"
          :icon  [:img {:src "/assets/candy-icons/Alacritty.svg"}]
          ;; :icon  octicons/terminal16
          }

         (= "Spotify" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/spotify.svg"}

         (= "firefox" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/firefox.svg"}

         (= "firefoxdeveloperedition" class)
         {:color "text-city-green-600"
          :src   "/assets/candy-icons/firefox-nightly.svg"}

         (= "Google-chrome" class)
         {:color "text-city-green-600"
          :src   "/assets/candy-icons/google-chrome.svg"}

         (string/includes? name "Slack call")
         {:color "text-city-green-600"
          :src   "/assets/candy-icons/shutter.svg"}

         (= "Slack" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/slack.svg"}

         (= "Rofi" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/kmenuedit.svg"}

         (= "1Password" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/1password.svg"}

         (= "zoom" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/Zoom.svg"}

         (= "clover/doctor-dock" name)
         {:color "text-city-blue-600"
          :icon  mdi/doctor}

         (string/includes? name "Developer Tools")
         {:color "text-city-blue-600"
          :src   "/assets/candy-icons/firefox-developer-edition.svg"}

         (= "Godot" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/godot.svg"}

         (= "Aseprite" class)
         {:color "text-city-green-400"
          :src   "/assets/candy-icons/winds.svg"}

         :else
         (do
           (println "missing icon for client" client)
           {:icon octicons/question16})))))

#?(:cljs
   (defn client-icons
     ([clients] (client-icons {} clients))
     ([{:client/keys [client-hovered client-unhovered]
        :keys        [workspace]
        } clients]
      (when (seq clients)
        [:div
         {:class ["flex" "flex-row"]}
         (for [c (->> clients
                      ;; (remove (comp #(= "clover/doctor-topbar" %) :awesome.client/name))
                      )]
           (let [c-name                                  (->> c :awesome.client/name (take 15) (apply str))
                 {:awesome.client/keys [urgent focused]} c
                 {:keys [color icon src]}                (client->icon c workspace)]
             ^{:key (:window c)}
             [:div
              {
               :on-mouse-over #(do (client-hovered c))
               :on-mouse-out  #(do (client-unhovered c))
               :class         ["flex" "flex-row" "items-center"]}
              [:div
               {:class [(cond
                          focused "text-city-orange-400"
                          urgent  "text-city-red-400"
                          color   color
                          :else   "text-city-blue-400")

                        "text-xl"
                        "p-2"
                        "border"
                        "rounded"
                        (cond focused "border-city-orange-400")
                        (cond focused "border-opacity-70"
                              :else   "border-opacity-0")]}
               (cond src   [:img {:class ["w-8"]
                                  :src   src}]
                     icon  [:div {:class ["text-3xl"]} icon]
                     :else c-name)]]))]))))

#?(:cljs
   (defn workspace-comp
     ([wsp] (workspace-comp nil wsp))
     ([{:as             opts
        ;; :client/keys    [hovered-client]
        :workspace/keys [hovered-workspace
                         workspace-hovered
                         workspace-unhovered
                         ]} wsp]
      (let [{:keys [workspace/title
                    workspace/color
                    awesome.tag/index
                    workspace/scratchpad
                    awesome.tag/clients
                    awesome.tag/selected
                    awesome.tag/urgent
                    git/dirty?
                    git/needs-push?
                    git/needs-pull?
                    ]} wsp
            hovering?  (= @hovered-workspace wsp)
            ]
        [:div
         {:class
          ["m-1" "p-2"
           "border"
           "border-city-blue-600"
           "rounded"
           "border-opacity-50"
           "bg-yo-blue-800"
           (cond
             selected "bg-opacity-60"
             :else    "bg-opacity-10")
           "text-white"]
          :on-mouse-enter #(do (bring-topbar-above)
                               (workspace-hovered wsp))
          :on-mouse-leave #(do (push-topbar-below)
                               (workspace-unhovered wsp))}
         (let [show-name (or hovering? (not scratchpad) urgent selected (#{0} (count clients)))]
           [:div
            {:class ["flex" "flex-row"
                     "items-center"]}
            [client-icons (assoc opts :workspace wsp) clients]

            [:div
             {:class [
                      "font-nes" "text-md"
                      (when show-name "px-2")
                      (when show-name "pl-3")
                      (when-not show-name "w-0")
                      "transition-all"

                      (cond
                        urgent   "text-city-red-400"
                        selected "text-city-orange-400"
                        color    ""
                        :else    "text-yo-blue-300")]
              :style (when (and (not selected) (not urgent) color) {:color color})}

             [:div
              (when show-name title)

              (let [show (and show-name (or hovering? (#{0} (count clients)) (not scratchpad)))]
                [:span
                 {:class [(when show "pl-2")]}
                 (when show
                   (str "(" index ")"))])]
             (when (or needs-push? needs-pull? dirty?)
               [:div
                {:class ["text-xs" "pt-2" "flex-wrap" "flex-col"
                         (cond
                           (or urgent (not selected)) "text-city-red-300"
                           :else                      "text-yo-blue-300")]}
                [:div (when needs-push? "#needs-push")]
                [:div (when needs-pull? "#needs-pull")]
                [:div (when dirty? "#dirty")]])]])

         (when hovering?
           [:div
            (for [ax (->actions wsp)]
              ^{:key (:action/label ax)}
              [:div
               {:class    ["cursor-pointer"
                           "hover:text-yo-blue-300"]
                :on-click (:action/on-click ax)}
               (:action/label ax)])])]))))

#?(:cljs
   (defn pie-chart
     "TODO write this pie chart component"
     [{:keys [label value]}]
     [:div
      (str label " " value)]))

#?(:cljs
   (defn widget []
     (let [hovered-client         (uix/state nil)
           hovered-workspace      (uix/state nil)
           last-hovered-client    (uix/state nil)
           last-hovered-workspace (uix/state nil)
           {:keys [items]}        (use-workspaces)
           metadata               (use-topbar-metadata)

           time (uix/state (t/zoned-date-time))]
       (js/setTimeout
         #(reset! time (t/zoned-date-time))
         1000)
       [:div
        {:class ["pl-32"
                 "flex" "flex-row"
                 "justify-between"
                 "min-h-screen"
                 "max-h-screen"
                 "overflow-hidden"
                 "text-city-pink-200"
                 ]}

        ;; left side (workspaces)
        [:div
         {:class [
                  "flex" "flex-row"
                  "justify-center"
                  "overflow-hidden"]}
         (for [[i it] (->> items (map-indexed vector))]
           ^{:key i}
           [workspace-comp
            {:workspace/hovered-workspace   hovered-workspace
             :workspace/workspace-hovered   (fn [w]
                                              (reset! last-hovered-workspace w)
                                              (reset! hovered-workspace w))
             :workspace/workspace-unhovered (fn [_] (reset! hovered-workspace nil))
             :client/hovered-client         hovered-client
             :client/client-hovered         (fn [c]
                                              (reset! last-hovered-client c)
                                              (reset! hovered-client c))
             :client/client-unhovered       (fn [_] (reset! hovered-client nil))}
            it])]

        [:div
         ;; TODO seems a bit overactive...
         [:span
          (some->> @time (t.format/format (t.format/formatter "mm/dd HH:MM")))]

         "|"
         [:span
          (:hostname metadata)]]

        [:div
         [:span
          (:spotify/artist metadata)]
         ">"
         [:span
          (:spotify/song metadata)]]

        ;; right side
        [:div
         {:class ["m-1" "p-4"
                  "bg-yo-blue-500"
                  "border-city-blue-400"
                  "rounded"
                  "w-1/5"
                  "text-white"]}

         [:div
          {:class ["text-right"]}
          [:div
           (when metadata
             (->>
               metadata
               (remove (fn [[k v]]
                         (or
                           (#{:spotify/artist :spotify/song :hostname} k)
                           (when (and v (string? v))
                             (string/includes? v "%")))))
               (map (fn [[k v]] (str "[" k " " v "] ")))
               (apply str)))]
          [:div
           (when-let [pcts
                      (->>
                        metadata
                        (filter (fn [[_ v]] (when (and v (string? v)) (string/includes? v "%")))))]
             (for [[k v] pcts]
               ^{:key k}
               [pie-chart {:label k :value v}]
               )
             )]]]])))
