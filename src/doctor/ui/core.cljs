(ns doctor.ui.core
  (:require
   [uix.dom.alpha :as uix.dom]
   [doctor.ui.tubes :as tubes]
   [time-literals.data-readers]
   [time-literals.read-write]
   [tick.timezone]
   [tick.locale-en-us]
   [wing.uix.router :as router]
   [uix.core.alpha :as uix]

   [doctor.ui.views.dock :as views.dock]))

(defn root []
  [:div
   {:class ["bg-yo-blue-500" "min-h-screen"]}

   [views.dock/widget]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  [["/" {:name :page/home}]
   ["/dock" {:name :page/dock}]
   ["/dock-bg" {:name :page/dock-bg}]
   ["/counter" {:name :page/counter}]])

(defn home []
  (let [params (router/use-route-parameters)]
    [:div
     [:div {:class "flex"}
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
      :page/home    [home]
      :page/dock-bg [root]
      :page/dock    [views.dock/widget]
      :page/counter [counter]
      [home])))



(defn ^:dev/after-load mount-root []
  (time-literals.read-write/print-time-literals-cljs!)
  (uix.dom/render
    [wing.uix.router/router-provider
     {:routes routes}
     view]
    (.getElementById js/document "app")))


(defn dev-setup []
  (enable-console-print!))

(defn ^:export init
  []
  (tubes/setup)
  (dev-setup)
  (mount-root))
