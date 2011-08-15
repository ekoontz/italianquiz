;; Seems like you need to restart ring to see changes to this file.
(ns italianverbs.quiz
    (:use 
     [hiccup core page-helpers]
     [somnium.congomongo])
    (:require [clojure.contrib.string :as stringc]
              [italianverbs.lexicon :as lexicon]
              [italianverbs.lev :as lev]
              [italianverbs.session :as session]
              [italianverbs.grammar :as gram]
              [italianverbs.html :as html]
              [italianverbs.generate :as gen]))

(def all-possible-question-types
  '(:mobili :mese :giorni :possessives :partitivo :ora :infinitivo :passato :presente :espressioni))

(defn per-user-correct [questions]
  "count of all correctly-answered questions for all session."
  (reduce
   (fn [x y]
     (+ x y))
   (map (fn [x] (if (= (get x :answer) (get x :guess)) 1 0))
        questions)))

(defn per-user-incorrect [questions]
  "count of all incorrectly-answered questions for all session."
  (reduce
   (fn [x y]
     (+ x y))
   (map (fn [x] (if (not (= (get x :answer) (get x :guess)))
                  (if (= nil (get x :guess))
                    0 1)
                  0))
        questions)))

(defn per-user-total [questions]
  "count of all incorrectly-answered questions for all session."
  (count questions))

(defn wrapchoice [word & [ istrue ] ]
  ;; FIXME: url-encode word.
  (let [href_prefix "/quiz/?"
;        english word]
        english (get word :english)]
       (html [:div {:class "guess"}
	       [:h3 [:a {:href (str href_prefix "guess=" english)} english]
  	       (if istrue [:i.debug true])
	       ]])))

(defn show-choice [lexicon remaining answer show-true-before]
  (if (>= remaining 0)
      (str
       (if (= show-true-before remaining)
	   (wrapchoice answer true))
       (if (> remaining 0)
	   (let [choice (rand-int (count lexicon))]
		(str 
		 (html [:div.debug (str "remaining: " remaining)])
		 (wrapchoice (get lexicon (nth (keys lexicon) choice)))
		 (show-choice (dissoc lexicon (nth (keys lexicon) choice)) (- remaining 1)
			answer show-true-before)))))))

(defn get-next-question-id [request]
  "get the question id for the next question for this user."
  ;; TODO: improve: only return last question and get its _id.
  (let [session (session/request-to-session request)]
    (count (fetch :question :where {:session session}))))

(defn normalize-whitespace [string]
  (stringc/replace-re #"[ ]+$" "" (stringc/replace-re #"^[ ]+" "" (stringc/replace-re #"[ ]+" " " string))))

(defn store-question [question request last-guess]
  (insert! :question {:question (normalize-whitespace (get question :english))
                      :answer (normalize-whitespace (get question :italian))
                      :id (get-next-question-id request)
                      :keys (str (keys question))
                      :cat (get question :cat)
                      :guess last-guess
                      :gender (get question :gender)
                      :italian (get question :italian)
                      :english (get question :english)
                      :session request}))

(defn clear-questions [session]
  (destroy! :question {:session session})
  session)

(defn set-filters [session request]
  (do
    (destroy! :filter {:session session})
    (insert! :filter {:form-params (get request :form-params)
                      :session session})
    session))


(defn each-correct [question]
  (if (= (get question :guess) (get question :answer)) '(true) nil))

(defn highlight-green [guess green index]
  "update letters in the guess that are correct."
  (if (> (.size guess) 0)
    (if (= (first green)
           index)
       (str
        (str "<span class='c'>" (first guess) "</span>")
        (highlight-green (rest guess) (rest green) (+ index 1)))
       (str
        (str "<span class='i'>" (first guess) "</span>")
        (highlight-green (rest guess) green (+ index 1))))))

(defn highlight-green2 [green2 index]
  (let [char (if (= (get (first green2) :action) "delete")
               (get (first green2) :truth)
               (get (first green2) :test))
        style (get (first green2) :action)]
    (if (> (.size green2) 0)
      (str
       "<span class='" style "'>" char "</span>"
       (if (= style "subst")
         (str "<span class='delete'>" (get (first green2) :truth) "</span>"))
       (highlight-green2
        (rest green2) (+ index 1))))))

(defn show-history-rows [qs count hide-answer total]
  (if (first qs)
    (let
        [row (first qs)
         distance-from-top
         (str "dist"
              (if (< (- total count) 5)
                (if (= (- total count) 0)
                  (str (- total count) " debug")
                  (- total count))
                "n"))
         correctness (if (and (get row :guess) (not (= (get row :guess) "")))
                       (if (= (get row :answer) (get row :guess))
                         "correct"
                         "incorrect"))]
      (html
       [:tr
        {:class (str distance-from-top
                     (if (= (mod count 2) 1)
                       " odd"))}
        [:td {:rowspan "2"} (get row :question)] ]

       [:tr
        {:class (str distance-from-top
                     (if (= (mod count 2) 1)
                       " odd"))}
;        [:td {:class "eval"}
;         (if (get row :green)
;           (str (highlight-green
;                     (lev/explode (get row :guess))
;                     (get row :green) 0))
;           (str "" (get row :guess)   ))]

        [:td {:class "eval"}
         (if (get row :green2)
           (str (highlight-green2
                     (get row :green2) 0))
           (str "" (get row :guess)   ))]


        ]
       (if (not (= (get row :answer)
                   (get row :guess)))
                 
         [:tr
          {:class (str distance-from-top
                       (if (= (mod count 2) 1)
                         " odd"))}
          [:td "" ]
          [:td {:class "answer"} (if (= hide-answer false) (first (rest qs)) (get row :answer))]
          ])
       
       (show-history-rows (rest qs) (- count 1) true total)))))

(defn store-guess [guess question]
  "update question # question id with guess: a rewrite of (evaluate-guess)."
  (let [guess
        (normalize-whitespace guess)]
    (update! :question question (merge question {:guess guess
                                                 :green2
                                                 (if (and guess
                                                          (> (.length guess) 0))
                                                   (lev/get-green2 (get question :answer)
                                                                   guess))
                                                 :green (if (and guess
                                                                 (> (.length guess) 0))
                                                          (lev/get-green (get question :answer)
                                                                         guess))}))))

(defn generate [question-type]
  "maps a question-type to feature structure. right now a big 'switch(question-type)' statement (in C terms)."
  (cond
   (= question-type :espressioni)
   (gen/espressioni)
   (= question-type :infinitivo)
   (gen/random-infinitivo)
   (= question-type :passato)
   (gen/random-passato-prossimo)
   (= question-type :presente)
   (gen/random-present)
   (= question-type :pp)
   (gram/pp
    {:$or [ {:italian "a"}, {:italian "di" }, {:italian "da"},
            {:italian "in" :english "in"}, {:italian "su"} ]}
    (gram/np-with-post-conditions
     {}
     gram/np-with-common-noun-and-definite-pronoun))
   (= question-type :partitivo)
   (gram/np {:number :plural
             :pronoun {:$ne true}}
            (gram/choose-lexeme {:def :part}))
   (= question-type :ora)
   (let [hour (rand-int 12)
         minute (* (rand-int 12) 5)
         ampm (if (= (rand-int 2) 0)
                "am"
                "pm")
         hour (if (= hour 0) 12 hour)]
    {:english (gram/english-time hour minute ampm)
     :italian (gram/italian-time hour minute ampm)})
   (= question-type :mobili)
   (gen/mobili)
   (= question-type :possessives)
   (let [fn gram/np-det-n-bar
         head
         (let [fn gram/n-bar
               head (gram/choose-lexeme
                     {:cat :noun
                      :common true
                      :number :singular})
               comp (gram/choose-lexeme
                     {:cat :adj
                      :gender (get head :gender)
                      :possessive true})]
           (merge {:test "possessive NPs"}
                  (apply fn (list head comp))))
         comp (gram/choose-lexeme
               {:cat :det
                :gender (get head :gender)
                :number (get head :number)
                :def :def})]
     (apply fn (list head comp)))
   (= question-type :mese)
   (gram/choose-lexeme {:month true})
   (= question-type :giorni)
   (gram/choose-lexeme {:giorni-della-settimana true})
   true
   (gram/sentence)))

(defn controls [session]
  (let [checked (fn [session key]
                  "return 'checked' if checkbox with key _key_ is set to true according to user's preferences."
                  (let [record (fetch-one :filter :where {:session session})
                        filters (if record
                                  (get record :form-params))]
                    (if (get filters key)
                      {:checked "checked"}
                      {})))
        checkbox-row (fn checkbox-row [name key session & [label display checkbox-disabled]]
                       (let [label (if label label name)]
                         (html
                          [:tr {:style (if display (str "display:" display))}
                           [:th
                            [:input (merge {:onclick "submit()" :name name :type "checkbox"}
                                           (if (= checkbox-disabled "disabled") {:disabled "disabled"} {})
                                           (checked session key))]]
                           [:td label ] ] )))
        checkbox-col (fn checkbox-col [name key session & [label display checkbox-disabled]]
                       (let [label (if label label name)]
                         (html
                          [:th
                           [:input (merge {:onclick "submit()" :name name :type "checkbox"}
                                          (if (= checkbox-disabled "disabled") {:disabled "disabled"} {})
                                          (checked session key))]]
                          [:td label ] )))]
    [:div {:id "controls" :class "controls quiz-elem"}
     [:h2 "Controls"]
     [:form {:method "post" :action "/quiz/filter" :accept-charset "iso-8859-1" }
      [:table
       [:tr
        (checkbox-col "ora" :ora session "Che ora è?")  ;; e.g. "5:30 => cinque ore e .."
        (checkbox-col "giorni" :giorni session "giorni della settimana")
        (checkbox-col "mobili" :mobili session)
        ]
       [:tr
        (checkbox-col "preposizioni" :preposizioni session "preposizioni" "none")
        (checkbox-col "partitivo" :partitivo session "articoli determinativi e partivi")
        (checkbox-col "mese" :mese session "le mese")
        ]
       [:tr
        (checkbox-col "numeri" :numeri session "numeri" "" "disabled") ;; e.g. "6.458 => sililaquattrocentocinquantotto"
        (checkbox-col "possessives" :possessives session) ;; e.g. "il tuo cane"
        (checkbox-col "espressioni" :espressioni session "espressioni utili") ;; e.g. "il tuo cane"
        ]
       ]
      [:div {:class "optiongroup"}
       [:h4 "Verbi"]
       [:table
        [:tr
         (checkbox-col "passato" :passato session "passato prossimo")  ;; e.g. "io ho fatto"
         (checkbox-col "presente" :presente session "presente indicativo" "")  ;; e.g. "io vado"
         (checkbox-col "infinitivo" :infinitivo session "infinitivo")  ;; e.g. "fare"
         ]
        ]      
       ]
      ]
     
     ;; at least for now, the following is used as empty anchor after settings are changed via controls and POSTed.
     [:div {:id "controlbottom" :style "display:none"} 
      " "
      ]
   ]))

(defn with-history-and-controls [session content]
  (let [questions (fetch :question :where {:session session})]
    [:div
     (controls session)
     content
     [:div {:class "history quiz-elem"}
      [:h2 "History"]
      
      
      [:div {:style "float:right"}
       [:form {:method "post" :action "/quiz/clear"}
        [:input.submit {:type "submit" :value "clear"}]]]
      
      [:table
       {:class "history"}
       [:thead
        ]
       [:tbody
        (show-history-rows (fetch :question :where {:session session} :sort {:_id -1})
                           (count (fetch :question :where {:session session}))
                           false
                           (count (fetch :question :where {:session session}))
                           )
        ]
       ]]
     
     [:div {:class "quiz-elem stats"}
      [:h2 "Stats"]
      [:table
       [:tr
        [:th "Correct"]
        [:td (per-user-correct questions)]
        ]
       
       [:tr
        [:th "Incorrect"]
        [:td (per-user-incorrect questions)]
        ]
       
       [:tr
        [:th "Total"]
        [:td (per-user-total questions)]
        ]
       
       ]
      ]
     
     ]
    ))

(defn filter-by-criteria [list criteria]
  (if (> (count list) 0)
    (if (get criteria (first list))
      (cons (first list)
            (filter-by-criteria (rest list) criteria))
      (filter-by-criteria (rest list)
                           criteria))))

(defn possible-question-types [session]
  (let [possible-question-types all-possible-question-types
        record (fetch-one :filter :where {:session session})
        filters (if record
                  (get record :form-params))
        result (filter-by-criteria possible-question-types filters)]
    (if (> (count result) 0)
      result
      all-possible-question-types)))
        
    
(defn quiz [last-guess request]
  "choose a question type from the set of question types possible given the user's preference, and
   and then generate a question for that question type."
  (let [session (session/request-to-session request)
        ;; normalize guess: remove space from beginning and end, and lower-case.
        last-guess (if last-guess (stringc/trim (stringc/lower-case last-guess)))
        get-next-question-id (get-next-question-id request)
        possible (possible-question-types (session/request-to-session request))
        next-question
        ;; TODO: next-question is two totally different things depending on the (if) - it's confusing.
        (if (or last-guess
                (= get-next-question-id 0))
          (generate (nth possible (rand-int (count possible)))))]
    
    (if last-guess (store-guess last-guess
                                (nth (fetch :question :where {:session session} :sort {:_id -1} :limit 1) 0)))
    
    (if (or last-guess
            (= get-next-question-id 0))
      (store-question next-question (session/request-to-session request) last-guess))
    
    (html
     (with-history-and-controls
       (session/request-to-session request)
       [:div {:class "quiz quiz-elem"}
        [:h2 (str "Domanda" " "
                  (if (or last-guess
                          (= get-next-question-id 0))
                    (+ 1 get-next-question-id)
                    get-next-question-id))]
        [:form {:name "quiz" :method "post" :action "/quiz/" :accept-charset "UTF-8"}
         [:table
          [:tr
           [:td [:h1 
                 (if (or last-guess
                         (= get-next-question-id 0))
                     (get next-question :english)
                     (str (get (nth (fetch :question :where {:session session} :sort {:_id -1} :limit 1) 0)
                               :question)))]]]
            [:tr
             [:td
              [:textarea {:cols "50" :rows "2" :name "guess" }]]]]
         [:div
          {:style "float:right;padding:1em"
           }
          [:input.submit {:type "submit" :value "riposta"}]]]]))))

(defn url-decode [string]
  (.replaceAll string "(%20)" " "))

(defn get-params [pairs]
  (if (first pairs)
      (let [keyval (re-seq #"[^=]+" (first pairs))]
	   (merge 
	    {(first keyval) (url-decode (second keyval))}
	    (get-params (rest pairs))))
    {}))

(defn get-param-map [query-string]
  (if query-string
      (get-params (re-seq #"[^&]+" query-string))))

;; TODO : differentiate (run) and (display): see also TODO in core.clj.
(defn run [request]
  (let [query-string (get request :form-params)]
    (html
     ;; get 'guess' from query-string (e.g. from "guess=to%20eat")
     ;; pass the users's guess to (quiz), which will evaluate it.
     [:div (quiz (get query-string "guess") request)])))

(defn display [request]
  (let [query-string (get request :form-params)]
    (html
     ;; get 'guess' from query-string (e.g. from "guess=to%20eat")
     ;; pass the users's guess to (quiz), which will evaluate it.
     [:div (quiz nil request)])))


