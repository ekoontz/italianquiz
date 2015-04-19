(ns italianverbs.auth)

(require '[clojure.tools.logging :as log])
(require '[clojure.string :as str])
(require '[compojure.core :as compojure :refer [GET PUT POST DELETE ANY]])
(require '[digest])
(require '[environ.core :refer [env]])
(require '[italianverbs.auth.google :as google])
(require '[italianverbs.auth.internal :as internal])
(require '[italianverbs.korma :as db])
(require '[italianverbs.session :as session])
(require '[cemerick.friend :as friend])
(require '[cemerick.friend.credentials :as creds])
(require '[ring.util.response :as resp])

(def routes
  (compojure/routes
   (GET "/login" request
        (resp/redirect "/"))
   (GET "/login/" request
        (resp/redirect "/"))
   (POST "/login" request
         (resp/redirect "/"))
   (ANY "/logout" request
        (friend/logout* (resp/redirect "/auth/login")))
   ;; TODO: make this a POST with 'username' and 'password' params so that users can login.
   (GET "/session/set/" request
        {:side-effect (session/register request)
         :session (get request :session)
         :status 302
         :headers {"Location" "/?msg=set"}})
   (GET "/session/clear/" request
        {:side-effect (session/unregister request)
         :status 302
         :headers {"Location" "/?msg=cleared"}})))

(defn get-user-id [fetch-fn]
  (let [username (:username (friend/current-authentication))]
    (:id (first (fetch-fn :student {:username username})))))

(defn confirm-and-create-user [request]
  (do (log/info (str "confirm-and-create-user: " request))
      {:status 302
       :headers {"Location" "/game"}}))

(defn haz-admin []
  (log/debug (str "haz-admin: current-authentication: " (friend/current-authentication)))
  (and (not (nil? (friend/current-authentication)))
       (not (nil?
             (:italianverbs.auth.internal/admin
              (:roles (friend/current-authentication)))))))

;; TODO: should be a macro, so that 'if-admin' is not evaluated unless (haz-admin) is true.
(defn is-admin [if-admin]
  (if (haz-admin)
    if-admin
    {:status 302
     :headers {"Location" "/login"}}))

;; TODO: should also be a macro.
(defn is-authenticated [if-authenticated]
  (if (not (nil? (friend/current-authentication)))
    if-authenticated
    {:status 302
     :headers {"Location" "/login"}}))

(defn get-loggedin-user-roles [identity]
  (map #(str/replace
         (str/replace %
                      #"^user" "student")
         #"^admin" "teacher")

       (-> identity friend/current-authentication :roles)))

(defn credential-fn [arg]
  (creds/bcrypt-credential-fn @internal/users arg))

;; in the above, the @internal/users map works as a 1-arg fn: (fn [user]) that 
;; takes a user id and returns that user's authentication and authorization info, 
;; so if you "call" @users with a given argument, (i.e. get the given
;; key in the @users map, e.g.:
;; (@users "tom")
;; the "return value" (i.e. value for that key) is:
;; {:username "tom", 
;;  :password "$2a$10$48TyZw9Ii6bpc.uwJtoXuuMHiRtwNPgC3yczPcpTLao0m0kaIVo02", 
;;  :roles #{:friend-interactive-form.users/user}}

(def login-form
  [:div 
   [:div {:class "login major"}
    [:form {:method "POST" :action "/auth/login"}
     [:table
      [:tr
       [:th "User"][:td [:input {:type "text" :name "username" :size "10"}]]
       [:th "Password"][:td [:input {:type "password" :name "password" :size "10"}]]
       [:td [:input {:type "submit" :class "button" :value "Login"}]]]]]]])
