(ns italianverbs.editor
  (:require
   [cemerick.friend :as friend]
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.auth :as auth]
   [italianverbs.english :as en]
   [italianverbs.html :as html]
   [italianverbs.italiano :as it]
   [italianverbs.morphology :refer (normalize-whitespace)]
   [italianverbs.korma :as db]
   [hiccup.core :refer (html)]

   ))

(declare control-panel)

(defn create [request])
(defn create-form [request])
(defn read [request])
(defn update [request])
(defn update-form [request])
(defn delete [request])
(defn delete-form [request])
(defn list [request])

(declare onload)

(defn routes []
  (compojure/routes
   (GET "/gen" request
        (let [do-generation (fn []
                              {:body (html/page 
                                      "Editor: Generation" 
                                      (control-panel request
                                                     (auth/haz-admin))
                                      request
                                      {:css "/css/settings.css"
                                       :js "/js/gen.js"
                                       :onload (onload)})
                               :status 200
                               :headers {"Content-Type" "text/html;charset=utf-8"}})]
                              
        (if false ;; TODO: define config variable workstation-mode.
          (friend/authorize #{::admin} do-generation)
          ;; turn off security for workstation dev
          (do-generation))))

   (GET "/gen/" request
        {:status 302
         :headers {"Location" "/editor/gen"}})

   (GET "/create" request
        (create-form request))
   (POST "/create" request
        (create request))

   (GET "/read" request
        (read request))

   (GET "/update" request
        (update-form request))
   (POST "/update" request
        (update request))

   (GET "/delete" request
        (delete-form request))
   (POST "/delete" request
        (delete request))))

(def generate-this-many-at-once 10)

;; see core.clj: (GET "/gen" request
(defn onload []
  (str "gen('examples',1," generate-this-many-at-once "); gen_per_verb();")) ;; javascript to be executed at page load.

(declare table-of-examples)

(defn control-panel [request haz-admin]
  (let [current-size "5,436"
        desired-size "10,000"]
    (html
     [:div#generation {:class "major"}
      [:h2 "Generation"]

;      [:div
;       [:button "Update"]]
      
      [:div#vocabulary
       [:h3 "Lexicon"]

       [:div#verbs 
        [:h4 "Verbs"]

        [:table 

          [:tr
           
           [:th ""]
           [:th {:style "width:10em"} "Italian"]
           [:th {:style "width:20em"} "Example"]

;           [:th {:style "width:10em"} "Semantics"]
           [:th {:style "width:10em"} "English"]
           [:th {:style "width:20em"} "Translation"]
           [:th {:style "width:3em"} ""]
           ]

         (map (fn [lexeme]
                [:tr.lexeme
                 
                 [:td
                  [:input {:type "checkbox"} ]]

                 [:td lexeme ]
                 [:td.example
                  [:div.gen_source {:id (str "verb_" lexeme)}  [:i {:class "fa fa-spinner fa-spin"} "" ] ]]

;                 [:td.semantics {:id (str "semantics_" lexeme)} [:i {:class "fa fa-spinner fa-spin"} "" ] ]

                 [:td {:id (str "english_verb_" lexeme)}  [:i {:class "fa fa-spinner fa-spin"} "" ] ]

                 [:td {:id (str "english_translation_" lexeme)} [:i {:class "fa fa-spinner fa-spin"} "" ]  ]

                 [:td [:i {:class "fa fa-refresh"} "" ] ]

                 ])
         
              (let [all-verbs
                    (filter (fn [lexeme]
                              (not (empty?
                                    (filter (fn [lex]
                                              (and
                                               (= :top (get-in lex [:synsem :infl]))
                                               (or true (= :bere (get-in lex [:synsem :sem :pred])))
                                               (= :verb
                                                  (get-in lex [:synsem :cat]))))
                                            (get @it/lexicon lexeme)))))
                            (sort (keys @it/lexicon)))]
                all-verbs))]]

       [:div#noun
        [:h4 "Nouns and Pronouns"]
        [:table

         (map (fn [lexeme]
                [:tr 
                 [:th [:input {:type "checkbox"}]]
                 [:td lexeme]])
              (filter (fn [lexeme]
                        (not (empty?
                              (filter (fn [lex]
                                        (= :noun
                                           (get-in lex [:synsem :cat])))
                                      (get @it/lexicon lexeme)))))
                      (sort (keys @it/lexicon))))
         ]
        ]

       [:div#dets
        [:h4 "Determiners"]
        [:table

         (map (fn [lexeme]
                [:tr 
                 [:th [:input {:type "checkbox"}]]
                 [:td lexeme]])
              (filter (fn [lexeme]
                        (not (empty?
                              (filter (fn [lex]
                                        (= :det
                                           (get-in lex [:synsem :cat])))
                                      (get @it/lexicon lexeme)))))
                      (sort (keys @it/lexicon))))
         ]
       ]
       ]

      [:div#inflections
       [:h3 "Inflections"]
       [:table

        (map (fn [infl]
               [:tr 
                [:th [:input {:type "checkbox"}]]
                [:td infl]])
             ["Condizionale"
              "Imperfetto"
              "Presente"
              "Futuro"
              "Passato Prossimo"])]
        ]


      [:div#examples
       [:h3 "Examples"] ;; see (defn onload)
                                 
         [:table
          [:tr
           [:th]
           [:th "English"]
           [:th "Italiano"]
           ]
          (table-of-examples 1 generate-this-many-at-once)
          ]
       ]

      [:div#currentsize
       [:h3 "Corpus Size" ]
       [:table
        [:tr
         [:th "Current"]
         [:td current-size]]
        [:tr
         [:th "Desired"]
         [:td [:input {:value desired-size}]]]]]

      ]
    ))) 

(defn table-of-examples [index upto]
  (if (<= index upto)
    (html
     [:tr
      [:th (str index)]
      [:td {:id (str "example_q_" index)}]
      [:td {:id (str "example_a_" index)}]]
     
     (table-of-examples (+ 1 index) generate-this-many-at-once))))
