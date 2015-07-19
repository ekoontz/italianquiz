(ns italianverbs.game
  (:require
   [clj-time.core :as t]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [hiccup.core :refer (html)]
   [italianverbs.authentication :as authentication]
   [italianverbs.config :refer [time-format]]
   [italianverbs.html :as html :refer [banner page rows2table]]
   [italianverbs.korma :as db]
   [italianverbs.user :refer [do-if-authenticated do-if-teacher username2userid]]
   [korma.core :as k]))

(declare headers)
(declare table)
(declare tr)
(declare show-students)
(def html-headers {"Content-Type" "text/html;charset=utf-8"})
(def json-headers {"Content-type" "application/json;charset=utf-8"})
(def resources {:onload "game_onload();"
                :css ["/css/game.css"]
                :jss ["/js/game/js"]})

(def routes
  (compojure/routes
   (GET "/" request
        (do-if-authenticated
         {:body
          (page "My Games"
                (let [user-id (username2userid (authentication/current request))]
                  [:div {:class "major"}

                   (do-if-teacher
                    [:div {:class "gamelist"}
                     [:h2 "Games for classes I'm teaching"]
                     (let [results (k/exec-raw
                                    ["SELECT *
                                        FROM game_in_class"
                                     []] :results)]
                       (rows2table results
                                   {}
                                   ))

                     [:h3 "Add a new game for a class"]

                     [:div 
                      "form goes here.."
                      
                      ]
                     

                     ])

                    [:div {:class "gamelist"}
                     [:h2 "Games I'm playing"]
                     (let [results (k/exec-raw
                                    ["SELECT *
                                        FROM student_in_game
                                       WHERE student=?"
                                     [user-id]] :results)]
                       (rows2table results
                                   {}
                                   ))
                     ]
                   
                   [:div {:class "gamelist"}
                    [:h2 "New Games Available"]

                     (let [results (k/exec-raw
                                    ["SELECT *
                                        FROM game_in_class"
                                     []] :results)]
                       (rows2table results
                                   {}))

                    ]
                   ])
                   
                request
                resources)}))

   (GET "/:game" request
        (do-if-authenticated
         {:body
          (let [game (Integer. (:game (:route-params request)))]
            (page "Game"
                  (let [userid (username2userid (authentication/current request))
                        result {}]
                    [:div#students {:game "major"}
                     [:h2 (banner [{:href "/game"
                                    :content "My Games"}
                                   {:href nil
                                    :content (:name result)}])]
                     [:div {:style "padding:1em"}
                      [:img {:width "50px" :src (:picture result)}]]])
                  request
                  resources))}))))
