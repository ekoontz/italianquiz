(ns italianverbs.about
  (:use
   [hiccup core page])
  (:require
   [cemerick.friend :as friend]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET POST]]
   [environ.core :refer [env]]
   [italianverbs.authentication :as authentication]
   [italianverbs.menubar :refer [menubar]]
   [italianverbs.session :as session]
   [italianverbs.user :refer [do-if do-if-authenticated do-if-teacher haz-admin?
                              haz-auth? has-admin-role? has-teacher-role?
                              login-form
                              menubar-info-for-user
                              username2userid
                              ]]
   [italianverbs.html :as html]
   [korma.core :as k]))

(declare about)
(declare check-for-classes)
(declare check-for-teacher)

(defn resources [request]
  {:css ["/css/about.css"]
   :menubar (menubar (menubar-info-for-user request))
   :show-login-form (login-form request)})

(def routes 
  (compojure/routes
   (GET "/about" request
        (do-if-teacher
         {:status 302
          :headers {"Location" "/class"}}

         ;; otherwise, not a teacher:
         (do-if-authenticated
          (check-for-classes request)

          {:status 200
           :headers {"Content-type" "text/html;charset=utf-8"}
           :body (html/page "Welcome to Verbcoach"
                            (html [:div.major
                                   [:h2 "Welcome to Verbcoach."]
                                   [:h3 "Please login to continue."]])
                           request
                           resources)})))
        

         
   (POST "/about" request
         {:status 302
          :headers {"Location" "/about"}})))

(declare search-for-teacher)


(defn check-for-classes [request]
  ;; if student has classes already, go directly to those.
  (let [user-id (username2userid (authentication/current request))
        classes (:num_classes
                 (first
                  (k/exec-raw
                   ["SELECT count(*) AS num_classes
                     FROM student_in_class
                    WHERE (student=?)"
                    [user-id]] :results)))]
    (if (> classes 0)
      {:status 302
       :headers {"Location" "/class"}}
      {:status 200
       :headers {"Content-type" "text/html;charset=utf-8"}
       :body (html/page "Welcome to Verbcoach"
                        (check-for-teacher request)
                        request
                        resources)})))

(defn check-for-teacher [request]
  (let [search-term (:search (:params request))
        results (search-for-teacher search-term)]
    [:div.major
     [:h2 "Welcome to Verbcoach."]
     [:h3 "Please find your teacher."]
     [:div {:style "float:left"}
      [:div.search_form
       [:form {:method "get"}
        [:input {:name "search" :size "30" :value search-term}]
        [:button {:onclick "submit()"} "Search"]]]
      [:div.rows2table (html/rows2table
                        results
                        {:cols [:teacher :email]
                         :col-fns {:teacher
                                   (fn [teacher]
                                     [:a {:href (str "/teacher/" (:id teacher))}
                                      (:teacher teacher)])}})]]]))
(defn search-for-teacher [search-term]
  (if (= search-term "")
    []
    ;; The "? != ''" is important because it prevents an empty search from returning all results.
    (k/exec-raw
     ["SELECT teacher.given_name || ' ' || teacher.family_name AS teacher,
              teacher.id,teacher.email
         FROM vc_user AS teacher
   INNER JOIN vc_user_role
           ON (vc_user_role.user_id = teacher.id)
          AND (vc_user_role.role = 'teacher')
        WHERE (? != '')
          AND ((teacher.family_name ILIKE ?)
            OR (teacher.given_name ILIKE ?)
            OR (teacher.email ILIKE ?))
     ORDER BY teacher.family_name,teacher.given_name,teacher.email
"
      [search-term
       (str "%" search-term "%")
       (str "%" search-term "%")
       (str "%" search-term "%")]] :results)))

(declare under-a-city)

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
       ]
      ]

     [:div.search_form
      [:h4 "Search for a game or a teacher:"
       ]
      [:form {:method "get"}
       [:input {:name "search" :size "50" :value search-term}
        ]
       [:button {:onclick "submit()"} "Search"]

       ]
      ]
     ]
    )

  )

(defn under-a-city [city search-term]
  (let [truncate-game-name 50]
    [:td
     [:ul
      (let [results
            (if (= search-term "")
              []
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
                     OR  (teacher.family_name ILIKE ?)
                     OR  (teacher.email ILIKE ?)
                     OR  (? = ''))
                AND (game.active = true)
                AND (city=?)"
            [(str "%" search-term "%")
             (str "%" search-term "%")
             (str "%" search-term "%")
             (str "%" search-term "%")
             (str "%" search-term "%")
             search-term
             city]] :results))]
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

