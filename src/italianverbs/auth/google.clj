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
  (log/error (str "CREDENTIAL FN TOKEN: " token))
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

;; TODO: factor out update/insert into separate function with more representative names
(defn token2username [access-token request]
  (log/info (str "token2username: " access-token ";" request))
  (let [user-by-access-token (first (k/exec-raw [(str "SELECT email,session FROM vc_user WHERE access_token=?") [access-token]] :results))
        session (if (and request
                         (:cookies request)
                         (get (:cookies request) "ring-session"))
                  (:value (get (:cookies request) "ring-session")))]
    (if user-by-access-token
      (let [email (:email user-by-access-token)]
        (log/info (str "found user by access-token in Postgres vc_user database: email: "
                       email))
        (if (not (= session (:session user-by-access-token)))
          (do
            (log/info (str "updating existing vc_user with session: " session))
            (k/exec-raw [(str "UPDATE vc_user
                                  SET (session) = (?::uuid)
                                WHERE email=? AND access_token=?")
                         [session email access-token]])
            email)))

      ;; else, access token was not found.
      (do
        (log/info (str "user's token was not in database: querying: https://www.googleapis.com/oauth2/v1/userinfo?access_token=<input access token> to obtain user info"))
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
                                                v))
                email (get body :email)
                given-name (get body :given_name)
                family-name (get body :family_name)
                picture (get body :picture)]
            (log/info (str "Google says user's email is: " email))
            (log/info (str "Google says user's given_name is: " given-name))
            (log/info (str "Google says user's family_name is: " family-name))
            (log/info (str "Google says user's picture is: " picture))
            
            (log/info (str "inserting new user record."))
            (k/exec-raw [(str "INSERT INTO vc_user (access_token,given_name,family_name,picture,updated,email,session)
                                            VALUES (?,?,?,?,now(),?,?::uuid)") 
                         [access-token given-name family-name picture email session]])
            email))))))

(defn insert-session [email session-cookie]
  (log/info (str "inserting new session record."))
  (k/exec-raw [(str "INSERT INTO session (id,user_id) SELECT ?,id FROM vc_user WHERE email=?")
               [session-cookie email]]))

(defn token2info [access-token]
  (first (k/exec-raw [(str "SELECT * FROM vc_user WHERE access_token=?")
                      [access-token]]
                     :results)))

(defn token2picture [access-token]
  (let [token2info (token2info access-token)]
    (if token2info
      (:picture token2info))))

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
          (log/debug (str "running /login with google-client-id: " (:client-id client-config)))

          ;; need all three environment variables set correctly; otherwise thrown an exception.
          (if (nil? (:client-id client-config))
            (do
              (log/error (str "No google client id was found in the environment."))
              (throw (Exception. (str "You must define GOOGLE_CLIENT_ID in your environment.")))))

          (if (nil? (:client-secret client-config))
            (do
              (log/error (str "No google client secret was found in the environment."))
              (throw (Exception. (str "You must define GOOGLE_CLIENT_SECRET in your environment.")))))

          (if (nil? (:callback client-config))
            (do
              (log/error (str "No google client callback was found in the environment."))
              (throw (Exception. (str "You must define GOOGLE_CALLBACK_DOMAIN in your environment.")))))

          (friend/authorize #{::user} "Authorized page.")
          (is-authenticated
           (do
             (let [username (token2username
                             (-> request :session :cemerick.friend/identity :current :access-token)
                             request)]
               (log/debug (str "Logging in user with access token: " 
                               (-> request :session :cemerick.friend/identity :current :access-token))))
             {:status 302
              :headers {"Location" "/"}}))))
        
   (GET "/admin" request
        (friend/authorize #{::admin} "Only admins can see this page."))))


