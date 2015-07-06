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
           (page "My page" (me request)
                 request
                 {:onload "me_onload();"
                  :css ["/css/me.css"]
                  :jss ["/js/me.js"]
                  })})

     (GET "/:verb/:tense" request
          (let [verb (:verb (:route-params request))
                tense (:tense (:route-params request))
                debug (log/debug (str "tense: " tense))
                debug (log/debug (str "verb: " verb))
                response
                ;; TODO: generalize beyond en -> it.
                (get-in-profile verb (keyword tense) "en" "it")]
            {:body (write-str response)
             :status 200
             :headers headers})))))

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
               avg(question.time_to_correct_response) AS ttcr
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
    
    (profile-table)
    
    ]
   
   [:div#last {:class "major"}
    [:h2 "Latest questions"]
    (latest-questions)      
    ]
   ])

(defn profile-table [ & [game]]
  (let [verbs (map :verb (k/exec-raw
                          [(str "SELECT DISTINCT 
                             structure->'root'->'italiano'->>'italiano' AS verb 
                        FROM expression 
                       WHERE (structure->'root'->'italiano'->'italiano') IS NOT NULL 
                    ORDER BY verb ASC")] :results))]
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
                         (merge {:tense tense}

                                ;; TODO: only works for en->it: generalize.
                                ;;   (get-in-profile verb (keyword tense) "en" "it")))
                                ))
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
                             (str "profile_verb_and_tense('"
                                  dom-id "','"
                                  verb "','"
                                  tense "');")
                             ]]

                           
                           (if (or true (and in-profile
                                             (> (:count in-profile) 0)))
                             [:div.info
                              [:div (:ttcr in-profile)]
                              [:div (:count in-profile)]]
                             " &nbsp; ")]))
                      profiles-for-verb)])))
          verbs)
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
