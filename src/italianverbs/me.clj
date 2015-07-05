(ns italianverbs.me
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :refer [write-str]]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.html :refer [page]]
   [korma.core :as k]))

(declare me)
(declare profile-table)

(def routes
  (let [headers {"Content-Type" "text/html;charset=utf-8"}]
    (compojure/routes

     (GET "/" request
          {:headers headers
           :status 200
           :body
           (page "My page" (me request)
                 request
                 {:onload "me()" ;; pass along userid
                  :css ["/css/me.css"]
                  :jss ["/css/me.js"]})}))))

(defn me [request]
  (let [profile {}]
    [:div#me {:class "major"}

     [:h2 "My profile"]

     [:h3 "Overall"]
     
     (profile-table profile)

     [:h3 "The foo game"]
     
     (profile-table profile)
     
     [:h3 "The bar game"]
     
     (profile-table profile)

     ]))

(defn profile-table [profile]
  [:table.profile
   [:tr
    [:td {:class "level0"}
     "&nbsp;"
     ]
    [:td {:class "level1"}
     "&nbsp;"
     ]
    [:td {:class "level2"}
     "&nbsp;"
     ]
    [:td {:class "level3"}
     "&nbsp;"
     ]
    [:td {:class "level4"}
     "&nbsp;"
     ]
    [:td {:class "level5"}
     "&nbsp;"
     ]
     [:td {:class "level6"}
      "&nbsp;"
      ]
    ]

   [:tr
    [:td {:class "level0"}
     "&nbsp;"
     ]
    [:td {:class "level1"}
     "&nbsp;"
     ]
    [:td {:class "level2"}
     "&nbsp;"
     ]
    [:td {:class "level3"}
     "&nbsp;"
     ]
    [:td {:class "level4"}
     "&nbsp;"
     ]
    [:td {:class "level5"}
     "&nbsp;"
     ]
     [:td {:class "level6"}
      "&nbsp;"
      ]
    ]

   [:tr
    [:td {:class "level0"}
     "&nbsp;"
     ]
    [:td {:class "level1"}
     "&nbsp;"
     ]
    [:td {:class "level2"}
     "&nbsp;"
     ]
    [:td {:class "level3"}
     "&nbsp;"
     ]
    [:td {:class "level4"}
     "&nbsp;"
     ]
    [:td {:class "level5"}
     "&nbsp;"
     ]
     [:td {:class "level6"}
      "&nbsp;"
      ]
    ]


   ])


