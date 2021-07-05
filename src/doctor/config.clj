(ns doctor.config
  (:require
   [systemic.core :as sys :refer [defsys]]))

(def home "/home/russ")

(defsys *config*
  :start
  (->
    {:prod (System/getenv "PROD")
     :server-port
     (or (some-> (System/getenv "SERVER_PORT") int) 3334)

     :db-file                (str home "/russmatney/doctor/db/doctor-db-file")
     :db-temp-migration-file (str home "/russmatney/doctor/db/doctor-db-migration-file")
     :home-dir               (str home)}))
