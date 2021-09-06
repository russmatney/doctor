(ns doctor.ui.components.icons
  (:require
   [clojure.string :as string]
   [hiccup-icons.octicons :as octicons]
   [hiccup-icons.fa :as fa]
   [hiccup-icons.fa4 :as fa4]
   [hiccup-icons.mdi :as mdi]))

(defn client->icon [client workspace]
  (let [{:awesome.client/keys [class name]} client
        {:workspace/keys [title]}           workspace]
    (cond
      (= "Emacs" class)
      (cond
        (= "journal" title)
        {:color "text-city-blue-400"
         :src   "/assets/candy-icons/todo.svg"}

        (= "garden" title)
        {:color "text-city-blue-400"
         :src   "/assets/candy-icons/cherrytree.svg"}

        :else
        {:color "text-city-blue-400"
         :src   "/assets/candy-icons/emacs.svg"})

      (= "Alacritty" class)
      {:color "text-city-green-600"
       :src   "/assets/candy-icons/Alacritty.svg"}

      (= "Spotify" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/spotify.svg"}

      (= "firefox" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/firefox.svg"}

      (= "firefoxdeveloperedition" class)
      {:color "text-city-green-600"
       :src   "/assets/candy-icons/firefox-nightly.svg"}

      (= "Google-chrome" class)
      {:color "text-city-green-600"
       :src   "/assets/candy-icons/google-chrome.svg"}

      (string/includes? name "Slack call")
      {:color "text-city-green-600"
       :src   "/assets/candy-icons/shutter.svg"}

      (= "Slack" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/slack.svg"}

      (= "Rofi" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/kmenuedit.svg"}

      (= "1Password" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/1password.svg"}

      (= "zoom" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/Zoom.svg"}

      (#{"clover/doctor-dock" "clover/doctor-topbar"} name)
      {:color "text-city-blue-600"
       :icon  mdi/doctor}

      (string/includes? name "Developer Tools")
      {:color "text-city-blue-600"
       :src   "/assets/candy-icons/firefox-developer-edition.svg"}

      (= "Godot" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/godot.svg"}

      (= "Aseprite" class)
      {:color "text-city-green-400"
       :src   "/assets/candy-icons/winds.svg"}

      :else
      (do
        (println "missing icon for client" client)
        {:icon octicons/question16}))))

(defn icon-comp [{:keys [class src icon text]}]
  (cond
    src   [:img {:class class :src src}]
    icon  [:div {:class class} icon]
    :else text))
