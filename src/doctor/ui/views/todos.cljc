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
       (:org/name --i))
     )
   )

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
     (t/parse "20210712:163730" (t/format "yyyyMMdd:hhmmss"))
     )
   )

#?(:clj
   (defn org-item->todo
     [{:org/keys      [name source-file]
       :org.prop/keys [created-at]
       :as            item}]
     (->
       item
       (assoc :todo/name name
              :todo/source-file (string/replace-first source-file "/home/russ/todo/" "")
              :todo/created-at (parse-created-at created-at)))))

#?(:clj
   (comment --i))


#?(:clj
   (defn build-todos []
     (->
       "~/todo/{journal,projects}.org"
       r.zsh/expand
       (string/split #" ")
       (->>
         (map fs/file)
         (mapcat org-crud/path->flattened-items)
         (filter :org/status)
         (map org-item->todo)
         (map #(merge % (get-todo-db %)))
         (sort-by :db/id)
         reverse))))

#?(:clj
   (comment
     (->
       "~/todo/{journal,projects}.org"
       r.zsh/expand)
     )
   )

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
  (def --i item)
  (println "upserting-to-db" item)
  (upsert-todo-db item)
  (update-todos)
  :ok
  )

#?(:cljs
   (defn ->actions [item]
     (let [{:keys []} item]
       (->>
         [{:action/label    "js/alert"
           :action/on-click #(js/alert item)}
          {:action/label    "open-in-emacs"
           :action/on-click #(open-in-emacs item)}
          {:action/label    "add-to-db"
           :action/on-click #(add-to-db item)}]
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
            :todo/keys [name created-at source-file]} item
           hovering?                                  (uix/state false)]
       [:div
        {:class          ["m-1" "p-4"
                          "border" "border-city-blue-600"
                          "bg-yo-blue-700"
                          "text-white"
                          (when @hovering? "cursor-pointer")]
         :on-click       #(on-select)
         :on-mouse-enter #(reset! hovering? true)
         :on-mouse-leave #(reset! hovering? false)}

        name

        (when created-at
          [:div
           {:class ["font-mono"]}
           created-at])

        (when-let [actions (->actions item)]
          [:div
           (for [[i ax] (map-indexed vector actions)]
             ^{:key i}
             [:div
              {:class    ["cursor-pointer"
                          "hover:text-yo-blue-300"]
               :on-click (fn [_] ((:action/on-click ax)))}
              (:action/label ax)
              ])])

        [:div
         {:class ["font-mono"]}
         source-file]

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
              url]
             )]

          )
        ])))

#?(:cljs
   (defn selected-node
     [{:org/keys  [body urls]
       :todo/keys [name source-file]}]

     [:div
      {:class ["flex" "flex-col" "p-2"]}
      [:span
       {:class ["font-nes" "text-xl" "text-city-green-200" "p-2"]}
       name]

      [:span
       {:class ["font-mono" "text-xl" "text-city-green-200" "p-2"]}
       source-file]

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
   (defn widget []
     (let [{:keys [items]} (use-todos)
           selected        (uix/state (first items)) ]
       [:div
        {:class ["flex" "flex-col" "flex-wrap"
                 "overflow-hidden"
                 "min-h-screen"]}

        [:div
         {:class ["font-nes" "text-xl" "text-city-green-200" "p-4"]}
         (str (count items) " Todos, " (count (->> items (filter :db/id))) " in db")]

        (when @selected
          (selected-node @selected))

        [:div
         {:class ["flex" "flex-col" "flex-wrap" "justify-center"]}
         (for [[i it] (->> items (map-indexed vector))]
           ^{:key i}
           [todo
            {:on-select    (fn [_] (reset! selected it))
             :is-selected? (= @selected it)}
            (assoc it :index i)])]])))
