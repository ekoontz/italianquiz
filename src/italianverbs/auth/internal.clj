(ns italianverbs.auth.internal)
;; Internal authentication - not third-party (e.g. Google, Facebook, Twitter, LinkedIn etc...)

(require '[cemerick.friend 
           [workflows :as workflows]
           [credentials :as creds]])
(require '[cemerick.friend :as friend])
(require '[clojure.tools.logging :as log])
(require '[clojure.string :as str])
(require '[compojure.core :as compojure :refer [context GET PUT POST DELETE ANY]])
(require '[digest])
(require '[environ.core :refer [env]])
(require '[formative.core :as f])
(require '[hiccup.core :refer (html)])
(require '[italianverbs.html :refer [page]])
(require '[italianverbs.korma :as db])
(require '[italianverbs.menubar :refer [menubar menuitem]])
(require '[ring.util.response :as resp])

(def routes
  (compojure/routes
   (GET "/" request
        (do
          {:status 302
           :headers {"Location" "/"}}))

   (GET "/register" request
        (page "Register"
              [:div.major
               [:h2 "Register with Verbcoach"]

               (f/render-form
                {:action "/auth/internal/register"
                 :enctype "multipart/form-data"
                 :method "post"
                 :fields [{:name :email :size 50 :label "Email"}
                          {:name :password :type :password :label "Password"}
                          {:name :confirm :type :password :label "Confirm"}]})
               ]
              request
              (fn [request]
                {:menubar (menubar {:extra-menu-item (menuitem {:selected? true
                                                                :current-url "/"
                                                                :text "Register"
                                                                :requires-admin false
                                                                :url-for-this-item "/internal/register"
                                                                :show? true})})})))
   (POST "/register" request
         (do
           (log/debug (str "request: " request))
           {:status 302
            :headers {"Location"
                      (str "/auth/internal/register?thanks")}}))
   
   (GET "/forgotpassword" request
        (page "Send password reset mail"
              [:div.major
               [:h2 "Send password reset mail"]

               (f/render-form
                {:action "/auth/internal/forgotpassword"
                 :enctype "multipart/form-data"
                 :method "post"
                 :fields [{:name :email :size 50 :label "Email"}]})
               ]
              request
              (fn [request]
                {:menubar (menubar {:extra-menu-item (menuitem {:selected? true
                                                                :current-url "/"
                                                                :text "Forgot Password"
                                                                :requires-admin false
                                                                :url-for-this-item "/internal/forgotpassword"
                                                                :show? true})})})))
   (POST "/forgotpassword" request
         (do
           (log/debug (str "request: " request))
           {:status 302
            :headers {"Location"
                      (str "/auth/internal/forgot?mailsent")}}))

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
