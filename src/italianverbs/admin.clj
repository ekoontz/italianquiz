(ns italianverbs.admin
  (:require
   [italianverbs.config :refer [time-format]]
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

   [:div.twocolumn
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

   [:div.twocolumn
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
     {:cols [:created :email :user :access_token :ring_session]}
     )]
   ])


   




      

  



    
