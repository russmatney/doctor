(ns doctor.ui.views.topbar
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[clawe.workspaces :as clawe.workspaces]
             [clawe.scratchpad :as scratchpad]
             [ralphie.awesome :as awm]
             [doctor.api.topbar :as d.topbar]
             [doctor.api.workspaces :as d.workspaces]]
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
              [doctor.ui.components.charts :as charts]
              [doctor.ui.components.debug :as debug]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Workspaces
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler get-workspaces [] (d.workspaces/active-workspaces))
(defstream workspaces-stream [] d.workspaces/*workspaces-stream*)

#?(:cljs
   (defn is-bar-app? [client]
     (and
       (-> client :awesome.client/name #{"clover/doctor-dock"
                                         "clover/doctor-topbar"})
       (-> client :awesome.client/focused not))))

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

       {:workspaces        @workspaces
        :active-clients    (->> @workspaces
                                (filter :awesome.tag/selected)
                                (mapcat :awesome.tag/clients)
                                (remove is-bar-app?))
        :active-workspaces (->> @workspaces (filter :awesome.tag/selected))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Topbar metadata
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler get-topbar-metadata [] (d.topbar/build-topbar-metadata))
(defstream topbar-metadata-stream [] d.topbar/*topbar-metadata-stream*)

#?(:cljs
   (defn use-topbar-metadata []
     (let [topbar-metadata (plasma.uix/state [])
           handle-resp     #(reset! topbar-metadata %)]

       (with-rpc [] (get-topbar-metadata) handle-resp)
       (with-stream [] (topbar-metadata-stream) handle-resp)

       @topbar-metadata)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Workspace commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler hide-workspace [item]
  (->
    ;; TODO support non scratchpad workspaces - could be a quick awm-fnl show-only
    item
    ;; :name
    ;; clawe.workspaces/for-name
    clawe.workspaces/merge-awm-tags
    scratchpad/toggle-scratchpad)
  (d.workspaces/update-workspaces))

(defhandler show-workspace [item]
  (->
    item
    ;; :name
    ;; clawe.workspaces/for-name
    clawe.workspaces/merge-awm-tags
    scratchpad/toggle-scratchpad)
  (d.workspaces/update-workspaces))

(defhandler toggle-topbar-above [above?]
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
;; Actions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn ->actions
     ([wsp] (->actions nil wsp))
     ([{:keys [hovering?]} wsp]
      (let [{:keys [awesome.tag/selected
                    git/dirty?
                    git/needs-push?
                    git/needs-pull?]} wsp]
        (->>
          [(when needs-push? {:action/icon {:icon    mdi/github-face
                                            :color   "text-city-red-400"
                                            :tooltip "Needs Push"}})
           (when needs-pull? {:action/icon {:icon    mdi/github-face
                                            :color   "text-city-blue-500"
                                            :tooltip "Needs Pull"}})
           (when dirty? {:action/icon {:icon    mdi/github-face
                                       :color   "text-city-green-500"
                                       :tooltip "Dirty"}})
           (when (and selected hovering?)
             {:action/label    "hide"
              :action/on-click #(hide-workspace wsp)
              :action/icon     {:icon fa/eye-slash}})
           (when (and (not selected) hovering?)
             {:action/label    "show"
              :action/on-click #(show-workspace wsp)
              :action/icon     {:icon fa/eye}})]
          (remove nil?))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Icons
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
   (defn client-icon-list
     [{:keys [on-hover-client on-unhover-client workspace]} clients]
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
                            :classes ["border-opacity-0"
                                      (cond
                                        focused "text-city-orange-400"
                                        urgent  "text-city-red-400"
                                        color   color
                                        :else   "text-city-blue-400")]
                            :border? true))]))])))

(def cell-classes
  ["flex" "flex-row" "justify-center"
   "max-h-16" "px-2"
   "border" "border-city-blue-600" "rounded" "border-opacity-50"
   "bg-yo-blue-800"
   "bg-opacity-10"
   "text-white"])

#?(:cljs
   (defn clients-cell
     [topbar-state clients]
     (let [hovering? (uix/state false)]
       [:div
        {:class          cell-classes
         :on-mouse-enter #(reset! hovering? true)
         :on-mouse-leave #(reset! hovering? false)}
        [:div {:class ["flex" "flex-row" "items-center" "justify-center"]}
         ;; icons
         [client-icon-list topbar-state clients]]])))

#?(:cljs
   (defn workspace-cell
     [{:as   topbar-state
       :keys [hovered-workspace on-hover-workspace on-unhover-workspace]}
      {:as               wsp
       :workspace/keys   [title scratchpad]
       :awesome.tag/keys [index clients selected urgent]}]
     (let [hovering? (= hovered-workspace wsp)]
       [:div
        {:class          (conj cell-classes (cond selected "bg-opacity-60" :else "bg-opacity-10"))
         :on-mouse-enter #(on-hover-workspace wsp)
         :on-mouse-leave #(on-unhover-workspace wsp)}
        (let [show-name (or hovering? (not scratchpad) urgent selected (#{0} (count clients)))]
          [:div {:class ["flex" "flex-row" "items-center" "justify-center"]}
           ;; name/number
           [:div {:class [(when show-name "px-2")
                          (when-not show-name "w-0")
                          "transition-all"
                          (cond urgent   "text-city-red-400"
                                selected "text-city-orange-400"
                                :else    "text-yo-blue-300")]}
            [:div {:class ["font-nes" "text-lg"]}
             ;; number/index
             (let [show (and show-name (or hovering? (#{0} (count clients))))]
               [:span {:class [(when show "pr-2")]}
                (when show
                  (str "(" index ")"))])
             ;; name/title
             (when show-name title)]]

           ;; clients
           [client-icon-list (assoc topbar-state :workspace wsp) clients]

           ;; actions
           [:div
            {:class ["flex" "flex-wrap" "flex-row" "text-yo-blue-300"]}
            (for [[i ax] (map-indexed vector (->actions {:hovering? hovering?} wsp))]
              ^{:key i}
              [:div {:class    ["cursor-pointer" "hover:text-yo-blue-300"]
                     :on-click (:action/on-click ax)}
               (if (seq (:action/icon ax))
                 [bar-icon (:action/icon ax)]
                 (:action/label ax))])]])])))

#?(:cljs
   (defn workspace-list [topbar-state wspcs]
     [:div
      {:class ["flex" "flex-row" "justify-center"]}
      (for [[i it] (->> wspcs (map-indexed vector))]
        ^{:key i}
        [workspace-cell topbar-state it])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Clock/host/metadata
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs (defn sep [] [:span.px-2 "|"]))

#?(:cljs
   (defn clock-host-metadata [{:keys [time topbar-above toggle-above-below]} metadata]
     [:div
      {:class ["flex" "flex-row" "justify-center" "items-center"]}

      [:div
       (some->> time (t.format/format (t.format/formatter "MM/dd HH:mm")))]

      [sep]
      [:div
       {:class ["font-nes"]}
       (:hostname metadata)]

      [sep]
      [:div
       (if (:microphone/muted metadata) fa/microphone-slash-solid fa/microphone-solid)]

      [sep]
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

      [sep]
      ;; current todos
      (let [ct (-> metadata :todos/in-progress count)]
        [:div
         (if (zero? ct)
           "No in-progress todos"
           (str ct " in-progress todo(s)"))])
      [sep]
      [:div
       {:on-click toggle-above-below}
       (if topbar-above "above" "below")]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Detail window
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn client-metadata
     ([client] [client-metadata nil client])
     ([opts client]
      (let [{:keys [awesome.client/name
                    awesome.client/class
                    awesome.client/instance]} client]
        [:div
         {:class ["flex" "flex-col" "mb-6"]}

         [:div.mb-4
          {:class ["flex" "flex-row"]}
          [icons/icon-comp
           (assoc (icons/client->icon client nil)
                  :class ["w-8" "mr-4"])]

          [:span.text-xl
           (str name " | " class " | " instance)]]

         [debug/raw-metadata
          (merge {:label "Raw Client Metadata"} opts)
          (->>
            client
            (remove (comp #{:awesome.client/name
                            :awesome.client/class
                            :awesome.client/instance} first))
            (sort-by first))]]))))

#?(:cljs
   (defn detail-window [{:keys [active-workspaces hovered-workspace
                                hovered-client
                                push-below]} metadata]
     [:div
      {:class          ["m-6" "ml-auto" "p-6"
                        "bg-yo-blue-500"
                        "bg-opacity-80"
                        "border-city-blue-400"
                        "rounded"
                        "w-2/3"
                        "text-white"
                        "overflow-y-auto"
                        "h-5/6" ;; scroll requires parent to have a height
                        ]
       :on-mouse-leave push-below}

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
              {:class ["flex flex-row justify-between items-center"]}
              [:span.text-xl.font-nes title]

              [:span.ml-auto
               (str
                 (when needs-push? (str "#needs-push"))
                 (when needs-pull? (str "#needs-pull"))
                 (when dirty? (str "#dirty")))]]

             [:div
              {:class ["mb-4" "font-mono"]}
              dir]

             (when (seq clients)
               (for [client clients]
                 ^{:key (:awesome.client/window client)}
                 [client-metadata client]))])))

      (when hovered-client
        [:div
         [:div.text-xl "Hovered Client"]
         [client-metadata {:initial-show? true} hovered-client]])

      [debug/raw-metadata {:label "Raw Topbar Metadata"}
       (->> metadata (sort-by first))]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Topbar widget and state
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-topbar-state []
     (let [hovered-client         (uix/state nil)
           hovered-workspace      (uix/state nil)
           last-hovered-client    (uix/state nil)
           last-hovered-workspace (uix/state nil)
           topbar-above           (uix/state true)
           pull-above             (fn []
                                    (-> (toggle-topbar-above true)
                                        (.then (fn [v] (reset! topbar-above v)))))
           push-below             (fn []
                                    (-> (toggle-topbar-above false)
                                        (.then (fn [v] (reset! topbar-above v)))))
           toggle-above-below     (fn []
                                    (-> (toggle-topbar-above (not @topbar-above))
                                        (.then (fn [v] (reset! topbar-above v)))))
           time                   (uix/state (t/zoned-date-time))
           interval               (atom nil)]
       (uix/with-effect [@interval]
         (reset! interval (js/setInterval #(reset! time (t/zoned-date-time)) 1000))
         (fn [] (js/clearInterval @interval)))

       {:hovered-client         @hovered-client
        :hovered-workspace      @hovered-workspace
        :last-hovered-workspace @last-hovered-workspace
        :last-hovered-client    @last-hovered-client
        :on-hover-workspace     (fn [w]
                                  (reset! last-hovered-workspace w)
                                  (reset! hovered-workspace w)
                                  (pull-above))
        :on-unhover-workspace   (fn [_] (reset! hovered-workspace nil))
        :on-hover-client        (fn [c]
                                  (reset! last-hovered-client c)
                                  (reset! hovered-client c)
                                  (pull-above))
        :on-unhover-client      (fn [_] (reset! hovered-client nil))
        :topbar-above           @topbar-above
        :pull-above             pull-above
        :push-below             push-below
        :toggle-above-below     toggle-above-below
        :time                   @time})))

#?(:cljs
   (defn widget []
     (let [metadata                                              (use-topbar-metadata)
           {:keys [workspaces active-clients active-workspaces]} (use-workspaces)
           topbar-state                                          (use-topbar-state)]

       [:div
        {:class ["h-screen" "overflow-hidden" "text-city-pink-200"]}
        [:div
         {:class ["flex" "flex-row" "justify-between"]}

         ;; repo workspaces
         [workspace-list topbar-state (->> workspaces (remove :workspace/scratchpad))]
         ;; scratchpads
         [workspace-list topbar-state (->> workspaces (filter :workspace/scratchpad))]
         ;; active-clients
         (when (seq active-clients) [clients-cell topbar-state active-clients])
         ;; clock/host/metadata
         [clock-host-metadata topbar-state metadata]
         ;; current task
         (when (:todos/latest metadata)
           (let [{:todo/keys [name]} (:todos/latest metadata)]
             [:div {:class ["font-mono"]}
              "Current Task: "
              [:span name]]))]

        ;; below bar
        (when (:topbar-above topbar-state)
          [detail-window
           (assoc topbar-state :active-workspaces active-workspaces) metadata])])))
