(ns italianverbs.auth.google
  (:require [cemerick.friend [workflows :as workflows]]
            [cemerick.friend :as friend]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [compojure.core :as compojure :refer [ANY GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :refer [format-config-uri]]
            [org.httpkit.client :as http]
            [korma.core :as k]))

(require '[environ.core :refer [env]])

(derive ::admin ::user)

(def client-config
  {:client-id (env :google-client-id)
   :client-secret (env :google-client-secret)
   :callback {:domain (env :google-callback-domain)
              :path "/oauth2callback"}})

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

(defn token2username [access-token]
  (let [user-in-db (first (k/exec-raw [(str "SELECT email FROM vc_user WHERE access_token=?") [access-token]] :results))]
    (if user-in-db
      (do (log/info (str "found user and access-token in database; email: " (:email user-in-db)))
          (:email user-in-db))
      (do
        (log/info (str "did not find user in database: querying: https://www.googleapis.com/oauth2/v1/userinfo?access_token=<input access token>"))
        (let [{:keys [status headers body error] :as resp} 
              @(http/get 
                (str "https://www.googleapis.com/oauth2/v1/userinfo?access_token=" access-token))]
          (if error
            (log/info "Failed, exception: " error)
            (log/info "HTTP GET success: " status))
          (log/debug (str "body: " body))
          (let [body (json/read-str body
                                    :key-fn keyword
                                    :value-fn (fn [k v]
                                                v))]
            (let [email (get body :email)]
              (log/info (str "Google says user's email is: " email))
              (k/exec-raw [(str "INSERT INTO vc_user (access_token,email) VALUES (?,?)") [access-token email]])
              (log/debug (str "token2username: " access-token " => " email))
            email)))))))

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
           (do
             (let [username (token2username
                             (-> request :session :cemerick.friend/identity :current :access-token))]
;               (log/info (str "INSERT INTO users words_per_game (game,word) " game-id "," word))

               (log/debug (str "Logging in user with access token: " 
                               (-> request :session :cemerick.friend/identity :current :access-token))))
             {:status 302
              :headers {"Location" "/"}}))))
        
   (GET "/admin" request
        (friend/authorize #{::admin} "Only admins can see this page."))))
