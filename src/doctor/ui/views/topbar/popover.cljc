(ns doctor.ui.views.topbar.popover
  (:require
   #?@(:cljs [[doctor.ui.components.icons :as icons]
              [doctor.ui.components.debug :as debug]])))

(defn is-bar-app? [client]
  (and
    (-> client :awesome.client/name #{"clover/doctor-dock"
                                      "clover/doctor-topbar"})
    (-> client :awesome.client/focused not)))

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

             [debug/raw-metadata
              {:label "Raw Workspace Metadata"}
              (->>
                wsp
                ;; (remove (comp #{:awesome.client/name
                ;;                 :awesome.client/class
                ;;                 :awesome.client/instance} first))
                (sort-by first))]

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
