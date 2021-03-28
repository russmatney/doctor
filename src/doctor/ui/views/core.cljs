(ns doctor.ui.views.core)

(defn root []
  [:div
   {:class ["bg-yo-blue-500" "min-h-screen"]}
   [:div
    {:class ["font-nes" "text-lg" "p-4" "text-white"]}
    "Doctor Widgets"]

   ;; widget list
   (for [[i widg] (->> ["widg" "et"] (map-indexed vector))]
     ^{:key i}
     [:div widg])])
