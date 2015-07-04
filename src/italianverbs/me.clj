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
  [:div#me {:class "major"}

  [:h2 "My profile"]

   [:h3 "Overall"]
   
   [:table


    ]


   [:h3 "The foo game"]
   
   [:table


    ]

   [:h3 "The bar game"]
   
   [:table


    ]


   ]

  )


  


