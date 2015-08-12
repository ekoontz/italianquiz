(ns italianverbs.about
  (:use
   [hiccup core page])
  (:require
   [cemerick.friend :as friend]
   [compojure.core :refer [GET]]
   [environ.core :refer [env]]
   [italianverbs.menubar :refer [menubar]]
   [italianverbs.session :as session]
   [italianverbs.user :refer [do-if do-if-teacher haz-admin?
                              haz-auth? has-admin-role has-teacher-role
                              login-form
                              menubar-info-for-user
                              ]]
   [italianverbs.html :as html]))

(declare about)

(defn resources [request]
  {:show-login-form (login-form request)
   :menubar (menubar (menubar-info-for-user request))})

(def routes 
  (GET "/about" request
       {:status 200
        :headers {"Content-Type" "text/html;charset=utf-8"}
        :body (html/page "Welcome to Verbcoach"
                         (about request)
                         request
                         resources)}))
(defn about [request]
   [:div.major {:style "height: 500px"} [:h2 "Welcome to Verbcoach."]

;   [:p 
;    "You can use this website just by clicking on the language you want to ;practice, or you can login using your existing Google account (enter your google username and password)."
;    ]

    [:div.intro "The best place on the web to learn how to conjugate verbs."]
    [:div.flags
     [:div.flag
       [:img {:src "/png/Flag_of_Italy.svg.png" }]
       [:div.language "Italiano"]
      ]

     [:div.flag
       [:img {:src "/png/Flag_of_Mexico.svg.png" }]
       [:div.language "Español"]
      ]

     [:div.flag
       [:img {:src "/png/Flag_of_Spain.svg.png" }]
       [:div.language "Español"]
      ]
     

     [:div.flag
      [:img {:src "/png/Flag_of_France.svg.png" }]
      [:div.language "Français"]
      ]
     ]
    ])
