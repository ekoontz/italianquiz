(ns italianverbs.about
  (:use
   [hiccup core page])
  (:require
   [cemerick.friend :as friend]
   [clojure.string :as string]
   [compojure.core :refer [GET]]
   [environ.core :refer [env]]
   [italianverbs.menubar :refer [menubar]]
   [italianverbs.session :as session]
   [italianverbs.user :refer [do-if do-if-teacher haz-admin?
                              haz-auth? has-admin-role has-teacher-role
                              login-form
                              menubar-info-for-user
                              ]]
   [italianverbs.html :as html]
   [korma.core :as k]))

(declare about)

(defn resources [request]
  {:css ["/css/about.css"]
   :menubar (menubar (menubar-info-for-user request))
   :show-login-form (login-form request)})

(def routes 
  (GET "/about" request
       {:status 200
        :headers {"Content-Type" "text/html;charset=utf-8"}
        :body (html/page "Welcome to Verbcoach"
                         (about request)
                         request
                         resources)}))
(defn about [request]
  (let [truncate-game-name 50]
    [:div.major {:style "height: 500px"} [:h2 "Welcome to Verbcoach."]
     [:div.intro "The best place on the web to learn how to conjugate verbs."]
     [:div.flags
      [:table
       [:tr
        [:th
         [:div.flag
          [:img {:src "/png/Flag_of_Italy.svg.png" }]]]

        [:th
         [:div.flag
          [:img {:src "/png/Flag_of_Mexico.svg.png" }]
          ]
         ]

        [:th
         [:div.flag
          [:img {:src "/png/Flag_of_Spain.svg.png" }]
          ]
         ]

        [:th
         [:div.flag
          [:img {:src "/png/Flag_of_France.svg.png" }]
          ]
         ]
        ]

       [:tr
        [:td "Italiano"]
        [:td "Español"]
        [:td "Español"]
        [:td "Français"]
        ]
    
       [:tr
        [:td "Firenze"]
        [:td "México D.F."]
        [:td "Barcelona"]
        [:td "Paris"]
        ]

       [:tr       
        [:td
         [:ul
          (let [results
                (k/exec-raw
                 ["SELECT id,name FROM game WHERE city=? LIMIT 5" ["Firenze"]] :results)]
            (map (fn [result]
                   (let [name (:name result)]
                     [:li
                     [:a {:title name
                          :href (str "/tour/" (:id result))}
                      (str
                       (subs name 0 (min truncate-game-name (.length name)))
                       (cond (> (.length name) truncate-game-name)
                             "..."
                             (= (.length name) 0)
                             "(unnamed game)"
                             true
                            ""))]]))
                 results))
          ]]
        
        [:td
         [:ul
          (let [results
                (k/exec-raw
                 ["SELECT id,name FROM game WHERE city=? LIMIT 5" ["México D.F."]] :results)]
            (map (fn [result]
                   (let [name (:name result)]
                     [:li
                      [:a {:title name
                           :href (str "/tour/" (:id result))}
                       (str
                       (subs name 0 (min truncate-game-name (.length name)))
                       (cond (> (.length name) truncate-game-name)
                             "..."
                             (= (.length name) 0)
                             "(unnamed game)"
                             true
                             ""))]]))
                 results))
          ]]
        

        [:td
         [:ul
          (let [results
                (k/exec-raw
                 ["SELECT id,name FROM game WHERE city=? LIMIT 5" ["Barcelona"]] :results)]
            (map (fn [result]
                   (let [name (:name result)]
                     [:li
                      [:a {:title name
                           :href (str "/tour/" (:id result))}
                       (str
                        (subs name 0 (min 20 (.length name)))
                        (cond (> (.length name) 20)
                              "..."
                              (= (.length name) 0)
                              "(unnamed game)"
                              true
                              ""))]]))
                 results))
          ]]
        
        [:td
         [:ul
          (let [results
                (k/exec-raw
                 ["SELECT id,name FROM game WHERE city=? LIMIT 5" ["Paris"]] :results)]
            (map (fn [result]
                   (let [name (:name result)]
                     [:li
                      [:a {:title name
                           :href (str "/tour/" (:id result))}
                       (str
                        (subs name 0 (min truncate-game-name (.length name)))
                        (cond (> (.length name) truncate-game-name)
                              "..."
                              (= (.length name) 0)
                              "(unnamed game)"
                              true
                              ""))]]))
                 results))
          ]]
        ]
       ]]]))

