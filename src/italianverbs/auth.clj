(ns italianverbs.auth)

;; TODO: cleanup (require)s.
(require '[clojure.tools.logging :as log])
(require '[clojure.string :as str])
(require '[compojure.core :as compojure :refer [context GET PUT POST DELETE ANY]])
(require '[digest])
(require '[environ.core :refer [env]])
(require '[friend-oauth2.workflow :as oauth2])
(require '[italianverbs.auth.google :as google])
(require '[italianverbs.auth.internal :as internal])
(require '[italianverbs.korma :as db])
(require '[italianverbs.session :as session])
(require '[cemerick.friend :as friend])
(require '[cemerick.friend.credentials :as creds])
(require '[ring.util.response :as resp])

(def routes
  (compojure/routes

   ;; Google Authentication
   (context "/google" []
            google/routes)

   ;; Verbcoach-internal Authentication
   (context "/internal" []
            internal/routes)

   (friend/logout (ANY "/logout" request (ring.util.response/redirect "/")))))

(defn get-user-id [fetch-fn]
  (log/debug (str "getting user id with current authentication: " (friend/current-authentication)))
  (let [username (:username (friend/current-authentication))]
    (:id (first (fetch-fn :student {:username username})))))

(defn confirm-and-create-user [request]
  (do (log/info (str "confirm-and-create-user: " request))
      {:status 302
       :headers {"Location" "/game"}}))

(defn haz-admin []
  (let [authentication (friend/current-authentication)]
    (log/debug (str "haz-admin: (friend/current-authentication): " authentication))
    (if (= (:allow-internal-admins env) "true")
      (log/warn (str "ALLOW_INTERNAL_ADMINS is enabled: allowing internally-authentication admins - should not be enabled in production")))

    (and (not (nil? authentication))
         (or

          ;; Internally-authenticated admins (Should not be used in production until passwords are not stored in plain text)
          (and (:allow-internal-admins env)
               (not (nil?
                     (:italianverbs.auth.internal/admin
                      (:roles authentication)))))
   
          ;; Google-authenticated admins - note that we decide here (not within google/) about whether the user
          ;; is an admin.
          (let [google-username (google/token2username (get-in authentication [:identity :access-token]))]
            (or (= google-username "ekoontz@gmail.com")
                (= google-username "franksfo2003@gmail.com")))))))

;; TODO: should be a macro, so that 'if-admin' is not evaluated unless (haz-admin) is true.
(defn is-admin [if-admin]
  (if (haz-admin)
    if-admin
    {:status 302
     :headers {"Location" "/"}}))

;; TODO: should also be a macro.
(defn is-authenticated [if-authenticated]
  (if (not (nil? (friend/current-authentication)))
    if-authenticated
    {:status 302
     :headers {"Location" "/auth/login"}}))

(defn get-loggedin-user-roles [identity]
  (map #(str/replace
         (str/replace %
                      #"^user" "student")
         #"^admin" "teacher")

       (-> identity friend/current-authentication :roles)))

(defn credential-fn [arg]
  (log/debug (str "calling credential-fn with arg: " arg))
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

    [:a {:href "/auth/google/login"} "Login with Google"]
    [:form {:method "POST" :action "/auth/internal/login"}
     [:table
      [:tr
       [:th "User"][:td [:input {:type "text" :name "username" :size "10"}]]
       [:th "Password"][:td [:input {:type "password" :name "password" :size "10"}]]
       [:td [:input {:type "submit" :class "button" :value "Login"}]]]]]]])

(defn logged-in-content [req identity]
  (log/trace (str "logged-in-content with identity: " identity))
  (let [username (cond (string? (:current identity))
                       (:current identity)

                       (map? (:current identity))
                       ;; if it's a map, google is only possibility for now.
                       (google/token2username (-> identity :current :access-token)))
        picture (if (map? (:current identity))
                  (google/token2picture (-> identity :current :access-token)))
        ]
    
    (log/debug (str "verbcoach username: " username))

    [:div {:class "login major" :style "display:block"}
     [:table {:style "border:0px"}
      [:tr
       [:td
        username]
       (if picture
         [:td
          [:img#profile {:src picture}]])
       [:th {:style "display:none"}
        (str "Roles:")]
       [:td {:style "white-space:nowrap;display:none"}
        (str/join ","
                  (get-loggedin-user-roles identity))]
       [:td {:style "float:right;white-space:nowrap"} [:a {:href "/auth/logout"} "Log out"]]]]]))

