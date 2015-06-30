(ns italianverbs.tour
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :refer [write-str]]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.borges.reader :refer [generate-question-and-correct-set]]
   [italianverbs.editor :refer [json-read-str]]
   [italianverbs.html :refer [page]]
   [italianverbs.morphology :refer (fo remove-parens)]
   [italianverbs.unify :refer (get-in unify)]
   [korma.core :as k]))

;; For now, source language and locale are constant.
(def source-language "en")
(def source-locale "US")

(declare game-chooser)
(declare generate-q-and-a)
(declare get-step-for-user)
(declare tour)

(def routes
  (let [headers {"Content-Type" "text/html;charset=utf-8"}]
    (compojure/routes

     (GET "/it" request
          {:headers headers
           :status 200
           :body
           (let [chosen-game (get (:query-params request) "game")
                 chosen-game (cond (= "" chosen-game)
                                   nil
                                   (nil? chosen-game)
                                   nil
                                   true
                                   (Integer. chosen-game))]
             (page "Map Tour" (tour "it" "IT"
                                    chosen-game
                                    ) request {:onload (str "start_tour('it','IT','" chosen-game "');")
                                               :css ["/css/tour.css"]
                                               :jss ["/js/cities.js"
                                                     "/js/gen.js"
                                                     "/js/leaflet.js"
                                                     "/js/it.js"
                                                     "/js/tour.js"]}))})
     (GET "/es" request
          {:status 302
           :headers {"Location" "/tour/es/ES"}})

     (GET "/es/ES" request
          {:status 200
           :headers headers
           :body (page "Map Tour" (tour "es" "ES") request {:onload "start_tour('es','ES');"
                                                       :css ["/css/tour.css"]
                                                       :jss ["/js/cities.js"
                                                             "/js/gen.js"
                                                             "/js/leaflet.js"
                                                             "/js/es.js"
                                                             "/js/tour.js"]})})
     (GET "/es/MX" request
          {:status 200
           :headers headers
           :body (page "Map Tour" (tour "es" "MX") request {:onload "start_tour('es','MX');"
                                                            :css ["/css/tour.css"]
                                                            :jss ["/js/cities.js"
                                                                  "/js/gen.js"
                                                                  "/js/leaflet.js"
                                                                  "/js/es.js"
                                                                  "/js/tour.js"]})})
     (GET "/it/generate-q-and-a" request
          (generate-q-and-a "it" "IT" request))

     (GET "/it/IT/generate-q-and-a" request
          (generate-q-and-a "it" "IT" request))

     (GET "/it/step" request
          (get-step-for-user "it" "IT" request))

    (GET "/es/ES/generate-q-and-a" request
         (generate-q-and-a "es" "ES" request))

     (GET "/es/MX/generate-q-and-a" request
          (generate-q-and-a "es" "MX" request))

     ;; Default: no locale given.
     ;; TODO: redirect to /tour/es/es/ES/generate-q-and-a
     (GET "/es/generate-q-and-a" request
          (generate-q-and-a "es" "ES" request))

     ;; below URLs are for backwards-compatibility:
     (GET "/" request
          {:status 302
           :headers {"Location" "/tour/it"}})

     (GET "/generate-q-and-a" request
          {:status 302
           :headers {"Location" "/tour/it/generate-q-and-a"}})

     (POST "/update-question" request
           (let [session (:value (get (:cookies request) "ring-session"))
                 question (Integer. (get (:form-params request) "question"))
                 ttcr (Integer. (get (:form-params request) "time"))]
             (log/debug (str "UPDATE QUESTION: POST: " request))
             (log/debug (str "UPDATE QUESTION: form-params: " (:form-params request)))
             (log/debug (str "UPDATE QUESTION: session: " session))
             (log/debug (str "UPDATE QUESTION: ttcr: " ttcr))
             (log/debug (str "UPDATE question SET (time_to_correct_response) = " ttcr " WHERE (id = " question " AND session_id = " session))
             
             (k/exec-raw [(str "UPDATE question SET (time_to_correct_response) = (?) WHERE (id = ? AND session_id = ?::uuid)")
                          [ttcr question session]])
             {:status 200
              :headers {"Content-Type" "application/json;charset=utf-8"}
              :body (write-str (:status (str "updated question: " (:question (:form-params request)))))}))
     )))

(defn get-game-spec [source-language target-language game]
  (log/debug (str "get-game-spec: game=" game))
  (log/debug (str "source-language: " source-language))
  (log/debug (str "target-language: " target-language))

  ;; no game chosen: use :top for both source and spec.
  (cond (= game :any)
        {:source-spec :top
         :target_spec :top}

        true
        (let [game-result (first 
                           (k/exec-raw
                            [(str "SELECT * 
                                     FROM game 
                                    WHERE id=? AND source=? AND target=? LIMIT 1")
                             [game source-language target-language]] :results))
              target-lexemes (map (fn [each-lexeme]
                                    each-lexeme)
                                  (map json-read-str (.getArray (:target_lex game-result))))
              target-tenses (map (fn [each-tense]
                                   each-tense)
                                 (map json-read-str (.getArray (:target_grammar game-result))))]

          (log/trace (str "game's target lexicon: " (string/join "," target-lexemes)))
          (log/debug (str "game's target tenses: " (string/join "," target-tenses)))
      
          (let [target-lexeme 
                (if (empty? target-lexemes)
                  :top
                  (nth target-lexemes (rand-int (.size target-lexemes))))

                target-tense (if (empty? target-tenses)
                               :top
                               (nth target-tenses (rand-int (.size target-tenses))))]

            (log/debug (str "target lexeme: " target-lexeme))
            (log/debug (str "target tense: " target-tense))
            ;; TODO: throw exception if (unify target-lexeme target-tense) would result in a fail.
            {:target_spec
             (unify target-lexeme target-tense)
             :source_spec :top}))))

(defn active-games [target-language]
  (log/debug (str "Selecting an active game from language:" target-language))
  (k/exec-raw [(str "SELECT * FROM game
                             WHERE active=true
                               AND target=?")
               [target-language]] :results))

(defn get-step-for-user [target-language target-locale request]
  (let [headers {"Content-Type" "application/json;charset=utf-8"
                 "Cache-Control" "no-cache, no-store, must-revalidate"
                 "Pragma" "no-cache"
                 "Expires" "0"}]
    {:status 200
     :headers headers
     :body (write-str {:position 0
                       :direction 1})}))

(defn sync-question-info [ & [{game-id :game-id
                               source-id :source-id
                               session-id :session-id}]]
  ;; TODO: save answer within question.
  ;; Look for where we set 'time_to_correct_response' to know how to add answer to question.
  "create a question. currently the answer is not tracked (but should be)."
  (log/info (str "sync-question-info: game-id:" game-id))
  (log/info (str "sync-question-info: expression source: " source-id))
  (log/info (str "sync-question-info: session-id: " session-id))
  (:id (first (k/exec-raw [(str "INSERT INTO question (game,source,session_id)
                                      VALUES (?,?,?::uuid) RETURNING id")
                           [game-id source-id session-id]] :results))))

(defn generate-q-and-a [target-language target-locale request]
  "generate a question in English and a set of possible correct answers in the target language, given parameters in request"
  (log/info (str "generate-q-and-a: target=" target-language "; target-locale=" target-locale ""))
  (log/debug (str "generate-q-and-a: request=" request))
  (log/debug (str "generate-q-and-a: session-id(1): " (:cookies request)))
  (log/debug (str "generate-q-and-a: session-id(2): " (get (:cookies request) "ring-session")))
  (log/debug (str "generate-q-and-a: session-id(3): " (:value (get (:cookies request) "ring-session"))))
  (let [session-id (if (and request
                         (:cookies request)
                         (get (:cookies request) "ring-session"))
                     (:value (get (:cookies request) "ring-session")))
        headers {"Content-Type" "application/json;charset=utf-8"
                 "Cache-Control" "no-cache, no-store, must-revalidate"
                 "Pragma" "no-cache"
                 "Expires" "0"}]
    (log/debug (str "generate-q-and-a: session-id: " session-id))
      
    (try (let [game (get (:params request) :game :any)
               debug (log/info (str "generate-q-and-a: chosen game=" game))
               game (cond (= "" game)
                          :any
                          (nil? game)
                          :any
                          (string? game)
                          (Integer. game)
                          true game)
               step (get (:params request) :step)
               game (if (= game :any)
                      (let [active (active-games target-language)]
                        (if (empty? active)
                          :any
                          (:id (nth active
                                    (rand-int (.size active))))))
                      game)
               debug (log/debug (str "game:" game))
               debug (log/debug (str "param keys:" (keys (:params request))))
               game-spec (get-game-spec "en" target-language game)
               debug (log/debug (str "game-spec: " game-spec))
               debug (log/debug (str "target-spec: " (get game-spec :target_spec)))
               spec :top
               tuple 
               (generate-question-and-correct-set (:target_spec game-spec)
                                                  source-language source-locale
                                                  target-language target-locale)

               debug (log/info (str "Question-and-answer tuple: " tuple))
               
               question-id (sync-question-info {:source-id (:source-id tuple)
                                                :session-id session-id
                                                :game-id game})

               ]
           {:status 200
            :headers headers
            :body (write-str
                   (merge tuple {:question_id question-id}))})
         (catch Exception e
           (do
             (log/error (str "attempt to (generate-question-and-correct-set) threw an error: " e))
             {:status 500
              :headers headers
              :body (write-str {:exception (str e)})})))))

(defn accent-characters [language locale]
  (cond (= language "it")
        [:div.accents
         [:button.accented {:onclick (str "add_a_grave('it','IT');")} "&agrave;"]
         [:button.accented {:onclick (str "add_e_grave('it','IT');")} "&egrave;"]
         [:button.accented {:onclick (str "add_o_grave('it','IT');")} "&ograve;"]
         ]
        (= language "es")
        (cond (= locale "MX")
              [:div.accents
               [:button.accented {:onclick (str "add_a_acute('es','MX');")} "&aacute;"]
               [:button.accented {:onclick (str "add_e_acute('es','MX');")} "&eacute;"]
               [:button.accented {:onclick (str "add_i_acute('es','MX');")} "&iacute;"]
               [:button.accented {:onclick (str "add_n_tilde('es','MX');")} "&ntilde;"]
               [:button.accented {:onclick (str "add_u_acute('es','MX');")} "&uacute;"]]
              :else
              [:div.accents
               [:button.accented {:onclick (str "add_a_acute('es','ES');")} "&aacute;"]
               [:button.accented {:onclick (str "add_e_acute('es','ES');")} "&eacute;"]
               [:button.accented {:onclick (str "add_i_acute('es','ES');")} "&iacute;"]
               [:button.accented {:onclick (str "add_n_tilde('es','ES');")} "&ntilde;"]
               [:button.accented {:onclick (str "add_u_acute('es','ES');")} "&uacute;"]])
        true
        ""))

(defn dont-know [language locale]
  (let [non_lo_so
        (cond (= language "it")
              (str "non_lo_so('it','IT');")
              (and (= language "es")
                   (= locale "MX"))
              (str "non_lo_so('es','MX');")
              (= language "es")
              (str "non_lo_so('es','ES');")
              :else
              (str "non_lo_so('it','IT');"))]
    [:button#non_lo_so {:onclick non_lo_so}
     (cond (= language "it")
           "Non lo so"
           (= language "es")
           "No sé"
           true
           "")]))

;; TODO: Move this to javascript (tour.js) - tour.clj should only be involved in
;; routing requests to responses.
(defn tour [language locale chosen-game]
  (log/info (str "(tour : chosen-game=" chosen-game) ")")
  [:div#game

   [:div#correctanswer 
    " "
    ]
   
   (game-chooser (if chosen-game (Integer. chosen-game) nil)
                 language
                 locale
                 )

    [:div#map ]

   [:div#streetview
    [:iframe#streetviewiframe
     {:src ""}] ;; src value is filled in with Javascript.
    ]

   [:div#tourgameform

    [:div#tourquestion
     ""
     ]

    [:div#gameinputdiv
      [:input#gameinput {:size "20"}]
     
     (accent-characters language locale)

     (dont-know language locale)]

    [:div#userprogresscontainer
     [:div#userprogress 
      ]]

     
    [:div#kilos {:style "z-index:4"}
     "Score:"
     [:span#scorevalue
      "0"
      ]
     ]
    ]

   ]
  )

(def game-pairs
  [{:source "en"
    :destination "it"
    :source_flag "/svg/britain.svg"
    :destination_flag "/svg/italy.svg"}])

(defn get-possible-games [] 
  (map (fn [row]
         (keyword (:word row)))
       (k/exec-raw [(str "SELECT * FROM games_to_use ")] :results)))

(defn choose-random-verb-group []
  "choose a verb group randomly from the games that are currently possible to play as determined by the games_to_use table."
  (let [games (k/exec-raw [(str "SELECT id FROM games WHERE games.id IN (SELECT game FROM games_to_use)")] :results)]
    (:id (nth games (rand-int (.size games))))))

(defn get-possible-preds [game-id]
  (map (fn [row]
         (:pred row))
       (k/exec-raw [(str "SELECT word AS pred
                            FROM words_per_game
                      INNER JOIN games
                              ON words_per_game.game = games.id
                           WHERE games.id = ?") [game-id]] :results)))

(defn get-possible-inflections [game-id]
  (map (fn [row]
         (:inflection row))
       (k/exec-raw [(str "SELECT inflection
                            FROM inflections_per_game
                           WHERE game = ?") [game-id]] :results)))

(defn game-chooser [current-game target-language locale]
  [:div#chooser {:class "tourelement"}

   "Choose your class/game:"

   [:select#chooserselect
    {:style "display:block;"
     :onchange
     "document.location='?game='+this.options[this.selectedIndex].value;"}

    [:option {:value ""} "All active games"]
    
    (map (fn [row]
           (let [if-selected (if (= (:id row) current-game)
                               {:selected true}
                               {})]
             [:option (merge if-selected {:value (:id row)}) (:name row)]))
         (k/exec-raw [(str "SELECT name,id
                              FROM game
                             WHERE target=?
                               AND active = true") [target-language]] :results))
    ]])

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

(defn additional-generation-constraints [spec]
  "apply additional constraints to improve generation results or performance."
  (cond false ;;(= generate-by :runtime)
        (unify spec
               {:head {:phrasal :top}
                :comp {:phrasal false}})
        true
        spec))
