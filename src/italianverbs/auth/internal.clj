(ns italianverbs.auth.internal)

;; Internal authentication - not third-party (e.g. Google, Facebook, Twitter, LinkedIn etc...)
(require '[clojure.tools.logging :as log])
(require '[clojure.string :as str])
(require '[compojure.core :as compojure :refer [context GET PUT POST DELETE ANY]])
(require '[digest])
(require '[environ.core :refer [env]])
(require '[italianverbs.korma :as db])
(require '[cemerick.friend 
           [workflows :as workflows]
           [credentials :as creds]])
(require '[cemerick.friend :as friend])
(require '[ring.util.response :as resp])

(def routes
  (compojure/routes
   (GET "/" request
        (do
          (log/debug (str "INTERNAL AUTHENTICATION: DEBUG."))
          {:status 302
           :headers {"Location" "/"}}))

   (GET "/register" request
         (str "INTERNAL AUTHENTICATION: GET /register."))
   (POST "/register" request
         (str "INTERNAL AUTHENTICATION: POST /register."))
   (GET "/forgotpassword" request
         (str "INTERNAL AUTHENTICATION: GET /forgotpassword."))
   (POST "/forgotpassword" request
         (str "INTERNAL AUTHENTICATION: POST /forgotpassword."))

   (GET "/resetpassword" request
         (str "INTERNAL AUTHENTICATION: GET /forgotpassword."))
   (POST "/resetpassword" request
         (str "INTERNAL AUTHENTICATION: POST /forgotpassword."))
   ))

(derive ::admin ::user)

;; internal authentication database - for testing only; not production, 
;; as passwords are plaintext. TODO: keep passwords elsewhere.
(def users (atom {;"franco" {:username "franco"
                  ;          :password (creds/hash-bcrypt "franco")
                  ;          :roles #{::user ::admin}}

                  "michael" {:username "michael"
                             :password (creds/hash-bcrypt "marcheschi")
                             :roles #{::user ::admin}}

                  "gino" {:username "gino"
                          :password (creds/hash-bcrypt "gino")
                          :roles #{::user}}}))

(defn credential-fn [arg]
  (log/debug (str "calling credential-fn with arg: " arg))
  (creds/bcrypt-credential-fn @users arg))

;; in the above, the @users map works as a 1-arg fn: (fn [user]) that 
;; takes a user id and returns that user's authentication and authorization info, 
;; so if you "call" @users with a given argument, (i.e. get the given
;; key in the @users map, e.g.:
;; (@users "tom")
;; the "return value" (i.e. value for that key) is:
;; {:username "tom", 
;;  :password "$2a$10$48TyZw9Ii6bpc.uwJtoXuuMHiRtwNPgC3yczPcpTLao0m0kaIVo02", 
;;  :roles #{:friend-interactive-form.users/user}}

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

