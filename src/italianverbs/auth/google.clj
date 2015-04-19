(ns italianverbs.auth.google
  (:require [compojure.core :as compojure :refer [ANY GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [cemerick.friend :as friend]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :refer [format-config-uri]]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])))
(defn credential-fn [token]
  ;;lookup token in DB or whatever to fetch appropriate :roles
  {:identity token :roles #{::user}})

(def client-config
  {:client-id "946241140791-833sod9itqgm9v8ihi0gj4ca9c3oevcr.apps.googleusercontent.com"
   :client-secret "pAD9FWexu96vw8YPH0fbPAoB"
   :callback {:domain "http://localhost:3000" :path "/oauth2callback"}})

(def uri-config
  {:authentication-uri {:url "https://accounts.google.com/o/oauth2/auth"
                       :query {:client_id (:client-id client-config)
                               :response_type "code"
                               :redirect_uri (format-config-uri client-config)
                               :scope "email"}}

   :access-token-uri {:url "https://accounts.google.com/o/oauth2/token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :grant_type "authorization_code"
                              :redirect_uri (format-config-uri client-config)}}})

(def routes
  (compojure/routes
   (GET "/" request "open.")
   (GET "/status" request
        (let [count (:count (:session request) 0)
              session (assoc (:session request) :count (inc count))]
          (-> (ring.util.response/response
               (str "<p>We've hit the session page " (:count session)
                    " times.</p><p>The current session: " session "</p>"))
              (assoc :session session))))
   (GET "/authlink" request
        (friend/authorize #{::user} "Authorized page."))
   (GET "/authlink2" request
        (friend/authorize #{::user} "Authorized page 2."))
   (GET "/admin" request
        (friend/authorize #{::admin} "Only admins can see this page."))
   (friend/logout (ANY "/logout" request (ring.util.response/redirect "/")))))

  
