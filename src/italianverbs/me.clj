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

;; TODO: move to test/me
(def mock-profile
  [
   {:tense :present
    :verb "parlare"
    :level 5}

   {:tense :present
    :verb "bere"
    :level 1}

   {:tense :present
    :verb "alzare"
    :level 1}

   {:tense :imperfetto
    :verb "alzare"
    :level 2}
                 
   {:tense :imperfetto
    :verb "mangiare"
    :level 4}

   {:tense :passato
    :verb "parlare"
    :level 6}
   
   {:tense :conditional
    :verb "bere"
    :level 8
    }
   ])

(defn get-in-profile [spec]
  "look in question table to find out user's profile for this particular spec."
  (let [sql "SELECT DISTINCT question.id,
       source.surface AS question,
       target.surface AS possible_answer,
       question.answer,
       question.time_to_correct_response AS ttcr,
       session_id AS session,
       issued
       FROM expression AS source
 INNER JOIN expression AS target
         ON ((target.structure->'synsem'->'sem') @>
             (source.structure->'synsem'->'sem'))
        AND (source.language = 'en')
        AND (target.language = 'it')
 INNER JOIN question
         ON question.source = source.id"]
    ))

(defn me [request]
  [:div#me
   
   [:div#myprofile {:class "major"}
    
    [:h2 "Profile"]
    
    [:h3 "Overall"]
    
    (profile-table mock-profile)
    
    ]
   
   [:div#last {:class "major"}
    [:h2 "Latest questions"]
    (latest-questions)      
    ]
   ])

(declare find-in-profile)

(defn profile-table [profile]
  (let [verbs (sort (set (flatten (map :verb profile))))
        tenses (sort (set (flatten (map :tense profile))))
        ]
    [:table.profile

     ;; top row: show all tenses
     [:tr
      (map (fn [tense]
             [:th [:div tense]])
           tenses)]
     
     (map (fn [verb]
            [:tr
             (map (fn [tense]
                    (let [in-profile (find-in-profile {:verb verb
                                                       :tense tense}
                                                      profile)
                          level (if in-profile (:level in-profile) "")
                          ]
                      [:td {:class (str "level" level)}
                       (str "&nbsp; ")]))
                  tenses)

             [:th.verb verb]])
          verbs)
     ]))

(defn find-in-profile [ {verb :verb
                         tense :tense} profile]
  (if (not (empty? profile))
    (let [item (first profile)]
      (if (and (= verb (:verb item))
               (= tense (:tense item)))
        item
        (find-in-profile {:verb verb
                          :tense tense} (rest profile))))))

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
