(ns italianverbs.user
  [:refer-clojure :exclude [get-in resolve]]
  [:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [korma.core :as db]
   [italianverbs.html :refer [page]]
   [italianverbs.korma :as korma]
   [italianverbs.unify :as unify :refer [deserialize get-in ref? strip-refs unify]]])

(declare user-model)

(defn roles-of-user
  "return the roles of user whose id is user-id"
  ([user-id]
     [:foo])
  ([]
     [:bar]))

(def routes
  (let [headers
        {"Content-Type" "text/html;charset=utf-8"}]
    (compojure/routes

     (GET "/" request
          {:headers headers
           :status 200
           :body
           (page "Your profile"
                 (user-model request)
                 request
                 {:onload ""
                  :css ["/css/user.css"]})}))))

(defn get-new-question [game-id & [user-id :user-id]]
  "Ask the expression database for a question for a given game 
   and user-id (if supplied). The question will be a map
   with the following fields:
    - a question id
    - the source expression
    - a list of the the possible correct expressions
  As a side-effect,
   the database will create a new row for the user (again, if supplied)
   for this question."
  )

(defn set-question-issued-at [timestamp]
  "")

(defn show-question-to-user [])

(defn handle-user-response [])

(defn user-model [request]
  [:table.user
   
   [:tr
    [:td
     [:div
      {:class "coord word"}
      "parlare"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "present"]
    ]
    [:td
     [:div
      {:class "coord p075"}
      "future"]
    ]
    [:td
     [:div
      {:class "coord p050"}
      "passato"
      ]
    ]
    [:td
     [:div
      {:class "coord p025"}
      "imperfect"
      ]
    ]
    [:td
     [:div
      {:class "coord p000"}
      "conditional"
      ]
    ]
    ]

   [:tr
    [:td
     [:div
      {:class "coord word"}
      "leggere"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "present"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "future"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "passato"
      ]
    ]
    [:td
     [:div
      {:class "coord p075"}
      "imperfect"
      ]
    ]
    [:td
     [:div
      {:class "coord p075"}
      "conditional"
      ]
    ]
    ]

   

   ]
  )



