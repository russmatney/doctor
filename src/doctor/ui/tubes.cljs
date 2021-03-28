(ns doctor.ui.tubes
  (:require
   [pneumatic-tubes.core :as tubes]
   [plasma.client :as plasma]
   [doctor.time-literals-transit :as tlt]
   [doctor.ui.connected :as connected]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tubes setup
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn on-disconnect []
  (log/info "Connection with server lost")

  (connected/reset false)
  )

(defn on-connect-failed []
  (log/info "Connection with server failed")

  (connected/reset false)
  )

(defn on-connect []
  (log/info "Connection with server started")

  (connected/reset true)
  )

(defn on-receive [event-v]
  (log/info "Event received from server"
            {:event-first (first event-v)})

  (when (plasma/event? event-v)
    (plasma/receive! event-v)))

(goog-define SERVER_HOST "localhost")
(goog-define SERVER_PORT 7777)

(def ws-url
  (str
    "ws://"
    (or SERVER_HOST "localhost")
    ":"
    (or SERVER_PORT 7777)
    "/ws"))

(def tube
  (tubes/tube
    ws-url
    on-receive
    on-connect
    on-disconnect
    on-connect-failed
    {:write-handlers tlt/write-handlers
     :read-handlers  tlt/read-handlers}
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; fx handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn setup []
  (tubes/create! tube)

  (plasma/use-transport!
    (fn [& args]
      (tubes/dispatch tube (into [:plasma/message] args)))))

(defn teardown []
  (tubes/destroy! tube))

(defn dispatch
  "Potentially useful for arbitrary dispatch to tubes."
  [event-v]
  (log/info "Dispatching event to server"
            {:event-first (first event-v)})
  (tubes/dispatch tube event-v))
