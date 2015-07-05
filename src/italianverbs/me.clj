(ns italianverbs.me
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :refer [write-str]]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.config :refer [time-format]]
   [italianverbs.html :refer [page]]
   [korma.core :as k]))

(declare latest-questions)
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
  (let [profile {

                 {:tense :present}
                 {"parlare" 0
                  "bere" 1
                  "alzare" 2}
                 
                 {:tense :imperfetto}
                 {"mangiare" 3
                  "alzare" 4}

                 {:tense :passato}
                 {"parlare" 5
                  "alzare" 6}
                
                 {:tense :conditional}
                 {"parlare" 7
                  "bere" 8
                  "alzare" 9}
                 }
        ]
    [:div#me
    
     [:div#myprofile {:class "major"}

      [:h2 "Profile"]

      [:h3 "Overall"]
     
      (profile-table profile)
      
      ]

     [:div#last {:class "major"}
      [:h2 "Latest questions"]
      (latest-questions)      
      ]
     ]))

(declare profile-row)

(defn profile-table [profile]
  (let [sorted-elements (reverse (sort-by :tense profile))

        profile
        (zipmap (keys sorted-elements)
                (vals sorted-elements))

        verbs (sort (set (flatten (map keys (vals sorted-elements)))))

        tenses (keys profile)
        
        ]
    [:table.profile

     ;; top row: show all verbs
     [:tr

      (map (fn [verb]
             [:th [:div verb]])
           verbs)
      
      ]

     (map (fn [tense]
            (profile-row tense (get profile tense) verbs))
          tenses)
     ]))

(declare profile-column)

(defn profile-row [key tense-row verbs]
  [:tr
   (map (fn [verb]
          (profile-column verb (get tense-row verb)))
        verbs)

   ;; final column in this row: every row represents one tense, so show that tense's name.
   [:th.tense (:tense key)]])

(defn profile-column [key val]
  (let [level val]
    [:td {:class (str "level" level)}
     (str " &nbsp;")]))

(defn latest-questions []
  (let [query
        "SELECT to_char(issued,?) AS issued,
                issued AS issued_sortby,
                expression.surface AS question,answer,
                time_to_correct_response AS ttcr
           FROM question 
     INNER JOIN expression 
             ON (expression.id = question.source) 
       ORDER BY issued_sortby DESC 
          LIMIT 50"

        results (k/exec-raw [query [time-format]]
                            :results)]
   (if (empty? results)
     [:div [:i "None."]]

     ;; else
     [:table {:class "striped padded"}
      [:tr
       [:th "When"]
       [:th "Question"]
       [:th "Answer"]
       [:th "Profile"]
       ]
      
      (map
       (fn [result]
         (let [ttcr (:ttcr result)
               profile
               (cond
                (or (nil? ttcr)
                    (> ttcr 20000)) ;; really bad.
                "level0"

                (> ttcr 10000)
                "level1"

                (> ttcr 5000)
                "level2"

                (> ttcr 2500)
                "level3"

                true ;; really good!
                "level4")]
           [:tr
            [:td (:issued result)]
            [:td (:question result)]
            [:td (:answer result)]
            [:td {:class profile}
             (:ttcr result)]]))
       results)])))
