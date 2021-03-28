(ns doctor.config
  (:require
   [systemic.core :as sys :refer [defsys]]
   [me.raynes.fs :as fs]))

(defsys *config*
  :start
  (->
    {:prod (System/getenv "PROD")
     :server-port
     (or (some-> (System/getenv "SERVER_PORT") int) 5555)

     :db-file                (str (fs/home) "/russmatney/doctor/db/doctor-db-file")
     :db-temp-migration-file (str (fs/home) "/russmatney/doctor/db/doctor-db-migration-file")
     :home-dir               (str (fs/home))}))
