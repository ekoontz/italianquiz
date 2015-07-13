(ns italianverbs.me
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.config :refer [time-format]]
   [italianverbs.editor :refer [human-tenses-to-spec
                                language-to-root-spec
                                language-to-root-keyword]]
   [italianverbs.html :refer [page]]
   [italianverbs.unify :refer [unify]]
   [korma.core :as k]))

;; TODO: language-specific
(def tenses (sort [:present :conditional :past :imperfect :future]))

(declare get-in-profile)
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
           (page "My page"
                 (me "it")
                 request
                 {:onload "me_onload();"
                  :css ["/css/me.css"]
                  :jss ["/js/me.js"]
                  })})

     (GET "/:source/:target/:verb/:tense" request
          (let [source (:source (:route-params request))
                target (:target (:route-params request))
                tense (:tense (:route-params request))
                verb (:verb (:route-params request))
                debug (log/trace (str "tense: " tense))
                debug (log/trace (str "verb: " verb))
                response
                (get-in-profile verb (keyword tense) source target)]
            {:body (json/write-str response)
             :status 200
             :headers headers})))))

(defn get-in-profile [verb tense source target]
  "look in question table to find out user's profile for this particular spec."
  (let [spec
        (unify
         (get human-tenses-to-spec tense)
         (language-to-root-spec target verb))

        ;; 1. get counts
        count-result
        (first
         (k/exec-raw
          [(str
            "SELECT count(*)
               FROM expression AS source
         INNER JOIN expression AS target
                 ON ((target.structure->'synsem'->'sem') @>
                     (source.structure->'synsem'->'sem'))
                AND (target.structure @> '" (json/write-str spec) "')
                AND (source.language = ?)
                AND (target.language = ?)
         INNER JOIN question
                 ON question.source = source.id
                AND (question.answer = target.surface)")
           [source target]] :results))

        count-result
        (if count-result
          (:count count-result)
          0)
        
        ;; 2. get the median result, ordered by time-to-correct response
        median
        (first
         (k/exec-raw
          [(str
            "SELECT ttcr,row_id AS median_row
               FROM (SELECT source.surface AS source,
                            target.surface AS target,
                            question.time_to_correct_response AS ttcr,
                            row_number() 
                             OVER (order by question.time_to_correct_response) AS row_id
                       FROM expression AS source
                 INNER JOIN expression AS target
                         ON ((target.structure->'synsem'->'sem') @>
                             (source.structure->'synsem'->'sem'))
                        AND (target.structure @> '" (json/write-str spec) "')
                        AND (source.language = ?)
                        AND (target.language = ?)
                 INNER JOIN question
                         ON question.source = source.id
                        AND (question.answer = target.surface)) AS median_question
                      WHERE median_question.row_id 
                    BETWEEN ?/2.0 
                        AND ?/2.0+1")
           [source target count-result count-result]] :results))]
    (merge {:count count-result
            :source source
            :verb verb
            :target target
            :tense tense}
           (if median
             median
             {:ttcr 0}))))

(declare instructor-selector)

(defn me [language]
  [:div#me
   
   [:div#myprofile {:class "major"}
    
    [:h2 "Profile"]

    [:h3 "Instructor"]

    (instructor-selector)
    
    [:h3 "Overall"]
    
    (profile-table language)
    
    ]
   
   [:div#last {:class "major"}
    [:h2 "Your latest questions"]
    (latest-questions)      
    ]
   ])

(defn instructor-selector []
  [:select
   [:option {:value ""} "No instructor chosen"]
   (map (fn [row]
          [:option {:value (:instructor_id row)}
           (:instructor row)])
        (k/exec-raw
         ["SELECT users.given_name || ' ' || users.family_name AS instructor,
                  users.id AS instructor_id
             FROM vc_user_role
       INNER JOIN vc_user AS users 
               ON users.id = vc_user_role.user_id
            WHERE role = 'teacher'
         ORDER BY users.family_name,users.given_name" []] :results))])

;; TODO: use html/tablize rather than this custom function.
(defn profile-table [ target & [game]]
  (let [target-long (language-to-root-keyword target)
        verbs (map :verb
                   (k/exec-raw
                    [(str
                      "SELECT DISTINCT 
                             structure->'root'->?->>? AS verb 
                        FROM expression 
                       WHERE (structure->'root'->?->?) IS NOT NULL 
                    ORDER BY verb ASC")
                           [target-long target-long
                            target-long target-long] ] :results))]
    [:table.profile

     ;; top row: show all tenses
     [:tr
      [:th "&nbsp;"]
      (map (fn [tense]
             [:th [:div tense]])
           tenses)]
     
     ;; one row per verb.
     (map (fn [verb]
            (let [profiles-for-verb
                  (map (fn [tense]
                         (merge {:tense tense}))
                       tenses)]
              (if (or true (not (empty? (filter #(and (not (nil? %))
                                             (> (:count %) 0))
                                       profiles-for-verb))))
                [:tr
                 [:th.verb verb]
                 (map (fn [in-profile]
                        (let [tense (string/replace-first (:tense in-profile) ":" "")

                              ;; TODO: validate dom-id or use a uuid or something rather than
                              ;; munging together dom id names.
                              dom-id (str "profile-verb-" verb "-tense-" tense)

                              ]
                              
                          [:td

                           [:span {:id dom-id
                                   :class "fa fa-spinner fa-spin"
                                   }
                                 
                            [:script {:type "text/javascript"}
                             (str "profile_verb_and_tense(" 
                                  "'" dom-id "'," 
                                  "'" target "'," 
                                  "'" verb "'," 
                                  "'" tense "');")
                             ]]

                           
                           (if (or true (and in-profile
                                             (> (:count in-profile) 0)))
                             [:div.info
                              [:div (:ttcr in-profile)]
                              [:div (:count in-profile)]]
                             " &nbsp; ")]))
                      profiles-for-verb)])))
          verbs
          )
     ]))

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

;; TODO: use html/tablize rather than this custom function.
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
          LIMIT 20"

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
       [:th "Time"]
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
            [:td {:class (str "profile " profile)}
             (:ttcr result)]]))
       results)])))
