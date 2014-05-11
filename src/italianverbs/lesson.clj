(ns italianverbs.lesson
  (:use [hiccup core])
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [italianverbs.db :as db]
   [italianverbs.morphology :refer (normalize-whitespace)]))

;; "I just realized on my way home that besides the time stamp teachers
;; should be allowed to enter their verbs in sets, sort of like the
;; flashcards in quizlet, with set names. This is because most teachers
;; use textbooks and textbooks are divided in chapters or units. So
;; teachers may like to be ablo to tell the system something
;; like: "create a test with 15 verbs from units 4 and 5" rather
;; than "create a test with 15 verbs entered between 1/11 and 4/21".  If
;; it is possible to do both a time stamp and a tag or name of set that
;; would be ideal."

(defn create-a-new-tag []
  [:div {:class "create"}

   [:form {:method "post" :action "/lesson/new"}
    [:table
     [:tr
      [:th "Create a new tag"]
      [:td
       [:input {:name "tag"}]]
      ]
     ]
    ]
   ])

;; TODO
(defn urlencode [string]
  string)

(defn delete [tag]
  (log/info (str "deleting tag: " tag))
  (db/fetch-and-modify :tag {:_id (db/object-id tag)} {} :remove? true))

(defn delete-from-tag [tag verb]
  (let [result (first (db/fetch :tag :where {:_id (db/object-id tag)}))]
    (db/fetch-and-modify :tag {:_id (db/object-id tag)} {:name (:name result)
                                                         :verbs (remove #(= verb %) (:verbs result))})))

(defn tr-result [results]
  (if (not (empty? results))
    (str (html [:tr 
                [:td [:a {:href (str "/lesson/" (:_id (first results))"/") } (:name (first results))]]
                [:td 
                 (if (:verbs (first results))
                   (.size (:verbs (first results)))
                   0)]
                [:td {:class "edit"}
                 [:form {:method "post" :action (str "/lesson/delete/" (db/primary-key (first results)))}
                  [:button {:onclick "submit()"} "delete"]]]])
         (tr-result (rest results)))
    ""))

(defn tr-verbs [tag results]
  (if (not (empty? results))
    (str (html [:tr 
                [:td (first results)]
                [:td {:class "edit"}
                 [:form {:method "post" :action (str "/lesson/" tag "/delete/" (first results))}
                  [:input {:type "hidden" :name "tag" :value (db/primary-key (first results))}]
                  [:button {:onclick "submit()"} "delete"]]]])
         (tr-verbs tag (rest results)))
    ""))

(defn show-tags []
  (let [script "/* js goes here.. */"]
    (html
     [:div {:class "major tag"}
      [:h2 "Groups"]

      [:table
       [:tr
        [:script script]
        [:th "Name"]
        [:th "Verbs"]
        [:th {:class "edit"} "Edit"]]
       
       (let [results (db/fetch :tag)]
         (tr-result results))
       
       ]
      (create-a-new-tag)
      ])))

(defn lesson [session request]
  (html
   (show-tags)))

(defn tr-lesson [lesson]
  (log/info (str "tr-lesson: " lesson)))

(defn add-a-new-verb [tag]
  [:div {:class "create"}

   [:form {:method "post" :action (str "/lesson/" tag "/new")}
    [:table
     [:tr
      [:th "Add a new verb:"]
      [:td
       [:input {:name "verb"}]]
      ]
     ]
    ]
   ])

(defn add-to-tag [tag other-params]
  (log/info (str "add-to-tag: " tag))
  (let [verb (:verb other-params)
        result (first (db/fetch :tag :where {:_id (db/object-id tag)}))
        verbs-of-tag (:verbs result)]
    (log/info (str "adding verb: " verb " to tag: " tag))
    (log/info (str "  current verbs: " verbs-of-tag))
    (db/fetch-and-modify :tag {:_id (db/object-id tag)} {:name (:name result)
                                                         :verbs (conj verbs-of-tag verb)})))

(defn show [session tag]
  (let [script "/* js goes here.. */"
        results (db/fetch :tag :where {:_id (db/object-id tag)})
        result (first results)]
    (html
     [:div {:class "major tag"}
     [:h2 [:a {:href "/lesson/" } "Groups"] " &raquo; "  (:name result)]

      [:table
       [:tr
        [:script script]
        [:th "Verb"]
        [:th {:class "edit"} "Edit"]]
       
       (log/info (str "show: tag: " result))
       (tr-verbs tag (:verbs result))

       ]

      (add-a-new-verb tag)

      ])))

(defn validate-new-tag [tag]
  "see whether _tag_ is good to add to the db."
  true)

(defn add-new-tag [tag]
  (db/insert! :tag {:name (normalize-whitespace tag)}))

(defn new [session request]
  (log/debug (str "/lesson/new with request: " (:form-params request)))

  (if (get (:form-params request) "tag")
    (let [new-tag (get (:form-params request) "tag")]
      (if (validate-new-tag new-tag)
        (do (log/info (str "Adding validated candidate tag: " new-tag))
            (add-new-tag new-tag)))))

  (html
   [:div {:class "major"}
    [:h2 [:a {:href "/lesson"} "tags"]]
    (show-tags)
    (create-a-new-tag)
    ]))

