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

   [:h3 "Sessions"]
   (tablize
    (k/exec-raw
     ["SELECT substring(access_token from 0 for 10) || '..' AS access_token,
       to_char(session.created,?) AS created,
       substring(ring_session::text from 0 for 10) || '..'  AS ring_session,
       users.given_name || ' ' || users.family_name AS user,
       users.email
  FROM session
LEFT JOIN vc_user AS users ON users.id = session.user_id
  ORDER BY created DESC" [time-format]] :results)
    {:cols [:created :access_token :ring_session :email :user]}

    )])



      

  



    
