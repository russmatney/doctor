(ns doctor.ui.views.todos
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[doctor.api.todos :as d.todos]]
       :cljs [[uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]
              [tick.alpha.api :as t]
              [hiccup-icons.fa :as fa]
              [doctor.ui.components.todos :as todos]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Todos data api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler get-todos-handler [] (d.todos/get-todos))
(defstream todos-stream [] d.todos/*todos-stream*)

#?(:cljs
   (defn use-todos []
     (let [items       (plasma.uix/state [])
           handle-resp (fn [its] (reset! items its))]

       (with-rpc [] (get-todos-handler) handle-resp)
       (with-stream [] (todos-stream) handle-resp)

       {:items @items})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn todo
     [{:keys [on-select is-selected?]} item]
     (let [{:db/keys   [id]
            :org/keys  [body urls]
            :todo/keys [status name file-name
                        last-started-at last-stopped-at last-cancelled-at last-complete-at
                        ]} item
           hovering?       (uix/state false)]
       [:div
        {:class          ["m-1" "py-2" "px-4"
                          "border" "border-city-blue-600"
                          "bg-yo-blue-700"
                          "text-white"
                          (when @hovering? "cursor-pointer")]
         :on-click       #(on-select)
         :on-mouse-enter #(reset! hovering? true)
         :on-mouse-leave #(reset! hovering? false)}

        [:div
         {:class ["flex" "justify-between"]}
         [:div
          {:class ["text-3xl"]}
          (case status
            :status/done        fa/check-circle
            :status/not-started fa/sticky-note
            :status/in-progress fa/pencil-alt-solid
            :status/cancelled   fa/ban-solid
            [:div "no status"])]

         [todos/action-list item]]

        [:span
         {:class ["text-xl"]}
         name]

        (when last-started-at
          [:div
           {:class ["font-mono"]}
           (t/instant (t/new-duration last-started-at :millis))])

        [:div
         {:class ["font-mono"]}
         file-name]

        (when id
          [:div
           {:class ["font-mono"]}
           (str "DB ID: " id)])

        (when (seq body)
          [:div
           {:class ["font-mono" "text-city-blue-400"
                    "flex" "flex-col" "p-2"
                    "bg-yo-blue-500"]}
           (for [[i line] (map-indexed vector body)]
             (let [{:keys [text]} line]
               (cond
                 (= "" text)
                 ^{:key i} [:span {:class ["py-1"]} " "]

                 :else
                 ^{:key i} [:span text])))])

        (when (seq urls)
          [:div
           {:class ["font-mono" "text-city-blue-400"
                    "flex" "flex-col" "pt-4" "p-2"]}
           (for [[i url] (map-indexed vector urls)]
             ^{:key i}
             [:a {:class ["py-1"
                          "cursor-pointer"
                          "hover:text-yo-blue-400"
                          ]
                  :href  url}
              url])])])))

#?(:cljs
   (defn split-counts [items {:keys [set-group-by]}]
     (let [bys [{:group-by :todo/file-name
                 :label    "Source File"}
                {:group-by :todo/status
                 :label    "Status"}
                {:group-by (comp boolean :db/id)
                 :label    "DB"}]]
       [:div.flex.flex-row.flex-wrap

        (for [[i by] (map-indexed vector bys)]
          (let [split (->> items
                           (group-by (:group-by by))
                           (map (fn [[v xs]] [v (count xs)])))]
            ^{:key i}
            [:div
             {:class [(when-not (zero? i) "px-8")]}
             [:div.text-xl.font-nes
              {:class    ["cursor-pointer"
                          "hover:text-city-red-600"]
               :on-click #(set-group-by (:group-by by))}
              (:label by)]
             [:div
              (for [[k v] (->> split (sort-by second))]
                ^{:key k}
                [:div
                 {:class    ["flex" "font-mono"
                             "cursor-pointer"
                             "hover:text-city-red-600"]
                  :on-click #(js/alert (str by " - " k " : " v))}
                 [:span.p-1.text-xl v]
                 [:span.p-1.text-xl (str k)]])]]))])))

#?(:cljs
   (defn widget []
     (let [{:keys [items]} (use-todos)
           selected        (uix/state (first items))

           items-by (uix/state :todo/file-name)

           item-groups (->> items
                            (group-by @items-by)
                            (map (fn [[status its]]
                                   {:item-group its
                                    :label      status})))]
       [:div
        {:class ["flex" "flex-col" "flex-wrap"
                 "overflow-hidden"
                 "min-h-screen"
                 "text-city-pink-200"]}

        [:div
         {:class ["p-4"]}
         [split-counts items {:set-group-by #(reset! items-by %)}]]

        ;; TODO move 'selected' to 'current'?
        ;; (when @selected
        ;;   (selected-node @selected))

        ;; TODO group/filter by file-name
        ;; TODO group/filter by status
        ;; TODO group/filter by tag
        ;; TODO group/filter by scheduled-date
        ;; TODO opt-in/out of files

        (for [[i {:keys [item-group label]}] (map-indexed vector item-groups)]
          ^{:key i}
          [:div
           {:class ["flex" "flex-col" "flex-wrap" "justify-center"]}
           [:div
            {:class ["text-2xl" "p-2" "pt-4"]}
            (str label " (" (count item-group) ")")]
           (for [[i it] (->> item-group (map-indexed vector))]
             ^{:key i}
             [todo
              {:on-select    (fn [_] (reset! selected it))
               :is-selected? (= @selected it)}
              (assoc it :index i)])])])))
