(ns italianverbs.tour
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :refer [write-str]]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [italianverbs.authentication :as authentication]
   [italianverbs.borges.reader :refer [generate-question-and-correct-set]]
   [italianverbs.config :refer [time-format]]
   [italianverbs.editor :refer [json-read-str]]
   [italianverbs.html :refer [page]]
   [italianverbs.menubar :refer [menubar]]
   [italianverbs.morphology :refer (fo remove-parens)]
   [dag-unify.core :refer (get-in unify)]
   [italianverbs.user :refer [menubar-info-for-user session2userid username2userid]]
   [korma.core :as k]))

;; For now, source language and locale are constant.
(def source-language "en")
(def source-locale "US")

(declare game-chooser)
(declare generate-q-and-a)
(declare get-step-for-user)
(declare request-to-session)
(declare tour)

(defn resources [request game game-target game-city] 
  (fn [request]
    {:onload (str "start_tour('" game "','" game-target "','" game-city "',"
                  (write-str (get-step-for-user (username2userid (authentication/current request))
                                                game))
                  ");")
     :menubar (menubar (menubar-info-for-user request))
     :css ["/css/tour.css"]
     :jss ["/js/cities.js"
           "/js/gen.js"
           "/js/leaflet.js"
           (str "/js/" game-target ".js")
           "/js/tour.js"]}))

(def routes
  (let [headers {"Content-Type" "text/html;charset=utf-8"}]
    (compojure/routes

     (GET "/:game" request
          (let [game (Integer. (:game (:route-params request)))
                game-result (first 
                             (k/exec-raw
                              [(str "SELECT name,source,target,city,created_by,
                                            to_char(game.created_on, ?) AS created_on
                                       FROM game 
                                      WHERE id=? LIMIT 1")
                               [time-format game]] :results))
                target (:target game-result)
                city (:city game-result)]
            (page "Map Tour" (tour target "IT"
                                   game
                                   (username2userid (authentication/current request)))
                  request
                  (resources request game target city))))
     (GET "/:game/debug" request
          (let [game (Integer. (:game (:route-params request)))
                game-result (first 
                             (k/exec-raw
                              [(str "SELECT name,source,target,created_by,
                                            to_char(game.created_on, ?) AS created_on
                                       FROM game 
                                      WHERE id=? LIMIT 1")
                               [time-format game]] :results))]
            {:status 200
             :headers {"Content-Type" "application/json;charset=utf-8"}
             :body (write-str {:comment (str "You chose a moft awfeome game: " game)
                               :game-result game-result
                               :game game})}))

     (GET "/:game/generate-q-and-a" request
          (let [game (Integer. (:game (:route-params request)))
                user-id (username2userid (authentication/current request))]
            (log/debug (str "generating question for game: " game " for user id: " user-id))
            (generate-q-and-a game (request-to-session request))))
     
     (GET "/" request
          {:status 302
           :headers {"Location" "/class"}})

     ;; TODO: join against users to show usernames rather than just session id.
     (GET "/report" request
          {:status 200
           :headers headers
           :body (page "Report"
                       (let [rows nil]
                         [:table {:class "striped padded"}
                          [:tr
                           [:th "id"]
                           [:th "Question"]
                           [:th "Answer"]
                           [:th {:style "text-align:right"} "Time To Correct Response"]
                           [:th "Session"]
                           [:th {:style "text-align:right"} "Issued"]
                           ]

                          (map (fn [row]
                                 [:tr
                                  [:td (:id row)]
                                  [:td (:question row)]
                                  [:td (:answer row)]
                                  [:td {:style "text-align:right"} (:ttcr row)]
                                  [:td (:session row)]
                                  [:td {:style "text-align:right"} (:issued row)]])
                               (k/exec-raw
                                [(str "SELECT question.id,
                                              source.surface AS question,
                                              question.answer,
                                              question.time_to_correct_response AS ttcr,
                                              session_id AS session,
                                              issued
                                         FROM expression AS source
                                   INNER JOIN question
                                           ON question.source = source.id
                                     ORDER BY issued DESC LIMIT 10")
                                 []] :results))])
                       request
                       {})})

     (POST "/update-question" request
           (let [session (:value (get (:cookies request) "ring-session"))
                 question (Integer. (get (:form-params request) "question"))
                 ttcr (Integer. (get (:form-params request) "time"))
                 answer (get (:form-params request) "answer")]
             (log/debug (str "UPDATE QUESTION: POST: " request))
             (log/debug (str "UPDATE QUESTION: form-params: " (:form-params request)))
             (log/debug (str "UPDATE QUESTION: session: " session))
             (log/debug (str "UPDATE QUESTION: ttcr: " ttcr))
             (log/debug (str "UPDATE QUESTION: answer: " answer))
             (log/debug (str "UPDATE question SET (time_to_correct_response) = " ttcr " WHERE (id = " question " AND session_id = " session))
             
             (k/exec-raw [(str "UPDATE question 
                                   SET (time_to_correct_response,answer) = (?,?) 
                                 WHERE (id = ? AND session_id = ?::uuid)")
                          [ttcr answer question session]])

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

(defn get-step-for-user [user-id game-id]
  ;; TODO: just a stub now: should look in DB and find user's last position.
  (let [current-position
        (first 
         (k/exec-raw
          [(str "SELECT last_move 
                       FROM student_in_game
                      WHERE game=? AND student=?")
           [game-id user-id]] :results))]
    (if (and current-position (:last_move current-position))
      {:position (:last_move current-position)
       :direction 1}
      ;; no position yet.
      {:position 0
       :direction 1})))

(defn sync-question-info [ & [{game-id :game-id
                               source-id :source-id
                               session-id :session-id}]]
  ;; Here we insert a question but leave the answer column null.
  ;; The answer is filled in later when the user POSTs to /tour/update-question,
  ;; where we add the user's correct answer and the TTCR (time-to-correct-response).
  "create a question. currently the answer is not tracked (but should be)."
  (log/debug (str "sync-question-info: game-id:" game-id))
  (log/debug (str "sync-question-info: expression source: " source-id))
  (log/debug (str "sync-question-info: session-id: " session-id))

  ;; update user's state in game:
  (log/debug (str "sync-question-info: getting userid for session: " session-id))
  (let [student-id (session2userid session-id)]
    (log/debug (str "sync-question-info: student's user-id: " student-id))
    (if student-id
      (let [current-position
            (first 
             (k/exec-raw
              [(str "SELECT last_move 
                       FROM student_in_game
                      WHERE game=? AND student=?")
               [game-id student-id]] :results))]
        (if (:last_move current-position)
          ;; increment student's last_move for this game.
          (do
            (log/debug (str "found for student: " student-id " a current position: " current-position))
            (k/exec-raw [(str "UPDATE student_in_game SET last_move=? WHERE game=? AND student=?")
                         [(+ 1 (:last_move current-position))
                          game-id student-id]]))
          ;; else, no current position yet: initialize.
          (do
            (log/debug (str "no current position found for student: " student-id))
            (k/exec-raw [(str "INSERT INTO student_in_game (game,student,last_move)
                                   VALUES (?,?,0)")
                         [game-id student-id]]))))))
  (:id (first (k/exec-raw [(str "INSERT INTO question (game,source,session_id)
                                      VALUES (?,?,?::uuid) RETURNING id")
                           [game-id source-id session-id]] :results))))

;; TODO: move to session.clj
(defn request-to-session [request]
  (if (and request
           (:cookies request)
           (get (:cookies request) "ring-session"))
    (:value (get (:cookies request) "ring-session"))))

(defn generate-q-and-a [game-id session-id]
  "generate a question in English and a set of possible correct answers in the target language, given parameters in request"
  (let [headers {"Content-Type" "application/json;charset=utf-8"
                 "Cache-Control" "no-cache, no-store, must-revalidate"
                 "Pragma" "no-cache"
                 "Expires" "0"}
        game (first 
              (k/exec-raw
               [(str "SELECT name,source,target,created_by,
                             to_char(game.created_on, ?) AS created_on
                        FROM game 
                       WHERE id=? LIMIT 1")
                [time-format game-id]] :results))
        target-language (:target game)
        target-locale "IT" ;; TODO: game should have a locale
        ]
        
    (log/info (str "generate-q-and-a: target=" target-language "; target-locale=" target-locale ""))
    (log/debug (str "generate-q-and-a: session-id: " session-id))
      
    (try (let [debug (log/info (str "generate-q-and-a: chosen game=" game))
               ;; TODO: do not allow failsafe "any" mode if no game chosen: some game should always be chosen if we got here;
               ;; else return "404 Game not found."
               game (cond (= "" game)
                          :any
                          (nil? game)
                          :any
                          (string? game)
                          (Integer. game)
                          true game)
               game (if (= game :any)
                      (let [active (active-games target-language)]
                        (if (empty? active)
                          :any
                          (:id (nth active
                                    (rand-int (.size active))))))
                      game)
               debug (log/debug (str "game:" game))
               game-spec (get-game-spec "en" target-language game-id)
               debug (log/debug (str "game-spec: " game-spec))
               debug (log/debug (str "target-spec: " (get game-spec :target_spec)))
               spec :top

               debug (log/debug (str "game-spec:" game-spec))

               tuple 
               (generate-question-and-correct-set (:target_spec game-spec)
                                                  source-language source-locale
                                                  target-language target-locale)

               debug (log/info (str "Question-and-answer tuple: " tuple))
               
               question-id (sync-question-info {:source-id (:source-id tuple)
                                                :session-id session-id
                                                :game-id game-id})

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

;; TODO: Move HTML rendering to javascript (tour.js) - tour.clj should only be involved in
;; routing requests to functions that respond with JSON.
(defn tour [language locale chosen-game user-id]
  (log/info (str "(tour : chosen-game=" chosen-game) ")")
  [:div#game

   [:div#correctanswer 
    " "
    ]

   [:div#gotitright
    " "
    ]

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
