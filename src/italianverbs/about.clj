(ns italianverbs.about
  (:require
   [compojure.core :refer [GET]]
   [italianverbs.user :refer [do-if do-if-teacher haz-admin?
                              has-admin-role has-teacher-role]]
   [italianverbs.html :as html]))

(declare about)

(def routes 
  (GET "/about" request
       {:status 200
        :headers {"Content-Type" "text/html;charset=utf-8"}
        :body (html/page "Welcome to Verbcoach"
                         (about request)
                         request)}))


(defn about [request]
   [:div.major {:style "height: 500px"} [:h2 "Welcome to Verbcoach."]

;   [:p 
;    "You can use this website just by clicking on the language you want to ;practice, or you can login using your existing Google account (enter your google username and password)."
;    ]

    [:div.intro 

     "The best place on the web to learn how to conjugate verbs."

     ]


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
