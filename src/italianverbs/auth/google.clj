(ns italianverbs.auth.google
  (:require [cemerick.friend [workflows :as workflows]]
            [cemerick.friend :as friend]
            [clojure.tools.logging :as log]
            [compojure.core :as compojure :refer [ANY GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :refer [format-config-uri]]
            [italianverbs.auth :as auth]))

(derive ::admin ::user)

;; verbcoach-workstation
(def workstation-google-api-config
;; TODO: checked in to source code: remove here and in Google console and replace with env settings.
  {:client-id "652200734300-3tbdfqhisnlctt6vh70boofrc08qc6a7.apps.googleusercontent.com"
   :client-secret "Nvj41la8ao_wsnXQunbp5arR"
   :callback {:domain "http://localhost:3000" :path "/oauth2callback"}})

;; verbcoach-dev
;; TODO: checked in to source code: remove here and in Google console and replace with env settings.
(def verbcoach-dev-google-api-config
  {:client-id "845033688568-1emrraqe25pnlj984s3cndfpd6s963vh.apps.googleusercontent.com"
   :client-secret "Y6dqv5fFna2QB4E5Xm-Yz8y3"
   :callback {:domain "http://verbcoach-dev.herokuapp.com" :path "/oauth2callback"}})

;; TODO: move to environment setting
(def client-config workstation-google-api-config)

(defn credential-fn [token]
  ;;lookup token in DB or whatever to fetch appropriate :roles
  {:identity token :roles #{::user}})

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

(def auth-config {:client-config client-config
                  :uri-config uri-config
                  :credential-fn credential-fn})

;; TODO: should be one level above this (italianverbs.auth)
(defn is-authenticated [if-authenticated]
  (if (not (nil? (friend/current-authentication)))
    if-authenticated
     (do (log/debug (str "is-authenticated: not authenticated; redirecting to /"))
         {:status 302
          :headers {"Location" "/"}})))

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

   (GET "/login" request
        (do
          (friend/authorize #{::user} "Authorized page.")
          (is-authenticated
           {:status 302
            :headers {"Location" "/"}})))
        
   (GET "/admin" request
        (friend/authorize #{::admin} "Only admins can see this page."))))
