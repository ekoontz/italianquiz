(ns italianverbs.about
  (:require
   [compojure.core :refer [GET]]
   [italianverbs.auth :refer [haz-admin]]
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
   [:div {:style "width:100%; height: 500px;"   :class "major"} [:h2 "Welcome to Verbcoach."]

;   [:p 
;    "You can use this website just by clicking on the language you want to ;practice, or you can login using your existing Google account (enter your google username and password)."
;    ]

    [:div.intro 

     "The best place on the web to learn how to conjugate verbs."

     ]


    [:div.flags
     [:div.flag

      [:a {:href "/tour/it"}
       [:img {:src "/png/Flag_of_Italy.svg.png" }]]

      [:a {:href "/tour/it"}
       [:div.language "Italiano"]
       ]
      ]

     [:div.flag

      [:a {:href "/tour/es/MX"}
       [:img {:src "/png/Flag_of_Mexico.svg.png" }]]

      [:a {:href "/tour/es/MX"}
       [:div.language "Español"]
       ]
      ]

     [:div.flag

      [:a {:href "/tour/es/ES"}
       [:img {:src "/png/Flag_of_Spain.svg.png" }]]

      [:a {:href "/tour/es/ES"}
       [:div.language "Español"]
       ]
      ]
     

     [:div.flag

      [:img {:src "/png/Flag_of_France.svg.png" }]
      [:div.language "Français"]

      [:i {:style "text-align:center;color:#ccc"} "Coming soon" ]]
     ]

    [:div {:style "float:left;margin-left:25%;width:75%;margin-top:1em"}
     [:p "Use the dropdown within the tour to choose a class/game."]
     [:img {:width "350px" :src "/png/select_game.png"} ]]

    
    (if (haz-admin)
      [:div {:class "rounded flags manage"}

       [:h3 "Manage Games"]

       [:div {:class "flag smallflag"}
        [:a {:href "/editor/it"}
          [:img {:src "/png/Flag_of_Italy.svg.png" }]]
        [:a {:href "/editor/it"}
         [:div.language "Italiano"]
         ]
        ]

       [:div {:class "flag smallflag"}
        [:a {:href "/editor/es"}
         [:img {:src "/png/Flag_of_Spain.svg.png" }]]
        [:a {:href "/editor/es"}
         [:div.language "Español"]
         ]
        ]
       
       [:div {:class "flag smallflag"}
        [:img {:src "/png/Flag_of_France.svg.png" }]
        [:div.language "Français"]
        [:i {:style "text-align:center;color:#ccc"} "Coming soon"]]]
      )
    
    ])

    




