(ns italianverbs.me
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :refer [write-str]]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.config :refer [time-format]]
   [italianverbs.editor :refer [human-tenses-to-spec language-to-root-spec]]
   [italianverbs.html :refer [page]]
   [italianverbs.unify :refer [unify]]
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

   {:tense :imperfect
    :verb "alzare"
    :level 2}

   {:tense :future
    :verb "alzare"
    :level 2}
                 
   {:tense :imperfect
    :verb "mangiare"
    :level 4}

   {:tense :past
    :verb "parlare"
    :level 6}
   
   {:tense :conditional
    :verb "bere"
    :level 8
    }
   ])

(defn get-in-profile [verb tense source target]
  "look in question table to find out user's profile for this particular spec."
  (let [spec
        (unify
         (get human-tenses-to-spec tense)
         (language-to-root-spec target verb)
         )]
    (first
     (k/exec-raw
      [(str
       "SELECT count(question.id),
               sum(question.time_to_correct_response) AS ttcr
       FROM expression AS source
 INNER JOIN expression AS target
         ON ((target.structure->'synsem'->'sem') @>
             (source.structure->'synsem'->'sem'))
        AND (source.language = ?)
        AND (target.language = ?)
        AND (target.structure @> '" (write-str spec) "')
 INNER JOIN question
         ON question.source = source.id
        AND (question.answer = target.surface)")
       [source target]] :results))))

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

(defn ttcr-to-level [ttcr]
  (cond
   (or (nil? ttcr)
       (> ttcr 20000)) ;; really bad.
   0
   
   (> ttcr 10000)
   1
   
   (> ttcr 5000)
   2
   
   (> ttcr 2500)
   3
   
   true ;; really good!
   4))

(defn profile-table [profile]
  (let [verbs (sort (set (flatten (map :verb profile))))
        verbs (map :verb (k/exec-raw
                          [(str "SELECT DISTINCT 
                             structure->'root'->'italiano'->>'italiano' AS verb 
                        FROM expression 
                       WHERE (structure->'root'->'italiano'->'italiano') IS NOT NULL 
                    ORDER BY verb ASC")] :results))

        tenses (sort (set (flatten (map :tense profile))))
        ]
    [:table.profile

     ;; top row: show all tenses
     [:tr
      [:th "&nbsp;"]
      (map (fn [tense]
             [:th [:div tense]])
           tenses)]
     
     (map (fn [verb]
            [:tr
             [:th.verb verb]
             (map (fn [tense]
                    (let [in-profile
                          (get-in-profile verb (keyword tense) "en" "it")
                          level (if (and in-profile
                                         (> (:count in-profile) 0))
                                  (ttcr-to-level
                                   (/ (:ttcr in-profile)
                                      (:count in-profile)))
                                  0)]
                      [:td {:class (str "level" level)}
                       (str
                        " &nbsp; ")]))
                  tenses)
            ])
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
               (str "level" (ttcr-to-level ttcr))]
           [:tr
            [:td (:issued result)]
            [:td (:question result)]
            [:td (:answer result)]
            [:td {:class profile}
             (:ttcr result)]]))
       results)])))
