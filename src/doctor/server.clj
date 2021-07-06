(ns doctor.server
  (:require
   [org.httpkit.server :as server]
   [taoensso.timbre :as log]
   [pneumatic-tubes.core :as tubes]
   [pneumatic-tubes.httpkit :refer [websocket-handler]]
   [systemic.core :as sys :refer [defsys]]
   [compojure.route :as c.route]
   [compojure.core :as c]
   [plasma.core :as plasma]
   [plasma.server :as plasma.server]
   [plasma.server.middleware :as plasma.middleware]
   [doctor.config :as config]
   ;; [doctor.db.core :as db]
   [doctor.time-literals-transit :as tlt]
   [doctor.ui.views.dock :as dock]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tubes setup systems
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defsys *transmitter*
  "Pneumatic tube for transmitting data"
  (tubes/transmitter))

(defsys *tx*
  "Function to transmit data to the provided tube"
  (fn [tube event]
    (tubes/dispatch *transmitter* tube event)))

(defsys *dispatch-all*
  (fn [event]
    (tubes/dispatch *transmitter* :all event)))

(comment
  (sys/start!)
  (*dispatch-all* [:my-event]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; *rx* event handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def plasma-handle-fn
  (-> plasma.server/handle
      (plasma.server.middleware/auto-require
        (fn [_] (sys/start!)))))

(defsys *rx*
  "Receiver for handling web events"
  :start
  (tubes/receiver
    (->
      {:tube/on-create
       (fn [tube _ev]
         (assoc tube
                :plasma/state (atom nil)
                :plasma/resources (atom nil)))

       :tube/on-destroy
       (fn [tube _ev]
         (binding [plasma/*state*     (:plasma/state tube)
                   plasma/*resources* (:plasma/resources tube)]
           (plasma/cleanup-resources!)))

       :plasma/message
       (fn [tube [_ msg-type req-id event args]]
         (binding [plasma/*state*     (:plasma/state tube)
                   plasma/*resources* (:plasma/resources tube)]
           (let [send!    (bound-fn [resp] (*tx* tube resp))
                 on-error (fn [e _ _]
                            (log/error e "Error in plasma handler" {:event event}))]
             ((plasma.server/receive!
                plasma-handle-fn msg-type req-id event args)
              send!
              on-error))
           tube))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(c/defroutes all-routes
  (c/GET "/ws" [] (websocket-handler
                    *rx* {}
                    {:read-handlers  tlt/read-handlers
                     :write-handlers tlt/write-handlers}))
  (c/GET "/dock/update" []
         (do
           (println "hello /dock/update" (System/currentTimeMillis))
           (dock/update-dock)
           nil))
  (c.route/not-found "Not found"))

(defsys *server*
  :extra-deps
  [*dispatch-all*
   *rx*
   dock/*workspaces-stream*
   ;; db/*conn*
   ]
  :start
  (let [port (:server-port config/*config*)]
    (log/info "Starting *server* on port:" port)
    (server/run-server all-routes {:port port}))
  :stop (*server*))

(comment
  @sys/*registry*
  (sys/stop!)
  (sys/start! `*server*)
  (sys/start! `*rx*)

  (sys/restart! `*server*)

  (slurp "http://localhost:3334/dock/update")
  (println "hi")
  (slurp "http://localhost:3334/ws"))
