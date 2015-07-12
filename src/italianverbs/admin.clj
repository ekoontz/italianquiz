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
     ["SELECT substring(access_token from 0 for 10) || '...' AS access_token,
              to_char(created,?) AS created,
              ring_session AS ring_session,
              user_id
         FROM session
     ORDER BY created DESC 
        LIMIT 20" [time-format]] :results)
    {:cols [:access_token :created :ring_session :user_id]}

    )])



      

  



    
