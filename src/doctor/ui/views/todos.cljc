(ns doctor.ui.views.todos
  (:require
   [plasma.core :refer [defhandler defstream]]
   #?@(:clj [[systemic.core :refer [defsys] :as sys]
             [manifold.stream :as s]
             [org-crud.core :as org-crud]
             [ralphie.zsh :as r.zsh]
             [tick.alpha.api :as t]
             [babashka.fs :as fs]
             [clojure.string :as string]
             [clawe.db.core :as db]
             ]
       :cljs [[uix.core.alpha :as uix]
              [plasma.uix :refer [with-rpc with-stream]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DB todo crud
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn get-todo-db
     "Matches on just :org/name for now."
     [item]
     (some->>
       (db/query
         '[:find (pull ?e [*])
           :in $ ?name
           :where
           [?e :org/name ?name]]
         (:org/name item))
       ffirst)))

#?(:clj
   (defn upsert-todo-db [item]
     (let [existing (get-todo-db item)
           merged   (merge existing item)
           merged   (update merged :org/id #(or % (java.util.UUID/randomUUID)))]
       (db/transact [merged])))
   )

#?(:clj
   (comment
     (get-todo-db --i)
     (upsert-todo-db --i)

     (db/query
       '[:find (pull ?e [*])
         :in $ ?name
         :where
         [?e :org/name ?name]
         [?e :org/id ?id]]
       (:org/name --i))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; org helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn parse-created-at [x]
     x)
   )

#?(:clj
   (comment
     (parse-created-at "20210712:163730")
     (t/parse "20210712:163730" (t/format "yyyyMMdd:hhmmss"))))

#?(:clj
   (defn org-item->todo
     [{:org/keys      [name source-file status]
       :org.prop/keys [created-at]
       :as            item}]
     (->
       item
       (assoc :todo/name name
              :todo/file-name (str (-> source-file fs/parent fs/file-name) "/" (fs/file-name source-file))
              :todo/created-at (parse-created-at created-at)
              :todo/status status))))

#?(:clj
   (comment --i))

#?(:clj
   (defn org-file-paths []
     (concat
       (->>
         ["~/russmatney/{doctor,clawe,org-crud}/{readme,todo}.org"
          "~/todo/{journal,projects}.org"]
         (mapcat #(-> %
                      r.zsh/expand
                      (string/split #" ")))))))

#?(:clj
   (defn build-todos []
     (->> (org-file-paths)
          (map fs/file)
          (filter fs/exists?)
          (mapcat org-crud/path->flattened-items)
          (filter :org/status) ;; this is set for org items with a todo state
          (map org-item->todo)
          (map #(merge % (get-todo-db %))))))

#?(:clj
   (comment
     (->> (build-todos)
          (filter :todo/status)
          (group-by :todo/status)
          (map (fn [[s xs]]
                 [s (count xs)])))

     (some->>
       (db/query
         '[:find (pull ?e [*])
           :where
           [?e :todo/status :status/in-progress]]))))

#?(:clj
   (defn sorted-todos []
     (->> (build-todos)
          (sort-by :db/id)
          reverse
          (sort-by :todo/status)
          (sort-by (comp not #{:status/in-progress} :todo/status)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; get-todos
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn get-todos []
     (build-todos)))

#?(:clj
   (comment
     (->>
       (build-todos)
       (filter :db/id)
       (count))))

#?(:clj
   (defsys *todos-stream*
     :start (s/stream)
     :stop (s/close! *todos-stream*)))

#?(:clj
   (comment
     (sys/start! `*todos-stream*)))

#?(:clj
   (defn update-todos []
     (s/put! *todos-stream* (get-todos))))

(defhandler get-todos-handler []
  (get-todos))

(defstream todos-stream [] *todos-stream*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; actions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defhandler open-in-emacs [item]
  (println "open in emacs!!!")
  (println "opening file:" item)
  :ok
  )


(comment
  (open-in-emacs {}))

(defhandler add-to-db [item]
  (println "upserting-to-db" item)
  (upsert-todo-db item)
  (update-todos)
  :ok
  )

(defhandler mark-complete [item]
  (println "marking-complete" item)
  (-> item
      (assoc :todo/status :status/done)
      upsert-todo-db)
  (update-todos)
  :ok
  )

(defhandler mark-in-progress [item]
  (println "marking-in-progress" item)
  (def --i item)
  (-> item
      (assoc :todo/status :status/in-progress)
      (assoc :todo/status :status/in-progress)
      upsert-todo-db)
  (update-todos)
  :ok
  )

(comment
  (-> --i
      (assoc :todo/status :status/in-progress)
      upsert-todo-db
      )

  (get-todo-db --i)
  )

(defhandler mark-not-started [item]
  (println "marking-not-started" item)
  (-> item
      (assoc :todo/status :status/not-started)
      upsert-todo-db)
  (update-todos)
  :ok
  )

(defhandler mark-cancelled [item]
  (println "marking-cancelled" item)
  (-> item
      (assoc :todo/status :status/cancelled)
      upsert-todo-db)
  (update-todos)
  :ok)

#?(:cljs
   (defn ->actions [item]
     (let [{:keys []} item]
       (->>
         [{:action/label    "js/alert"
           :action/on-click #(js/alert item)}
          {:action/label    "open-in-emacs"
           :action/on-click #(open-in-emacs item)}
          {:action/label    "add-to-db"
           :action/on-click #(add-to-db item)}
          {:action/label    "mark-complete"
           :action/on-click #(mark-complete item)}
          {:action/label    "mark-in-progress"
           :action/on-click #(mark-in-progress item)}
          {:action/label    "mark-not-started"
           :action/on-click #(mark-not-started item)}
          {:action/label    "mark-cancelled"
           :action/on-click #(mark-cancelled item)}]
         (remove nil?)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontend
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defn use-todos []
     (let [items       (plasma.uix/state [])
           handle-resp (fn [its] (reset! items its))]

       (with-rpc [] (get-todos-handler) handle-resp)
       (with-stream [] (todos-stream) handle-resp)

       {:items @items})))

#?(:cljs
   (defn todo
     [{:keys [on-select is-selected?]} item]
     (let [{:db/keys   [id]
            :org/keys  [body urls]
            :todo/keys [status name created-at file-name]} item
           hovering?                                       (uix/state false)]
       [:div
        {:class          ["m-1" "p-4"
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
          (case status
            :status/done        [:div "done"]
            :status/not-started [:div "not started"]
            :status/in-progress [:div "in progress"]
            :status/cancelled   [:div "cancelled"]
            [:div "no status"])]

         (when-let [actions (->actions item)]
           [:div
            {:class ["flex" "flex-row" "flex-wrap"]}
            (for [[i ax] (map-indexed vector actions)]
              ^{:key i}
              [:div
               {:class    ["px-2" "mx-2"
                           "cursor-pointer"
                           "hover:text-city-blue-300"
                           "rounded"
                           "border"
                           "border-city-blue-700"
                           "hover:border-city-blue-300"]
                :on-click (fn [_] ((:action/on-click ax)))}
               (:action/label ax)])])]

        [:span
         {:class ["text-xl"]}
         name]

        (when created-at
          [:div
           {:class ["font-mono"]}
           created-at])

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
   (defn selected-node
     [{:org/keys  [body urls]
       :todo/keys [name file-name]}]

     [:div
      {:class ["flex" "flex-col" "p-2"]}
      [:span
       {:class ["font-nes" "text-xl" "text-city-green-200" "p-2"]}
       name]

      [:span
       {:class ["font-mono" "text-xl" "text-city-green-200" "p-2"]}
       file-name]

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
            url])])]))

#?(:cljs
   (defn split-counts [items]
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
             [:div.text-xl.font-nes (:label by)]
             [:div
              (for [[k v] (->> split (sort-by second))]
                ^{:key k}
                [:div
                 {:class    ["flex" "font-mono"
                             "cursor-pointer"
                             "hover:text-city-red-600"
                             ]
                  :on-click #(js/alert (str by " - " k " : " v))}
                 [:span.p-1.text-xl v]
                 [:span.p-1.text-xl (str k)]])]]))])))

#?(:cljs
   (defn widget []
     (let [{:keys [items]} (use-todos)
           selected        (uix/state (first items))

           item-groups (->> items
                            (group-by :todo/status)
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
         [split-counts items]]

        ;; TODO move 'selected' to 'current'?
        (when @selected
          (selected-node @selected))

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
            label]
           (for [[i it] (->> item-group (map-indexed vector))]
             ^{:key i}
             [todo
              {:on-select    (fn [_] (reset! selected it))
               :is-selected? (= @selected it)}
              (assoc it :index i)])])])))
