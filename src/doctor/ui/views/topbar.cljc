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
             [clawe.db.core :as db]
             ]
       :cljs [[wing.core :as w]
              [clojure.string :as string]
              [uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]
              [hiccup-icons.octicons :as octicons]
              [hiccup-icons.fa :as fa]
              [hiccup-icons.fa4 :as fa4]
              [hiccup-icons.mdi :as mdi]
              [tick.alpha.api :as t]
              [tick.format :as t.format]
              [doctor.ui.components.icons :as icons]
              [doctor.ui.components.charts :as charts]])))

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
;; Get todos
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


#?(:clj
   (defn in-progress-todos []
     (some->>
       (db/query
         '[:find (pull ?e [*])
           :where
           [?e :todo/status :status/in-progress]])
       first)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Misc metadata
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


#?(:clj
   (defn build-topbar-metadata []
     (let [todos  (in-progress-todos)
           latest (some->> todos (sort-by :todo/last-started-at) reverse first)]
       (->
         {:microphone/muted (r.pulseaudio/input-muted?)
          :spotify/volume   (r.spotify/spotify-volume-label)
          :audio/volume     (r.pulseaudio/default-sink-volume-label)
          :hostname         (-> (process/$ hostname) process/check :out slurp string/trim)}
         (merge (r.spotify/spotify-current-song)
                (r.battery/info))
         (dissoc :spotify/album-url :spotify/album)
         (assoc :todos/in-progress todos)
         (assoc :todos/latest latest)))))

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
    ;; TODO support non scratchpad workspaces - could be a quick awm-fnl show-only
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

(defhandler toggle-topbar-above [above?]
  (println "setting topbar above" above?)
  (if above?
    ;; awm-fnl does not yet support passed arguments
    (awm/awm-fnl
      '(->
         (client.get)
         (lume.filter (fn [c] (= c.name "clover/doctor-topbar")))
         (lume.first)
         ((fn [c]
            (tset c :above true)
            (tset c :below false)
            (tset c :ontop true)))))
    (awm/awm-fnl
      '(->
         (client.get)
         (lume.filter (fn [c] (= c.name "clover/doctor-topbar")))
         (lume.first)
         ((fn [c]
            (tset c :above false)
            (tset c :below true)
            (tset c :ontop false))))))
  above?)

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
   (defn ->active-clients-actions []
     (let [] (->> [] (remove nil?)))))

#?(:cljs
   (defn ->actions [item]
     (let [{:keys [awesome.tag/selected]} item]
       (->>
         [(when selected
            {:action/label    "hide"
             :action/on-click #(hide-workspace item)
             :action/icon     {:icon fa/eye-slash}})
          (when-not selected
            {:action/label    "show"
             :action/on-click #(show-workspace item)
             :action/icon     {:icon fa/eye}})]
         (remove nil?)))))

#?(:cljs
   (defn bar-icon
     [{:keys [color icon src
              on-mouse-over
              on-mouse-out
              fallback-text
              classes
              border?]}]
     [:div
      {:on-mouse-over on-mouse-over
       :on-mouse-out  on-mouse-out
       :class         ["flex" "flex-row" "items-center"]}
      [:div
       {:class (concat (when border? ["border"]) [color] classes)}
       (cond
         src   [:img {:class ["w-10"] :src src}]
         icon  [:div {:class ["text-3xl"]} icon]
         :else fallback-text)]]))

#?(:cljs
   (defn is-bar-app? [client]
     (and
       (-> client :awesome.client/name #{"clover/doctor-dock"
                                         "clover/doctor-topbar"})
       (-> client :awesome.client/focused not))))

#?(:cljs
   (defn client-icons
     [{:keys [on-hover-client on-unhover-client workspace]
       :as   _opts}
      clients]
     (when (seq clients)
       [:div
        {:class ["flex" "flex-row" "flex-wrap"]}
        (for [c (->> clients (remove is-bar-app?))]
          (let [c-name                                         (->> c :awesome.client/name (take 15) (apply str))
                {:awesome.client/keys [window urgent focused]} c
                {:keys [color] :as icon-def}                   (icons/client->icon c workspace)]
            ^{:key (or window c-name)}
            [bar-icon (-> icon-def
                          (assoc
                            :on-mouse-over #(on-hover-client c)
                            :on-mouse-out  #(on-unhover-client c)
                            :fallback-text c-name
                            :color color
                            :classes [
                                      ;; (cond focused "bg-city-orange-400")
                                      ;; (cond focused "bg-opacity-10")
                                      (cond #_focused #_ "border-opacity-20"
                                            :else     "border-opacity-0")
                                      (cond
                                        focused "text-city-orange-400"
                                        urgent  "text-city-red-400"
                                        color   color
                                        :else   "text-city-blue-400")]
                            :border? true))]))])))

#?(:cljs
   (defn git-icons
     [{:keys [git/dirty?
              git/needs-push?
              git/needs-pull?]}]

     (when (or needs-push? needs-pull? dirty?)
       [:div
        {:class ["flex" "flex-wrap" "flex-row"
                 "text-yo-blue-300"]}
        (when needs-push?
          [bar-icon
           {:icon    mdi/doctor
            :color   "text-city-red-400"
            :tooltip "Needs Push"}])
        (when needs-pull?
          [bar-icon
           {:icon    mdi/doctor
            :color   "text-city-blue-500"
            :tooltip "Needs Pull"}])
        (when dirty?
          [bar-icon
           {:icon    mdi/doctor
            :color   "text-city-green-500"
            :tooltip "Dirty"}])])))

#?(:cljs
   (defn client-list [opts clients]
     (let [hovering? (uix/state false)]
       [:div
        {:class
         ["flex" "flex-row" "justify-center"
          "max-h-16"
          "px-2"
          "border" "border-city-blue-600" "rounded"
          "border-opacity-50"
          "bg-yo-blue-800"
          "bg-opacity-10"
          "text-white"]
         :on-mouse-enter #(reset! hovering? true)
         :on-mouse-leave #(reset! hovering? false)}
        [:div
         {:class ["flex" "flex-row" "items-center" "justify-center"]}

         ;; icons
         [client-icons opts clients]

         (when hovering?
           [:div
            {:class ["flex" "flex-wrap" "flex-row"
                     "text-yo-blue-300"]}
            (for [ax (->active-clients-actions)]
              (do
                (println "ax" ax)
                ^{:key (:action/label ax)}
                [:div
                 {:class    ["cursor-pointer"
                             "hover:text-yo-blue-300"]
                  :on-click (:action/on-click ax)}
                 (if (seq (:action/icon ax))
                   [bar-icon (:action/icon ax)]
                   (:action/label ax))]))])]])))

#?(:cljs
   (defn workspace-comp
     [{:as   opts
       :keys [hovered-workspace
              on-hover-workspace
              on-unhover-workspace]}
      {:keys [workspace/title
              workspace/color
              awesome.tag/index
              workspace/scratchpad
              awesome.tag/clients
              awesome.tag/selected
              awesome.tag/urgent]
       :as   wsp}]
     (let [hovering? (= hovered-workspace wsp)]
       [:div
        {:class
         ["flex" "flex-row" "justify-center"
          "max-h-16"
          "px-2"
          "border" "border-city-blue-600" "rounded"
          "border-opacity-50"
          "bg-yo-blue-800"
          (cond selected "bg-opacity-60"
                :else    "bg-opacity-10")
          "text-white"]
         :on-mouse-enter #(on-hover-workspace wsp)
         :on-mouse-leave #(on-unhover-workspace wsp)}
        (let [show-name (or hovering? (not scratchpad) urgent selected (#{0} (count clients)))]
          [:div
           {:class ["flex" "flex-row" "items-center" "justify-center"]}

           ;; name/number
           [:div
            {:class [(when show-name "px-2")
                     (when-not show-name "w-0")
                     "transition-all"

                     (cond
                       urgent   "text-city-red-400"
                       selected "text-city-orange-400"
                       :else    "text-yo-blue-300")]
             :style (when (and (not selected) (not urgent) color) {:color color})}

            [:div
             {:class ["font-nes" "text-xs"]}
             (let [show (and show-name
                             (or hovering?
                                 (#{0} (count clients))
                                 (not scratchpad)))]
               [:span
                {:class [(when show "pr-2")]}
                (when show
                  (str "(" index ")"))])

             [:span
              (when show-name title)]]]

           ;; icons
           [client-icons (assoc opts :workspace wsp) clients]
           [git-icons wsp]

           (when hovering?
             [:div
              {:class ["flex" "flex-wrap" "flex-row"
                       "text-yo-blue-300"]}
              (for [ax (->actions wsp)]
                ^{:key (:action/label ax)}
                [:div
                 {:class    ["cursor-pointer"
                             "hover:text-yo-blue-300"]
                  :on-click (:action/on-click ax)}
                 (if (seq (:action/icon ax))
                   [bar-icon (:action/icon ax)]
                   (:action/label ax))])])])])))

#?(:cljs
   (defn workspace-list [opts wspcs]
     [:div
      {:class ["flex" "flex-row"
               "justify-center"
               "overflow-hidden"]}
      (for [[i it] (->> wspcs (map-indexed vector))]
        ^{:key i}
        [workspace-comp opts it])]))

#?(:cljs
   (defn detail-window [{:keys [active-workspaces hovered-workspace]} metadata]
     [:div
      {:class ["m-6" "ml-auto" "p-6"
               "bg-yo-blue-500"
               "border-city-blue-400"
               "rounded"
               "w-1/2"
               "text-white"]}

      (when (or (seq active-workspaces) hovered-workspace)
        (for [wsp (if hovered-workspace [hovered-workspace] active-workspaces)]
          (let [{:keys [workspace/directory
                        git/repo
                        git/needs-push?
                        git/dirty?
                        git/needs-pull?
                        workspace/title
                        awesome.tag/clients]} wsp

                dir     (or directory repo)
                clients (->> clients (remove is-bar-app?))]

            ^{:key title}
            [:div
             {:class ["text-left"]}
             [:div
              {:class ["flex flex-row justify-between"]}
              [:span.text-xl "Workspace: " title]

              [:span.ml-auto
               (str
                 (when needs-push? (str "#needs-push"))
                 (when needs-pull? (str "#needs-pull"))
                 (when dirty? (str "#dirty")))]]

             [:div
              {:class ["mb-4"]}
              dir]

             (when (seq clients)
               (for [client clients]
                 (let [{:keys [awesome.client/name
                               awesome.client/class
                               awesome.client/instance
                               awesome.client/window]} client]
                   ^{:key window}
                   [:div
                    {:class ["text-left" "flex" "flex-col" "mb-6"]}

                    [:span.text-xl (str name " | " class " | " instance)]

                    (->>
                      client
                      (remove (comp #{:awesome.client/name
                                      :awesome.client/class
                                      :awesome.client/instance} first))
                      (map (fn [[k v]] (str "[" k " " v "] ")))
                      (apply str))])))])))


      [:div
       {:class ["mt-auto"]}
       (when metadata
         (->>
           metadata
           (remove (fn [[k v]]
                     (or
                       (#{:spotify/artist :spotify/song :hostname} k)
                       (when (and v (string? v))
                         (string/includes? v "%")))))
           (map (fn [[k v]] (str "[" k " " v "] ")))
           (apply str)))]]))

#?(:cljs
   (defn widget []
     (let [metadata          (use-topbar-metadata)
           {:keys [items]}   (use-workspaces)
           active-clients    (->> items
                                  (filter :awesome.tag/selected)
                                  (mapcat :awesome.tag/clients)
                                  (remove is-bar-app?))
           active-workspaces (->> items (filter :awesome.tag/selected))

           hovered-client         (uix/state nil)
           hovered-workspace      (uix/state nil)
           last-hovered-client    (uix/state nil)
           last-hovered-workspace (uix/state nil)
           topbar-above           (uix/state true)

           opts {:hovered-client         @hovered-client
                 :hovered-workspace      @hovered-workspace
                 :last-hovered-workspace @last-hovered-workspace
                 :last-hovered-client    @last-hovered-client
                 :on-hover-workspace     (fn [w]
                                           (reset! last-hovered-workspace w)
                                           (reset! hovered-workspace w))
                 :on-unhover-workspace   (fn [_]
                                           (reset! hovered-workspace nil))
                 :on-hover-client        (fn [c]
                                           (reset! last-hovered-client c)
                                           (reset! hovered-client c))
                 :on-unhover-client      (fn [_]
                                           (reset! hovered-client nil))}

           time (uix/state (t/zoned-date-time))]
       (println "last-hovered-workspace" last-hovered-workspace)
       ;; TODO kill/reuse to prevent loading up too many timers
       ;; (js/setTimeout
       ;;   #(reset! time (t/zoned-date-time))
       ;;   100000)

       [:div
        {:class ["min-h-screen"
                 "max-h-screen"
                 "overflow-hidden"
                 "text-city-pink-200"]}
        [:div
         {:class ["flex" "flex-row" "justify-between"]}
         ;; above/below toggle bar
         [:div
          {:class         ["absolute" "top-0" "left-0" "right-0" "bg-black"
                           (if @topbar-above "opacity-10" "opacity-0")]
           :on-mouse-over (fn [e]
                            ;; don't fire when at the very top, only on the way there
                            (when (> e.pageY 7)
                              (-> (toggle-topbar-above (not @topbar-above))
                                  (.then (fn [v] (reset! topbar-above v))))))
           :style         {:z-index 0}}
          [:span {:class "opacity-0"} "|"]]

         ;; left side (workspaces)
         [workspace-list opts (->> items (remove :workspace/scratchpad))]
         [workspace-list opts (->> items (filter :workspace/scratchpad))]
         (when (seq active-clients)
           [client-list opts active-clients])

         ;; clock/host/metadata
         [:div
          {:class ["flex" "flex-row" "justify-center" "items-center"]}
          [:div
           (some->> @time (t.format/format (t.format/formatter "MM/dd HH:mm")))]

          "|"
          [:div
           {:class ["font-nes"]}
           (:hostname metadata)]

          "|"
          [:div
           (if (:microphone/muted metadata) fa/microphone-slash-solid fa/microphone-solid)]

          "|"
          [:div
           {:class ["flex" "flex-row"]}
           (when-let [pcts
                      (->>
                        metadata
                        (filter (fn [[_ v]] (when (and v (string? v)) (string/includes? v "%")))))]
             (for [[k v] pcts]
               ^{:key k}
               [:div
                {:class ["w-10"]}
                [charts/pie-chart
                 {:label (str k)
                  :value v
                  :color (case k
                           :spotify/volume "rgb(255, 205, 86)"
                           :audio/volume   "rgb(54, 162, 235)",
                           "rgb(255, 99, 132)")}]]))]

          "|"
          ;; current todos
          (let [ct (-> metadata :todos/in-progress count)]
            [:div
             (if (zero? ct)
               "No in-progress todos"
               (str ct " in-progress todo(s)"))])]

         ;; TODO call update-topbar-metadata when todos get updated?
         ;; for now it gets called by various things already
         (when (:todos/latest metadata)
           (let [{:todo/keys [name]} (:todos/latest metadata)]
             [:div {:class ["font-mono"]}
              "Current Task: "
              [:span name]]))]

        ;; below bar
        [detail-window
         (assoc opts :active-workspaces active-workspaces) metadata]])))
