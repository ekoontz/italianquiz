(ns italianverbs.about
  (:use
   [hiccup core page])
  (:require
   [cemerick.friend :as friend]
   [compojure.core :refer [GET]]
   [environ.core :refer [env]]
   [italianverbs.user :refer [do-if do-if-teacher haz-admin?
                              haz-auth? has-admin-role has-teacher-role]]
   [italianverbs.html :as html]))

;; TODO: move this to somewhere that has access to italianverbs.user -
;; this cannot be in menubar or html.
(defn login-form [request]
  (let [authentication (haz-auth? request)]
    (if authentication
      (html
       [:div {:class "login major"}
        [:h4 {:style "width:auto;float:right;"} (:username (friend/current-authentication))]
        [:div {:style "width:auto;float:left"} [:a {:href "/auth/logout"} "Log out"]]])

      (html
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
        ]))))

(declare about)

(def routes 
  (GET "/about" request
       {:status 200
        :headers {"Content-Type" "text/html;charset=utf-8"}
        :body (html/page "Welcome to Verbcoach"
                         (about request)
                         request
                         {:show-login-form (login-form request)})}))


(defn about [request]
   [:div.major {:style "height: 500px"} [:h2 "Welcome to Verbcoach."]

;   [:p 
;    "You can use this website just by clicking on the language you want to ;practice, or you can login using your existing Google account (enter your google username and password)."
;    ]

    [:div.intro "The best place on the web to learn how to conjugate verbs."]
    [:div.flags
     [:div.flag

       [:img {:src "/png/Flag_of_Italy.svg.png" }]

       [:div.language "Italiano"]
      ]

     [:div.flag
       [:img {:src "/png/Flag_of_Mexico.svg.png" }]

       [:div.language "Español"]
      ]

     [:div.flag

       [:img {:src "/png/Flag_of_Spain.svg.png" }]

       [:div.language "Español"]
       
      ]
     

     [:div.flag

      [:img {:src "/png/Flag_of_France.svg.png" }]
      [:div.language "Français"]

      [:i {:style "text-align:center;color:#ccc"} "Coming soon" ]]
     ]
    ])
