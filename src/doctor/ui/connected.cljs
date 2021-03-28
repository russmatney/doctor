(ns doctor.ui.connected)

(defonce connected? (atom false))

(defn reset [& args] (apply reset! connected? args))
