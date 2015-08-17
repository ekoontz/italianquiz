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

(defn under-a-city [city search-term]
  (let [truncate-game-name 50]
    [:td
     [:ul
      (let [results
            (k/exec-raw
           ["SELECT game.id,game.name,
                    teacher.given_name || ' ' || teacher.family_name AS teacher_name,
                    teacher.email AS teacher_email
               FROM game
         INNER JOIN vc_user AS teacher
                 ON game.created_by = teacher.id
              WHERE ((name ILIKE ?)
                     OR (city ILIKE ?)
                     OR  (teacher.given_name ILIKE ?)
                     OR  (teacher.email ILIKE ?)
                     OR  (? = ''))
                AND (game.active = true)
                AND (city=?)"
            [(str "%" search-term "%")
             (str "%" search-term "%")
             (str "%" search-term "%")
             (str "%" search-term "%")
             search-term
             city]] :results)]
        (map (fn [result]
               (let [name (str (:name result) " : " 
                               (if (:teacher_name result)
                                 (:teacher_name result)
                                 (:teacher_email result)))]
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
      ]]))

(defn about [request]
  (let [truncate-game-name 50
        search-term (:search (:params request))]
    [:div.major
     [:h2 "Welcome to Verbcoach."]
     [:div.intro "The best place on the web to learn how to conjugate verbs."]

     ;; TODO move away from tabular layout; use <div>s instead.
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
        [:th "Italiano - Firenze"]
        [:th "Español - México D.F."]
        [:th "Español - Barcelona"]
        [:th "Français - Paris"]
        ]

       [:tr       
        (under-a-city "Firenze" search-term)
        (under-a-city "México D.F." search-term)
        (under-a-city "Barcelona" search-term)
        (under-a-city "Paris" search-term)
        ]
       ]]]))

