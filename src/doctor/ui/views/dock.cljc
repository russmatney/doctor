(ns doctor.ui.views.dock
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             [clawe.workspaces :as clawe.workspaces]
             [clawe.scratchpad :as scratchpad]
             [ralphie.awesome :as awm]
             [ralphie.pulseaudio :as r.pulseaudio]
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
;; Active workspaces
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn active-workspaces []
     (->>
       (clawe.workspaces/all-workspaces)
       (filter :awesome.tag/name)
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
       (sort-by :awesome.tag/index)
       first)

     (update-dock)
     ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Misc metadata
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn build-dock-metadata []
     {:muted (r.pulseaudio/input-muted?)}))

(defhandler get-dock-metadata []
  (build-dock-metadata))

#?(:clj
   (defsys *dock-metadata-stream*
     :start (s/stream)
     :stop (s/close! *dock-metadata-stream*)))

#?(:clj
   (comment
     (sys/start! `*dock-metadata-stream*)))

(defstream dock-metadata-stream [] *dock-metadata-stream*)

#?(:clj
   (defn update-dock-metadata []
     (println "pushing to dock-metadata stream (updating dock-metadata)!")
     (s/put! *dock-metadata-stream* (build-dock-metadata))))

#?(:clj
   (comment
     (->>
       (build-dock-metadata)
       (sort-by :awesome.tag/index)
       first)

     (update-dock-metadata)
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dock behavior
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler bring-dock-above []
  (println "bring dock above")
  (awm/awm-fnl
    '(->
       (client.get)
       (lume.filter (fn [c] (= c.name "clover/doctor-dock")))
       (lume.first)
       ((fn [c]
          (tset c :above true)
          (tset c :below false))))))

(defhandler push-dock-below []
  (println "push dock below")
  (awm/awm-fnl
    '(->
       (client.get)
       (lume.filter (fn [c] (= c.name "clover/doctor-dock")))
       (lume.first)
       ((fn [c]
          (tset c :above false)
          (tset c :below true)
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend Data contexts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-workspaces []
     (let [workspaces  (plasma.uix/state [])
           handle-resp (fn [new-wsps]
                         (swap! workspaces
                                (fn [_wsps]
                                  (->>
                                    (concat
                                      ;; TODO work around this keeping the 'old' ones
                                      ;; (or wsps [])
                                      new-wsps)
                                    (w/distinct-by :workspace/title)
                                    (sort-by :awesome.tag/index)))))]

       (with-rpc [] (get-workspaces) handle-resp)
       (with-stream [] (workspaces-stream) handle-resp)

       {:items @workspaces})))

#?(:cljs
   (defn use-dock-metadata []
     (let [dock-metadata (plasma.uix/state [])
           handle-resp   #(reset! dock-metadata %)]

       (with-rpc [] (get-dock-metadata) handle-resp)
       (with-stream [] (dock-metadata-stream) handle-resp)

       @dock-metadata)))

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
   (defn client->icon [client]
     ;; TODO namespace client keys
     ;; TODO get filepaths for class == emacs clients
     ;; i.e. emacs clients could have types for clojure project, react/python project, org file, etc
     (let [{:awesome.client/keys [class name]} client]
       (cond
         (= "Emacs" class)
         {:color "text-city-blue-400"
          :src   "/assets/candy-icons/emacs.svg"}

         (= "Alacritty" class)
         {:color "text-city-green-600"
          ;; :icon  [:img {:src   "/assets/candy-icons/Alacritty.svg"}]
          :icon  octicons/terminal16}

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

         :else
         (do
           (println "missing icon for client" client)
           {:icon octicons/question16})))))

#?(:cljs
   (defn client-icons
     ([clients] (client-icons clients {}))
     ([clients {:client/keys [client-hovered client-unhovered]}]
      (when (seq clients)
        [:div
         {:class ["flex" "flex-row"]}
         (for [c (->> clients
                      ;; (remove (comp #(= "clover/doctor-dock" %) :awesome.client/name))
                      )]
           (let [c-name                                  (->> c :awesome.client/name (take 15) (apply str))
                 {:awesome.client/keys [urgent focused]} c
                 {:keys [color icon src]}                (client->icon c)]
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

                        "text-3xl"
                        "p-2"
                        "border"
                        "rounded"
                        (cond focused "border-city-orange-400")
                        (cond focused "border-opacity-70"
                              :else   "border-opacity-0")]}
               (cond src   [:img {:class ["w-16"]
                                  :src   src}]
                     icon  [:div {:class ["text-6xl"]} icon]
                     :else c-name)]]))]))))

#?(:cljs
   (defn workspace-comp
     ([wsp] (workspace-comp nil wsp))
     ([{:as             opts
        :client/keys    [hovered-client]
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
                    ]} wsp
            hovering?  (= @hovered-workspace wsp)
            ]
        [:div
         {:class
          ["m-1" "p-2" "mt-auto"
           "border"
           "border-city-blue-600"
           "rounded"
           "border-opacity-50"
           "bg-yo-blue-800"
           (cond
             selected "bg-opacity-60"
             :else    "bg-opacity-10")
           "text-white"]
          :on-mouse-enter #(do (bring-dock-above)
                               (workspace-hovered wsp))
          :on-mouse-leave #(do (push-dock-below)
                               (workspace-unhovered wsp))}
         (let [show-name (or hovering? (not scratchpad) urgent selected (#{0} (count clients)))]
           [:div
            {:class ["flex" "flex-row"
                     "items-center"]}
            [client-icons clients opts]

            [:div
             {:class ["font-nes" "text-lg"
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
             (when show-name title)

             (let [show (and show-name (or hovering? (#{0} (count clients)) (not scratchpad)))]
               [:span
                {:class [(when show "pl-2")]}
                (when show
                  (str "(" index ")"))])]])

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
   (defn widget []
     (let [hovered-client    (uix/state nil)
           hovered-workspace (uix/state nil)
           {:keys [items]}   (use-workspaces)
           metadata          (use-dock-metadata)]
       (println "metadata" metadata)
       [:div
        {:class ["flex" "flex-row"
                 "justify-between"
                 "min-h-screen"
                 "max-h-screen"
                 "overflow-hidden"]}

        ;; left side
        [:div
         {:class ["m-1" "p-4" "mt-auto"
                  "bg-yo-blue-500"
                  "border-city-blue-400"
                  "rounded"
                  "text-white"
                  "w-1/5"]}

         (when @hovered-workspace
           (let [{:keys [workspace/directory
                         git/repo]} @hovered-workspace
                 dir-path           (string/replace (or repo directory "") "/home/russ" "~")]
             ;; (->>
             ;;   @hovered-workspace
             ;;   (map (fn [[k v]] (str "[" k ": " v "] ")))
             ;;   (concat ["[dir-path: " dir-path "]"])
             ;;   (apply str))
             ))

         (when @hovered-client
           (->>
             @hovered-client
             (map (fn [[k v]] (str "[" k " " v "] ")))
             (apply str)))]

        ;; workspaces (middle)
        [:div
         {:class ["flex" "flex-row"
                  "justify-center"
                  "overflow-hidden"]}
         (for [[i it] (->> items (map-indexed vector))]
           ^{:key i}
           [workspace-comp
            {:workspace/hovered-workspace   hovered-workspace
             :workspace/workspace-hovered   (fn [w] (reset! hovered-workspace w))
             :workspace/workspace-unhovered (fn [_] (reset! hovered-workspace nil))
             :client/hovered-client         hovered-client
             :client/client-hovered         (fn [c] (reset! hovered-client c))
             :client/client-unhovered       (fn [_] (reset! hovered-client nil))}
            it])]

        ;; right side
        [:div
         {:class ["m-1" "p-4" "mt-auto"
                  "bg-yo-blue-500"
                  "border-city-blue-400"
                  "rounded"
                  "w-1/5"
                  "text-white"
                  ]}

         [:div
          {:class ["text-right"]}
          (when metadata
            (->>
              metadata
              (map (fn [[k v]] (str "[" k " " v "] ")))
              (apply str)))]
         ]])))
