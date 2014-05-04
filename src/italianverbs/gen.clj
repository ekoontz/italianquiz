(ns italianverbs.gen
  (:use [hiccup core])
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [italianverbs.html :as html]
   [italianverbs.lesson :as lesson]
   [somnium.congomongo :as db])) ;; TODO: provide database abstraction over mongo and other possible backing stores.


(defn tr-result [results]
  (if (not (empty? results))
    (str (html [:tr 
                [:td [:a {:href (str "/generate/" (:_id (first results))"/") } (:name (first results))]]
                [:td {:class "number"}
                 (if (and (:verbs (first results))
                          (> (.size (:verbs (first results))) 0))
                   [:a {:href (str "/generate/" (:_id (first results))"/") } (str (.size (:verbs (first results))))]
                   "0")]])
         (tr-result (rest results)))
    ""))

(defn generate [session request]
  (html
   [:div {:class "major gen"}
    [:h2 "Generate"]
    [:table
     [:tr
      [:th "Tag"]
      [:th {:class "number"} "Verbs"]
      ]

     (let [results (db/fetch :tag)]
       (tr-result results))
     ]]))

(defn tr-verbs [tag results]
  (if (not (empty? results))
    (str (html [:tr 
                [:td (first results)]])
         (tr-verbs tag (rest results)))
    ""))

(defn generate-from [tag]
  (let [map-of-tag (first (db/fetch :tag :where {:_id (db/object-id tag)}))
        tag (:name map-of-tag)
        verbs (:verbs map-of-tag)]
    (html
     [:div {:class "major"}
      [:h2 (str "Generate" " &raquo; " tag)]

      [:table
       [:tr
        [:th "Verb"]
        ]
       
       (tr-verbs tag verbs)

       ]




      ])))

(defn page [header body]
  (html/page header body {:uri "/generate/"}))
