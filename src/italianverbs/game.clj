(ns italianverbs.game
  (:refer-clojure :exclude [get-in merge])
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clojure.tools.logging :as log]

   [hiccup.page :refer (html5)]

   [italianverbs.cache :refer (create-index)]
   [italianverbs.morphology :refer [fo fo-ps remove-parens]]
   [italianverbs.translate :refer [get-meaning]]
   [italianverbs.ug :refer (head-principle)]
   [italianverbs.unify :refer [get-in merge strip-refs]]

   [italianverbs.english :as en]
   [italianverbs.italiano :as it]))

(def game-pairs
  [{:source "en"
    :destination "it"
    :source_flag "/svg/britain.svg"
    :destination_flag "/svg/italy.svg"}])

(def possible-preds [:bere :dormire :leggere :mangiare :parlare])

(defn direction-chooser []
  (html5
   [:dev#chooser
    [:select {:style "display:none" :disabled "true" };; TODO: not working yet, so disabled.
     [:option {:onclick "location='/cloud?src=en&dest=it';"}
      "en -> it"
      ]
     
     [:option {:onclick "location='/cloud?src=it&dest=en';"}
      "it -> en"
      ]

     [:option {:onclick "location='/cloud?src=en&dest=es';"}
      "en -> es"
      ]

     [:option {:onclick "location='/cloud?src=es&dest=en';"}
      "es -> en"
      ]
     ]]))

(defn game []
  (html5
   [:div#game

    [:svg {:id "svgarena"}]

    [:div#rainforest

     [:div#wordbar

      [:div#q1 "wordbar"]

      [:div#q2 "not used"]

      [:div#q3 "yet"]

      ]
     
     (direction-chooser)
     
     [:div#score
      "Score:"
      [:span#scorevalue
       "0"
       ]
     ]

     [:div#sky ]

     [:div#correction_dialog {:style "display:none"}

      [:form {:onsubmit "return false;"}
       [:div#cd_left_context_of_answer {:class "correct_answer" } "" ]
       [:div#cd_rca {:class "correct_answer" } "" ]
       [:h3#correct_answer "" ]
       [:div#full_question {:class "question"} " " ]
       [:input {:id "correction_bare_id" :type "hidden"}]
       ]
      ;; end of :form

      ] ;; end of :div #correction_dialog



     [:div#ground
      


      ] ;; end of :div#ground

     ] ;; end of :div#rainforest

    [:div#gameform

      [:input {:id "game_input" :size "50"}]
     
      [:button#answer_button {:class "click;float:right;width:auto"
                              :onclick "submit_game_response('game_input'); event.preventDefault(); return false;"} "Answer" ]
     
      ] ;; end of :div #gameform
    ] ;; end of :div #game
) ; html5/div

) ;; end of (defn)

(defn evaluate [user-response]
  (let [params (:form-params user-response)]
    (log/info (str "keys: " (keys params)))
    (let [guess (get-in params '("guess"))]
      (log/info (str "guess: " (get-in params '("guess"))))
      (str "clojure saw your response:" guess))))

(defn map-realize [the-fn seq]
  (if (not (empty? seq))
    (cons (apply the-fn (list (first seq)))
          (map-realize the-fn (rest seq)))))

(defn html-form [question]
  (do
    (log/info (str "html-form: question: " (fo question)))
    {:left_context_source (remove-parens (fo (get-in question [:comp])))
     :head_of_source (remove-parens (fo (get-in question [:head])))
     :right_context_source ""
     :right_context_destination ""}))

(def mini-en-grammar
  (filter #(= (:rule %) "s-present")
          en/grammar))

(def mini-en-index (create-index mini-en-grammar (flatten (vals en/lexicon)) head-principle))

(def mini-it-grammar
  (filter #(= (:rule %) "s-present")
          it/grammar))

(def mini-it-index (create-index mini-it-grammar (flatten (vals it/lexicon)) head-principle))

(def source-language-generate en/generate)
(def source-language-grammar mini-en-grammar)
(def source-language-index mini-en-index)

(def target-language-generate it/generate)
(def target-language-grammar mini-it-grammar)
(def target-language-index mini-it-index)

(defn generate-question [request]
  (let [pred (nth possible-preds (rand-int (.size possible-preds)))
        spec
        {:head {:phrasal :top}
         :comp {:phrasal false}
         :synsem {:sem {:pred pred}
                  :cat :verb
                  :subcat '()}}
        question (source-language-generate
                  spec {:grammar source-language-grammar
                        :index source-language-index})
        form (html-form question)]

    (log/info "generate-question: question: " (fo question))

    {:status 200
     :headers {"Content-Type" "application/json;charset=utf-8"
               
               "Cache-Control" "no-cache, no-store, must-revalidate"
               "Pragma" "no-cache"
               "Expires" "0"}
     :body (json/write-str
            {:left_context_of_question (:left_context_source form)
             :full_question (fo question)
             :question (:head_of_source form)
             :right_context_of_question (:right_context_source form)
             :semantics (strip-refs (get-meaning question))})}))

;; This is the counterpart to (generate-questions) immediately above: it generates
;; an expression that the user should be learniong.
(defn generate-answers [request]
  "generate a single sentence according to the semantics of the request."
  (log/info (str "generate-answers: request semantics: " (get-in request [:params :semantics])))
  (log/info (str "cloud_id: " (get-in request [:params :cloud_id])))
  (let [semantics (json/read-str (get-in request [:params :semantics])
                                 :key-fn keyword
                                 :value-fn (fn [k v]
                                            (cond (string? v)
                                                  (keyword v)
                                                  :else v)))
        debug (log/debug (str "semantics: " semantics))

        more-constraints {:synsem {:subcat '()}
                          :head {:phrasal :top}
                          :comp {:phrasal false}}


        to-generate (merge semantics more-constraints)

        debug (log/info (str "to-generate: " to-generate))

        ;; TODO: for now, we are hard-wired to generate an answer in Italian,
        ;; but function should accept an input parameter to determine which language should be
        ;; used.
        answer (target-language-generate
                to-generate
                {:grammar target-language-grammar
                 :index target-language-index})

        ;; used to group questions by some common feature - in this case,
        ;; we'll use the pred since this is a way of cross-linguistically
        ;; grouping verbs with the same meaning together.
        group_by (get-in answer [:synsem :sem :pred])

        debug (log/info (str "group_by: " group_by))
        ]
    
    {:status 200
     :headers {"Content-Type" "application/json;charset=utf-8"}
     :body
     (json/write-str
      {:cloud_id (get-in request [:params :cloud_id])
 
       :group_by group_by
       :left_context_of_answer (remove-parens (fo (get-in answer [:comp])))
       ;; here we combine all of the possible answers,
       ;; using punctuation within the string as means of delimitation (a comma).
       ;; TODO: use a JSON array instead, and also, remove the use of (remove-parens),
       ;; since this is another way that punctuation being abused to serialize data structures.
       :answer (string/join ","
                            (list 
                             (remove-parens (fo (get-in answer [:head])))
                             (remove-parens (fo answer))))
       :semantics semantics
       :right_context_of_answer ""})}))

(defn tour []
  (html5
   [:h3 {:style "background:lightgreen;padding:0.25em"} "Benvenuto a l'Italia!"]

   [:div#game

    [:svg {:id "svgarena"}]

    [:div#rainforest

     [:div#wordbar

      [:div#q1 "wordbar"]

      [:div#q2 "not used"]

      [:div#q3 "yet"]

      ]
     
     (direction-chooser)
     
     [:div#kilos
      "Kilometri:"
      [:span#scorevalue
       "0"
       ]
     ]

     [:div {:style "z-index:2"}
      

      [:div#map {:style "height:400px;"}  ]


      ]

     [:div#correction_dialog {:style "display:none"}

      [:form {:onsubmit "return false;"}
       [:div#cd_left_context_of_answer {:class "correct_answer" } "" ]
       [:div#cd_rca {:class "correct_answer" } "" ]
       [:h3#correct_answer "" ]
       [:div#full_question {:class "question"} " " ]
       [:input {:id "correction_bare_id" :type "hidden"}]
       ]
      ;; end of :form

      ] ;; end of :div #correction_dialog


     ] ;; end of :div#rainforest

    [:div#gameform


     [:div#tourquestion
      ""
      ]

     [:input {:id "game_input" :size "30"}]
     
     [:button#answer_button {:class "click;float:right;width:auto"
                             :onclick "submit_tour_response('game_input'); event.preventDefault(); return false;"} "Answer" ]
     
     ] ;; end of :div #gameform
    ] ;; end of :div #game
) ; html5/div

) ;; end of (defn)

