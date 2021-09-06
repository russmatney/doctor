(ns doctor.server
  (:require
   [taoensso.timbre :as log]
   [systemic.core :as sys :refer [defsys]]
   [plasma.server :as plasma.server]
   [plasma.server.interceptors :as plasma.interceptors]
   [cognitect.transit :as transit]
   [ring.adapter.undertow :as undertow]
   [ring.adapter.undertow.websocket :as undertow.ws]

   [doctor.config :as config]
   [doctor.time-literals-transit :as tlt]
   [doctor.ui.views.todos :as todos]
   [doctor.api.workspaces :as d.workspaces]
   [doctor.api.topbar :as d.topbar]
   [doctor.ui.views.screenshots :as screenshots]
   [doctor.ui.views.wallpapers :as wallpapers]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Plasma config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defsys *sessions*
  "Plasma sessions"
  (atom {}))

(defsys *plasma-server*
  "Our plasma server bundle"
  (plasma.server/make-server
    {:session-atom *sessions*
     :send-fn      #(undertow.ws/send %2 %1)
     :on-error     #(log/warn (:error %) "Error in plasma handler" {:request %})
     :transit-read-handlers
     (merge transit/default-read-handlers
            tlt/read-handlers)
     :interceptors [(plasma.interceptors/auto-require
                      #(do (log/info "Auto requiring namespace" {:namespace %})
                           (systemic.core/start!)))
                    (plasma.interceptors/load-metadata)]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defsys *server*
  "Doctor webserver"
  :extra-deps
  [d.workspaces/*workspaces-stream*
   d.topbar/*topbar-metadata-stream*
   screenshots/*screenshots-stream*
   wallpapers/*wallpapers-stream*
   todos/*todos-stream*]
  :start
  (let [port (:server/port config/*config*)]
    (log/info "Starting *server* on port" port)
    (undertow/run-undertow
      (fn [{:keys [uri] :as _req}]
        (log/info "request" uri (System/currentTimeMillis))
        ;; poor man's router
        (cond
          (= uri "/topbar/update")
          (do
            (d.workspaces/update-workspaces)
            (d.topbar/update-topbar-metadata)
            {:status 200 :body "updated topbar"})

          (= uri "/dock/update")
          (do
            (d.workspaces/update-workspaces)
            (d.topbar/update-topbar-metadata)
            {:status 200 :body "updated topbar"})

          (= uri "/screenshots/update")
          (do
            (screenshots/update-screenshots)
            {:status 200 :body "updated screenshots"})

          (= uri "/todos/update")
          (do
            (todos/update-todos)
            {:status 200 :body "updated todos"})

          (= uri "/ws")
          {:undertow/websocket
           {:on-open    #(do (log/info "Client connected")
                             (plasma.server/on-connect! *plasma-server* %))
            :on-message #(plasma.server/on-message!
                           *plasma-server*
                           (:channel %)
                           (:data %))
            :on-close   #(plasma.server/on-disconnect!
                           *plasma-server*
                           (:ws-channel %))}}))
      {:port             port
       :session-manager? false
       :websocket?       true}))
  :stop
  (.stop *server*))

(comment
  @sys/*registry*
  (sys/stop!)
  (sys/start! `*server*)
  (sys/restart! `*server*)
  *server*

  (slurp "http://localhost:3334/dock/update")
  (println "hi"))
