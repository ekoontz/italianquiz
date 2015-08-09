(ns italianverbs.authentication)

;; TODO: cleanup (require)s.
(require '[clojure.tools.logging :as log])
(require '[clojure.string :as str])
(require '[compojure.core :as compojure :refer [context GET PUT POST DELETE ANY]])
(require '[digest])
(require '[environ.core :refer [env]])
(require '[friend-oauth2.workflow :as oauth2])

(require '[italianverbs.auth.google :as google])
(require '[italianverbs.auth.internal :as internal])
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

(defn current [ & [request]]
  (let [current-authentication (friend/current-authentication)]
    (log/debug (str "authentication/current: friend/current-authentication: " current-authentication))
    ;; Try each supported authentication method. Currently 'google' and 'internal' are supported.
    ;; 'internal' is insecure unless SSL is used. It must be explicitly enabled
    ;; with ALLOW_INTERNAL_AUTHENTICATION.
    (cond (and current-authentication
               (map? current-authentication)
               (contains? (:roles current-authentication)
                          :italianverbs.auth.google/user))
          (let [google-username
                (google/token2username (get-in current-authentication [:identity :access-token])
                                       request)]
            (log/debug (str "returning google-username: " google-username))
            google-username)

          (and (= (:allow-internal-authentication env) "true")
               current-authentication
               (map? current-authentication)
               (contains? (:roles current-authentication)
                          :italianverbs.auth.internal/user))
          (let [username (:username current-authentication)]
            (log/debug "returning internal-username: " username)
            username)

          true (do (log/debug "no authentication found.")
                   nil))))

;; TODO: move to auth/internal.clj
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

;; TODO: move to auth/internal.clj
(def login-form
  [:div {:class "login major"}
   [:div {:style "float:left; width:55%"}
    [:a {:href "/auth/google/login"} "Login with Google"]]
   (if (:allow-internal-authentication env)
     [:div
      ;; the :action below must be the same as given in
      ;; core/app/:login-uri. The actual value is arbitrary and is
      ;; not defined by any route (it is friend-internal).
      [:form {:method "POST" :action "/login"}
       [:table
        [:tr
         [:th "Email"][:td [:input {:type "text" :name "username" :size "10"}]]
         [:th "Password"][:td [:input {:type "password" :name "password" :size "10"}]]
         [:td [:input {:type "submit" :class "button" :value "Login"}]]]]]])
   [:div {:style "float:right;text-align:right;width:45%;border:0px dashed blue"} [:a {:href "/auth/internal/register"} "Register a new account"]]
   ])

(defn request2user [request]
  ;; For now, google functionality is the only way to resolve a username from a request.
  (let [id (friend/identity request)]
    (google/token2username (-> id :current :access-token) request)))

(defn logged-in-content [request]
  (let [id (friend/identity request)]
    (log/debug (str "logged-in-content with request: " request))
    (log/debug (str "logged-in-content with id: " id))
    (let [username (cond (string? (:current id))
                         (:current id)

                         (map? (:current id))
                         ;; if it's a map, google is only possibility for now.
                         (request2user request))

          picture (if (map? (:current id))
                    (google/token2picture (-> id :current :access-token)))
          ]
      [:div {:class "login major" :style "display:block"}
       [:table {:style "border:0px"}
        [:tr
         [:td
          username]
         (if picture
           [:td
            [:img#profile {:src picture}]])
         [:td {:style "float:right;white-space:nowrap"}
          [:a {:href "/auth/logout"} "Log out"]]]]])))

(defn no-auth [& {:keys [login-uri credential-fn login-failure-handler redirect-on-auth?]
                  :as form-config
                  :or {redirect-on-auth? true}}]
  "This workflow simply sends a session cookie to the client if the client doesn't already have one. Using '?setsession=true' is used to prevent endless re-generation if the client refuses to supply a cookie to us."
  (fn [{:keys [request-method params form-params]
        :as request}]
    (let [ring-session (get-in request [:cookies "ring-session" :value])
          attempted-to-set-session? (get-in request [:query-params "setsession"])]
      (log/trace "Checking session config for this unauthenticated, sessionless user: request: " request)

      (cond
       (and (not ring-session)
            attempted-to-set-session?)
       (do
         (log/debug "User is blocking session cookies: not attempting to set.")
         nil)
       
       (not ring-session)
       (do
         (log/debug "Creating a session for this unauthenticated, sessionless user: request: " request)
         (let [response
               {:status 302
                :headers {"Location" "?setsession=true"}
                :session {}}]
           response))

       true
       (do (log/trace "Unauthenticated user has an existing session: " ring-session)
           (google/insert-session-if-none ring-session)
           nil)))))
