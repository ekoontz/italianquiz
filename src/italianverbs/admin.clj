(ns italianverbs.admin
  (:require
   [italianverbs.config :refer [time-format]]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.html :refer [page tablize]]
   [italianverbs.user :refer [do-if-admin]]
   [korma.core :as k]))

(declare admin)

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
                  {:onload "admin_onload();"
                   :css ["/css/admin.css"]
                  :jss ["/js/admin.js"]
                   })})))))

(defn admin []
  [:div#admin {:class "major"}
   [:h2 "Admin"]

   [:div {:style "float:left;width:48%"}
    [:h3 "Users"]
    (tablize
     (k/exec-raw
      ["SELECT users.given_name || ' ' || users.family_name AS name,
               users.email,to_char(max(session.created),?) AS last_login
          FROM vc_user
            AS users
     LEFT JOIN session
            ON (session.user_id = users.id)
      GROUP BY email,name
      ORDER BY last_login,name" [time-format]] :results)
     {:cols [:email :name :last_login]}

     )

    [:h3 "Roles"]
    (tablize
     (k/exec-raw
      ["SELECT users.given_name || ' ' || users.family_name AS name,
               users.email,array_sort_unique(array_agg(role)) AS roles
          FROM vc_user AS users
    INNER JOIN vc_user_role
            ON users.id = vc_user_role.user_id
      GROUP BY users.given_name,users.family_name,users.email
      ORDER BY users.family_name,users.given_name"
       []] :results)
     {:cols [:email :name :roles]}
     )
    

    ]

   [:div {:style "float:left;width:48%"}
    [:h3 "Sessions"]
    (tablize
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


   




      

  



    
