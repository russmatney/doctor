(ns doctor.api.todos
  (:require
   [systemic.core :refer [defsys] :as sys]
   [manifold.stream :as s]
   [org-crud.core :as org-crud]
   [ralphie.zsh :as r.zsh]
   [tick.alpha.api :as t]
   [babashka.fs :as fs]
   [clojure.string :as string]
   [clawe.db.core :as db]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DB todo crud
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
    ffirst))

(defn upsert-todo-db [item]
  (let [existing (get-todo-db item)
        merged   (merge existing item)
        merged   (update merged :org/id #(or % (java.util.UUID/randomUUID)))]
    (db/transact [merged])))


(comment
  (db/query
    '[:find (pull ?e [*])
      :in $ ?name
      :where
      [?e :org/name ?name]]
    (:org/name --i))

  (db/query
    '[:find (pull ?e [*])
      :where
      [?e :todo/status :status/in-progress]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; org helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-created-at [x]
  x)


(comment
  (parse-created-at "20210712:163730")
  (t/parse "20210712:163730" (t/format "yyyyMMdd:hhmmss")))

(defn org-item->todo
  [{:org/keys      [name source-file status]
    :org.prop/keys [created-at]
    :as            item}]
  (->
    item
    (assoc :todo/name name
           :todo/file-name (str (-> source-file fs/parent fs/file-name) "/" (fs/file-name source-file))
           :todo/created-at (parse-created-at created-at)
           :todo/status status)))

(comment --i)

(defn org-file-paths []
  (concat
    (->>
      ["~/russmatney/{doctor,clawe,org-crud}/{readme,todo}.org"
       "~/todo/{journal,projects}.org"]
      (mapcat #(-> %
                   r.zsh/expand
                   (string/split #" "))))))

(defn build-todos []
  (->> (org-file-paths)
       (map fs/file)
       (filter fs/exists?)
       (mapcat org-crud/path->flattened-items)
       (filter :org/status) ;; this is set for org items with a todo state
       (map org-item->todo)
       (map #(merge % (get-todo-db %)))))

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
        [?e :todo/status :status/in-progress]])))

(defn sorted-todos []
  (->> (build-todos)
       (sort-by :db/id)
       reverse
       (sort-by :todo/status)
       (sort-by (comp not #{:status/in-progress} :todo/status))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; get-todos
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-todos []
  (build-todos))

(comment
  (->>
    (build-todos)
    (filter :db/id)
    (count)))

(defsys *todos-stream*
  :start (s/stream)
  :stop (s/close! *todos-stream*))

(comment
  (sys/start! `*todos-stream*))

(defn update-todos []
  (s/put! *todos-stream* (get-todos)))
