(ns doctor.ui.core
  (:require
   [plasma.client]
   [doctor.time-literals-transit :as tlt]
   [doctor.ui.connected :as connected]
   [taoensso.timbre :as log]
   [uix.dom.alpha :as uix.dom]
   [time-literals.data-readers]
   [time-literals.read-write]
   [tick.timezone]
   [tick.locale-en-us]
   [wing.uix.router :as router]
   [uix.core.alpha :as uix]

   [doctor.ui.views.dock :as views.dock]
   [doctor.ui.views.screenshots :as views.screenshots]))

(defn root []
  [:div
   {:class ["bg-yo-blue-500" "min-h-screen"]}

   [views.dock/widget]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; routes, home
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  [["/" {:name :page/home}]
   ["/dock" {:name :page/dock}]
   ["/dock-bg" {:name :page/dock-bg}]
   ["/counter" {:name :page/counter}]
   ["/screenshots" {:name :page/screenshots}]])

(defn home []
  (let [params (router/use-route-parameters)]
    [:div
     [:div {:class ["flex" "flex-col"]}
      [:a {:href (router/href :page/chess)} "Chess"]
      [:a {:href (router/href :page/dock)} "Dock"]
      [:a {:href (router/href :page/counter)} "Counter"]]
     [:p (str "Router:" (clj->js (uix/context router/*router*)))]
     [:p (str "Match: "  @params)]]))

(defn counter []
  (let [page-name (-> router/*match* uix/context :data :name)
        count     (router/use-route-parameters [:query :count])]
    [:div
     (when (= :page/counter page-name)
       [:button {:on-click #(swap! count inc)} @count])]))

(defn view
  []
  (let [page-name (-> router/*match* uix/context :data :name)]
    (case page-name
      :page/home        [home]
      :page/dock-bg     [root]
      :page/dock        [views.dock/widget]
      :page/counter     [counter]
      :page/screenshots [views.screenshots/widget]
      [home])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Websocket events
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn on-close []
  (log/info "Connection with server closed")
  (connected/reset false))

(defn on-error []
  (log/info "Connection with server error")
  (connected/reset false))

(defn on-open []
  (log/info "Connection with server open")
  (connected/reset true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bootstrap
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:dev/after-load mount-root []
  (time-literals.read-write/print-time-literals-cljs!)
  (uix.dom/render
    [wing.uix.router/router-provider
     {:routes routes}
     view]
    (.getElementById js/document "app")))

(defn dev-setup []
  (enable-console-print!))

(goog-define SERVER_HOST "localhost")
(goog-define SERVER_PORT 3334)

(def ws-url (str "ws://" SERVER_HOST ":" SERVER_PORT "/ws"))

(defn ^:export init
  []
  (plasma.client/use-transport!
    (plasma.client/websocket-transport
      ws-url
      {:on-open                on-open
       :on-close               on-close
       :on-error               on-error
       :transit-write-handlers tlt/write-handlers
       :transit-read-handlers  tlt/read-handlers}))
  (dev-setup)
  (mount-root))
