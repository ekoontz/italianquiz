(ns italianverbs.admin
  (:require
   [italianverbs.config :refer [short-language-name-to-long time-format]]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.html :refer [page rows2table]]
   [italianverbs.menubar :refer [menubar]]
   [italianverbs.user :refer [do-if-admin login-form menubar-info-for-user]]
   [korma.core :as k]))

(declare admin)

(defn resources [request]
  {:onload "admin_onload();"
   :css ["/css/admin.css"]
   :jss ["/js/admin.js"]
   :show-login-form (login-form request)
   :menubar (menubar (menubar-info-for-user request))})

(def routes
  (let [headers {"Content-Type" "text/html;charset=utf-8"}]
    (compojure/routes

     (GET "/" request
          (do-if-admin
           {:headers headers
            :status 200
            :body
            (page "Admin page"
                  (admin)
                  request
                  resources)})))))

(defn admin []
  [:div#admin {:class "major"}
   [:h2 "Admin"]

   [:div.onecolumn
    [:h3 "Classes"]
    (rows2table
     (k/exec-raw
      ["SELECT class.id,class.name AS class, 
           teacher.given_name || ' ' || teacher.family_name AS teacher,
           teacher.email,class.language,to_char(class.created,?) AS created,game_counts.count AS games
          FROM class
    INNER JOIN vc_user AS teacher ON (teacher.id = class.teacher)
    INNER JOIN (SELECT class.id AS class,count(game_in_class.game) 
                  FROM class 
            INNER JOIN game_in_class 
               ON (class.id = game_in_class.class) 
            GROUP BY class.id) AS game_counts 
            ON (game_counts.class = class.id)
      ORDER BY class.created DESC;
"
       [time-format]] :results)

     {:cols [:class :teacher :email :language :games :created]
      :col-fns {:language (fn [row]
                            (short-language-name-to-long (:language row)))
                :class (fn [row]
                          [:a {:href (str "/class/" (:id row))}
                           (:class row)])}
      :td-styles {:games "text-align:right;"}
      :th-styles {:games "text-align:right;"}
      :language (fn [row]
                  (short-language-name-to-long (:language row)))}
     )
    ]
   
   [:div.onecolumn
    [:h3 "Games"]
    (rows2table
     (k/exec-raw
      ["SELECT game.name AS game,game.id AS id,
               creator.email AS created_by,game.target AS language,
               city,active,to_char(game.created_on,?) AS created_on
          FROM game 
     LEFT JOIN vc_user AS creator 
            ON (creator.id = game.created_by)
      ORDER BY game.created_on DESC;
" [time-format]] :results)
     {:cols [:game :created_by :language :city :created_on :active]
      :td-styles {:created_on "white-space:nowrap;"}
      :col-fns {:game (fn [row]
                        [:a {:href (str "/game/" (:id row))}
                         (:game row)])
                :language (fn [row]
                            (short-language-name-to-long (:language row)))
                }
      }


     )
    

    ]


   
   [:div.onecolumn
    [:h3 "Users"]
    (rows2table
     (k/exec-raw
      ["SELECT users.given_name || ' ' || users.family_name AS name,
               users.email,
               array_sort_unique(array_agg(role)) AS roles,
               to_char(max(users.created),?) AS joined,
               to_char(max(session.created),?) AS last_login
          FROM vc_user
            AS users
     LEFT JOIN vc_user_role
            ON (users.id = vc_user_role.user_id)
     LEFT JOIN session
            ON (session.user_id = users.id)
      GROUP BY email,name
      ORDER BY email" [time-format time-format]] :results)
     {:cols [:email :name :joined :last_login :roles]}

     )
    ]

   [:div.onecolumn
    [:h3 "Sessions"]
    (rows2table
     (k/exec-raw
      ["SELECT substring(access_token from 0 for 10) || '..' AS access_token,
       to_char(session.created,?) AS created,
       substring(ring_session::text from 0 for 10) || '..'  AS ring_session,
       users.given_name || ' ' || users.family_name AS user,
       users.email
          FROM session
     LEFT JOIN vc_user AS users 
            ON users.id = session.user_id
      ORDER BY session.created DESC" [time-format]] :results)
     {:cols [:email :user :access_token :created :ring_session]}
     )]
   ])


   




      

  



    
