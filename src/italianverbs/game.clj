(ns italianverbs.game
  (:require
   [clj-time.core :as t]
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [formative.core :as f]
   [hiccup.core :refer (html)]
   [italianverbs.authentication :as authentication]
   [italianverbs.config :refer [describe-spec
                                default-city-for-language
                                default-source-language
                                json-read-str
                                language-dropdown
                                language-radio-buttons
                                language-to-spec-path
                                short-language-name-from-match
                                short-language-name-to-edn
                                short-language-name-to-long
                                sqlname-from-match
                                tenses-as-editables
                                tenses-as-editables-french
                                tenses-human-readable
                                time-format]]
   [italianverbs.html :as html :refer [banner page rows2table]]
   [babel.korma :as db]
   [italianverbs.menubar :refer [menubar]]
   [italianverbs.user :refer [do-if do-if-admin do-if-authenticated do-if-teacher
                              do-if-teacher-or-admin
                              do-if-not-teacher
                              has-admin-role?
                              has-teacher-role?
                              login-form menubar-info-for-user username2userid]]
   [dag-unify.core :refer [unify]]
   [korma.core :as k]))

(declare body)
(declare delete-game)
(declare game-to-edit)
(declare game-editor-form)
(declare onload)
(declare insert-game)
(declare is-owner-of?)
(declare new-game-form)
(declare show-students)
(declare show-game)
(declare show-games)
(declare source-to-target-mappings)
(declare table)
(declare toggle-activation)
(declare tr)
(declare update-game)
(declare verb-tense-table)

(def html-headers {"Content-Type" "text/html;charset=utf-8"})
(def json-headers {"Content-type" "application/json;charset=utf-8"})
(defn resources [request]
  {:onload "game_onload();"
   :css ["/css/game.css"]
   :jss ["/js/editor.js" "/js/game.js" "/js/gen.js"]
   :show-login-form (login-form request)
   :menubar (menubar (menubar-info-for-user request))})

(declare tenses-of-game-as-human-readable)
(declare verbs-of-game-as-human-readable)
(declare games-i-am-playing)

(def routes
  (compojure/routes
   (GET "/" request
        (do-if-authenticated
         {:body
          (page "Games"
                (let [user-id (username2userid (authentication/current request))]
                  [:div {:class "major"}
                   [:h2 "Games"]
                   
                   ;; do at bottom if user is a teacher.
                   (do-if-not-teacher
                    (games-i-am-playing user-id)
                    "")

                   (do-if-teacher
                    [:div {:class "gamelist"}
                     [:h3 "Games in classes I'm teaching"]
                     (let [results (k/exec-raw
                                    ["SELECT class.id AS class_id,class.name AS class, game.name AS game, 
                                             game.id AS game_id,class.language,game.city,
                                             game.target_grammar AS tenses,game.target_lex AS verbs,
                                             trim(creator.given_name || ' ' || creator.family_name) AS game_owner
                                        FROM game
                                  INNER JOIN game_in_class ON (game.id = game_in_class.game)
                                  INNER JOIN class ON (class.id=game_in_class.class AND class.teacher = ?)
                                   LEFT JOIN vc_user AS creator ON (game.created_by = creator.id)"
                                     [user-id]] :results)]
                       (rows2table results
                                   {:cols [:game :class :language :city :verbs :tenses :game_owner]
                                    :col-fns
                                    {:game (fn [game-in-class]
                                             [:a {:href (str "/game/" (:game_id game-in-class))} (:game game-in-class)])
                                     :class (fn [game-in-class]
                                              [:a {:href (str "/class/" (:class_id game-in-class))} (:class game-in-class)])
                                     :language (fn [game-in-class]
                                                 (short-language-name-to-long (:language game-in-class)))
                                     :verbs (fn [game-in-class]
                                              (verbs-of-game-as-human-readable (:verbs game-in-class)
                                                                               (:language game-in-class)))
                                     :tenses (fn [game-in-class]
                                               (tenses-of-game-as-human-readable (:tenses game-in-class)))
                                     }}
                                   ))]
                    "")
                   (do-if-teacher
                    [:div {:class "gamelist"}
                     [:h3 "Games I created"]
                     (let [results (k/exec-raw
                                    ["SELECT game.name AS game, game.target AS language,city,game.id AS game_id,
                                             game.target_grammar AS tenses,game.target_lex AS verbs
                                        FROM game WHERE created_by=?"
                                     [user-id]] :results)]
                       (rows2table results
                                   {:cols [:game :language :city :verbs :tenses]
                                    :col-fns
                                    {:game (fn [game-in-class]
                                             [:a {:href (str "/game/" (:game_id game-in-class))} (:game game-in-class)])
                                     :language (fn [game-in-class]
                                                 (short-language-name-to-long (:language game-in-class)))
                                     :verbs
                                     (fn [game-in-class]
                                       (let [language-short-name (:language game-in-class)
                                             language-keyword-name
                                             (sqlname-from-match (short-language-name-to-long language-short-name))
                                             language-keyword (keyword language-keyword-name)
                                             target-lex (:verbs game-in-class)]
                                         (string/join ", "
                                                      (map #(html [:i %])
                                                           (map #(get-in % [:root language-keyword language-keyword])
                                                                (map json-read-str
                                                                     (.getArray target-lex)))))))
                                     :tenses
                                     (fn [game-in-class]
                                       (let [target-grammar (:tenses game-in-class)]
                                         (map #(html [:b (str %)])
                                              (remove nil?
                                                      (string/join ", "
                                                      (map #(get tenses-human-readable %)
                                                           (map json-read-str
                                                                (.getArray target-grammar))))))))
                                     }}
                                   ))
                     ]
                    ""
                    )

                   (do-if-teacher
                    (new-game-form)
                    "")

                   (do-if-teacher
                    (games-i-am-playing user-id)
                    "")

                   ]


                  )
                request
                resources)
          :status 200
          :headers html-headers}))

   (POST "/activate/:game" request
         (let [game-id (:game (:route-params request))
               user (authentication/current request)]
           (do-if
            (do
              (log/debug (str "Activate game: " game-id ": can user: '" user "' activate this game?"))
              (is-owner-of? (Integer. game-id) user))
            (let [debug (log/debug (str "PARAMS: " (:params request)))
                  message (toggle-activation (:game (:route-params request)) (= "on" (:active (:params request))))]
              {:status 302
               :headers {"Location" (str "/game?message=" message)}})
            (do
              (log/warn (str "User:" user " tried to activate game: " game-id " but was denied authorization to do so."))
              {:status 302
               :headers {"Location" (str "/game/?message=Unauthorized+to+activate+game:" game-id)}}))))

   (POST "/clone/:game-id" request
          (let [source-game-id (:game-id (:route-params request))
                user (authentication/current request)
                user-id (username2userid user)]
            (do-if-teacher
             (do
               (log/info "cloning game: " source-game-id " for user: " user)
               (let [new-game-id
                     (:id (first
                           (k/exec-raw ["
INSERT INTO game
           (source_groupings,target_groupings,source_lex,source_grammar,
           target_lex,target_grammar,name,source,target,active,created_by)

     SELECT source_groupings,target_groupings,source_lex,source_grammar,

            target_lex,target_grammar,name || ' (copy)',source,target,false,?
       FROM game
      WHERE id=? RETURNING id"
                            [user-id (Integer. source-game-id)]] :results)))]
                 {:status 302
                  :headers {"Location" (str "/game/" new-game-id)}})))))
   
   (POST "/delete/:game-to-delete" request
         (let [game-id (:game-to-delete (:route-params request))
               user (authentication/current request)]
           (do-if
            (do
              (log/debug (str "Delete game: " game-id ": can user: '" user "' delete this game?"))
              (or (is-owner-of? (Integer. game-id) user)
                  (has-admin-role?)))
            (do
              (let [message (delete-game (:game-to-delete (:route-params request)))]
                {:status 302
                 :headers {"Location" (str "/game/" "?message=" message)}}))
            (do
              (log/warn (str "User:" user " tried to delete game: " game-id " but was denied authorization to do so."))
              {:status 302
               :headers {"Location" (str "/game/" game-id "?message=Unauthorized+to+delete+game:" game-id)}}))))
  
   (POST "/:game-to-edit/edit" request
         (let [game-id (:game-to-edit (:route-params request))
               user (authentication/current request)
               form-params (html/multipart-to-edn (:multipart-params request))
               class-id (if (and (:class form-params)
                                 (not (empty? (:class (:params request)))))
                          (Integer. (:class form-params)))]
           (do-if
            (do
              (log/debug (str "Edit game: " game-id ": can user: '" user "' edit this game?"))
              (or (is-owner-of? (Integer. game-id) user)
                  (has-admin-role?)))
            (do
              (log/info (str "User:" user " is authorized to update this game."))
              (update-game (:game-to-edit (:route-params request))
                           (html/multipart-to-edn (:multipart-params request)))
              {:status 302
               :headers {"Location" (str "/game/" game-id
                                         "?"
                                         (if class-id (str "class=" class-id "&"))
                                         "message=Edited+game:" game-id)}})
            (do
              (log/warn (str "User:" user " tried to edit game: " game-id " but was denied authorization to do so."))
              {:status 302
               :headers {"Location" (str "/game/" game-id "?message=Unauthorized+to+edit+game:" game-id)}}))))

   (GET "/:game/:verb/:tense" request
        ;; Return translation pairs for this game's target and source language such that
        ;; the target member of the pair has the given verb and tense.
        (do-if-teacher-or-admin
         (let [game-id (Integer. (:game (:route-params request)))
               nth-verb (Integer. (:verb (:route-params request)))
               nth-grammar-spec (Integer. (:tense (:route-params request)))
               game (first (k/exec-raw ["SELECT target_lex,target_grammar
                                           FROM game 
                                          WHERE id=?" [(Integer. game-id)]] :results))
               lexeme-spec (json-read-str (nth (map (fn [x] x) (.getArray (:target_lex game))) nth-verb))
               grammar-spec (json-read-str (nth (map (fn [x] x) (.getArray (:target_grammar game))) nth-grammar-spec))

               unified-spec ;; combine the tense and lexeme together into a single spec
               (unify lexeme-spec grammar-spec)

               sql
               "SELECT count(*) 
                  FROM (SELECT source.surface AS source,
                               array_sort_unique(array_agg(target.surface)) AS targets
                          FROM game
                    RIGHT JOIN expression AS source
                            ON source.language = game.source
                    RIGHT JOIN expression AS target
                            ON target.language = game.target
                           AND ((source.structure->'synsem'->'sem') @> (target.structure->'synsem'->'sem'))
                           AND target.structure @> ?::jsonb
                         WHERE game.id=?
                      GROUP BY source.surface) AS translation_pairs"

               count (:count (first (k/exec-raw [sql
                                                 [(json/write-str unified-spec)
                                                  game-id] ]
                                                :results)))
               ]
           {:body (json/write-str
                   {:count count
                    :refine_param unified-spec})
            :status 200
            :headers json-headers})))

   (GET "/:game" request
        (let [game-id (Integer. (:game (:route-params request)))
              class-id (if (and (:class (:params request))
                                (not (empty? (:class (:params request)))))
                         (Integer. (:class (:params request)))) ;; class-id is optional
              game (first (k/exec-raw ["SELECT * FROM game WHERE id=?" [(Integer. game-id)]] :results))
              class (if class-id (first (k/exec-raw ["SELECT * FROM class WHERE id=?" [(Integer. class-id)]] :results)))]
          {:body
           (page (str
                  (if class (str "Class: " class))
                  "Game: " (:name game))
                 (body (:name game)
                       (show-game (:game (:route-params request))
                                  {:show-as-owner? (or (has-admin-role?)
                                                       (is-owner-of?
                                                        (Integer. game-id)
                                                        (authentication/current request)))
                                   :show-as-teacher? (has-teacher-role?)
                                   :class class-id
                                   :refine (:refine (:params request))})
                       request
                       class-id)
                 request resources)
           :status 200
           :headers html-headers}))
   
   (POST "/new" request
         (do-if-teacher-or-admin
          (let [params (html/multipart-to-edn (:multipart-params request))
                language (cond (and (:language params)
                                    (not (= (string/trim (:language params)) "")))
                               (:language params)

                               true
                               (do (log/debug (str "POST /game/new: trying to guess language from game's name: " (:name params)))
                                   (let [result (short-language-name-from-match (:name params))]
                                     (log/debug (str "guess was: " result))
                                     result)))

                city (if (:city params)
                       (:city params)
                       (default-city-for-language language))
                
                user (authentication/current request)
                user-id (username2userid user)

                debug (log/debug (str "Inserting new game with params: " params))
                
                target-language language
                game (insert-game (:name params) default-source-language target-language
                                  (:source_groupings params)
                                  (:target_groupings params)
                                  user-id)
                params (merge params
                              {:city city})
                class (if (:class params)
                        (Integer. (:class params)))
                ]
            (log/debug (str "Updating game: " (:id game) " post-insert with params: " params))
            (update-game (:id game) (merge params
                                           {:source default-source-language
                                            :target language
                                            :city city}))
            (let [game (:id game)]
              (if class
                (k/exec-raw ["INSERT INTO game_in_class (game,class) 
                                     SELECT ?,?
                                      WHERE 
                                 NOT EXISTS (SELECT game,class 
                                               FROM game_in_class 
                                              WHERE game = ? AND class=?)" [game class game class]]))
              {:status 302 :headers {"Location" 
                                     (str "/game/" game
                                          "?"
                                          (if class (str "class=" class "&"))
                                          "message=Created game.")}}))))))
(defn new-game-form [ & [ {class :class
                           header :header
                           target-language :target-language}]]
  (let [header (if header header "Create a new game")]
    [:div#new {:class "gamelist"}
     [:h3 header]
     [:div.new {:style "display:block"}
      [:form {:method "post"
              :id "new_game_form"
              :enctype "multipart/form-data"
              :onsubmit "return validateNewGame('new_game_form');"
              :action "/game/new"}
       (if target-language
         [:input {:type :hidden :name "language" :value target-language}]
         ;; if no target-language yet, we need to show the radio buttons to choose one.
         [:div {:style "float:left;width:99%;padding:0.5em"}
          [:table.language_radio
           [:tr
            (map (fn [option]
                   [:td
                    [:input {:type "radio" :label (:label option) :name "language" :value (:value option)}
                     (:label option)]])
               (:options (language-radio-buttons)))]]])
       (if class [:input {:type :hidden
                          :name "class" :value class}])
       [:input {:name "name" :size "50" :placeholder (str "Enter the name of a new game.")} ]
       [:div.input-shell
        [:input {:class "btn btn-primary"
                 :name "submit"
                 :type "submit"
                 :value "Create"}]]
       ]]]))

(defn games-i-am-playing [user-id]
  [:div {:class "gamelist"}
   [:h3 "Games I'm playing"]
   (let [results (k/exec-raw
                  ["SELECT 'resume' AS resume,game.id AS id,game.name AS game,city,
                                           last_move AS position,game.target AS language
                                      FROM student_in_game
                                INNER JOIN game
                                        ON (game.id = student_in_game.game)
                                     WHERE student=?"
                   [user-id]] :results)]
     [:div.rows2table
      (rows2table results
                  {:col-fns {:game (fn [game]
                                     (html [:a {:href (str "/game/" (:id game))}
                                            (str (:game game))]))
                             :language (fn [game-in-class]
                                         (short-language-name-to-long (:language game-in-class)))
                             :resume (fn [game]
                                       (html [:button {:onclick (str "document.location='/tour/"
                                                                     (:id game) "';")}
                                              "Resume"]))}
                   :th-styles {:resume "visibility:hidden"}
                   :cols [:resume :game :city :position :language]})])])

(defn insert-game [name source target source-set target-set user-id]
  "Create a game with a name, a source and target language and two
  arrays, one for the source language and one for the target
  language. Each array is an integer which refers to a set of
  'anyof-sets', each member of which is a possible specification in its
  respective language. See example usage in test/editor.clj."
  (log/debug (str "source-set: " source-set))
  (log/debug (str "source-set with commas: " (str "ARRAY[" (string/join "," (map #(str "" (string/join "," %) "") source-set)) "]")))
  (log/debug (str "target-set with commas: " (str "ARRAY[" (string/join "," (map #(str "" (string/join "," %) "") target-set)) "]")))

  (let [source-grouping-str (str "ARRAY[" (string/join "," (map #(str "" (string/join "," %) "") source-set)) "]::integer[]")
        target-grouping-str (str "ARRAY[" (string/join "," (map #(str "" (string/join "," %) "") target-set)) "]::integer[]")
        sql (str "INSERT INTO game (name,source_groupings,target_groupings,source,target,created_by)
                       SELECT ?, " source-grouping-str "," target-grouping-str ",?,?,? RETURNING id")]
    (log/debug (str "inserting new game with sql: " sql))
    ;; return the row ID of the game that has been inserted.
    (first (k/exec-raw [sql [name source target user-id]] :results))))

(defn is-owner-of? [game-id user]
  "return true iff user (identified by their email) is the owner of the game whose is game-id"
  (log/debug (str "is-owner-of: game-id:" game-id))
  (log/debug (str "is-owner-of: user:   " user))
  (let [result (first (k/exec-raw ["SELECT 1
                                      FROM game
                                INNER JOIN vc_user
                                        ON (vc_user.email = ?)
                                     WHERE game.id=?
                                       AND game.created_by = vc_user.id"
                                   [user game-id]]
                                  :results))]
    (not (nil? result))))

(defn onload []
  (string/join " "
               (map (fn [game]
                      (let [game-id (:game game)
                            source (:source game)
                            target (:target game)]
                        (str
                         "log(INFO,'editor onload: loading gen_per_verb( " game-id " )');"
                         "gen_per_verb('" game-id "','" source "','" target "');")))
                    (k/exec-raw [(str "SELECT games_to_use.game,games.source,games.target
                                         FROM games_to_use
                                   INNER JOIN games
                                           ON games_to_use.game = games.id")] :results))))

(defn body [title content request & [class-id]]
  (let [game-id (:game (:route-params request))
        game (if game-id
               (first
                (k/exec-raw
                 ["SELECT * FROM game WHERE id=?" [(Integer. game-id)]] :results)))
        class (if class-id
                (first (k/exec-raw ["SELECT * FROM class WHERE id=?" [(Integer. class-id)]] :results)))]
    (html/page
     title
     (html
      [:div {:class "major"}
       [:h2 
        (banner (concat
                 (if class-id
                   [{:href (str "/class/" class-id)
                     :content (:name class)}]
                   [{:href "/game" 
                     :content "Games"}])
                 (if game
                   [{:href (if (:refine (:params request))
                             (str "/game/" game-id))
                     :content (if (= "" (string/trim title)) "(untitled)" title)}])

                 (if (:refine (:params request))
                   [{:href nil
                     :content 
                     (describe-spec (json-read-str (:refine (:params request))))
                    }]

                   ) ;; (if (:refine
                 ) ;; concat
                ) ;; banner
        ] ;; :h2
      content])
     request
     {:css "/css/game.css"

      :jss [ "/js/editor.js" "/js/game.js" "/js/gen.js"]
      :onload (onload)})))

(defn toggle-activation [game active]
  (do (k/exec-raw ["UPDATE game SET active=? WHERE id=?" [active (Integer. game)]])
      (str "successfully toggled activation of game: " game)))

(defn delete-game [game-id]
  (if game-id
    (do
      (log/debug (str "DELETING GAME: " game-id))
      (let [game-id (Integer. game-id)
            game-row (first (k/exec-raw [(str "SELECT * FROM game WHERE id=?") [game-id]] :results))]
        (log/debug (str "GAME ROW: " game-row))
        (k/exec-raw [(str "DELETE FROM game WHERE id=?") [game-id]])
        (str "Deleted game: " (:name game-row))))

    ;; game-id is null:
    (let [error-message (str "Error: no game to delete: game-id was null.")]
      (log/error error-message)
      error-message)))

(defn update-game [game-id params]
  (let [game-id game-id
        dump-sql false
        params (html/multipart-to-edn params)
        debug  (log/debug (str "UPDATING GAME WITH PARAMS (converted to keywords): " params))

        source-grouping-set (cond
                             (nil? (:source_groupings params))
                             []
                             (and (string? (:source_groupings params))
                                  (= (string/trim (:source_groupings params)) ""))
                             []
                             (string? (:source_groupings params))
                             (do (log/error (str "source_groupings is unexpectedly a string:"
                                                 (:source_groupings params) "; splitting."))
                                 (throw (Exception. (str "editor/update-game: could not update game: " game-id "; no :source_groupings found in input params: " params))))
                             true
                             (filter #(not (= (string/trim %) ""))
                                     (:source_groupings params)))

        target-grouping-set (cond
                             (nil? (:target_groupings params))
                             []
                             (and (string? (:target_groupings params))
                                  (= (string/trim (:target_groupings params)) ""))
                             []
                             (string? (:target_groupings params))
                             (do (log/error (str "target_groupings is unexpectedly a string:"
                                                 (:target_groupings params) "; splitting."))
                                 (throw (Exception. (str "editor/update-game: could not update game: " game-id "; no :target_groupings found in input para
ms: " params))))
                             true
                             (filter #(not (= (string/trim %) ""))
                                     (:target_groupings params)))


        debug (log/debug (str "project name will be updated to: " (:name params)))

        language-name (short-language-name-to-edn (:target params))

        ;; wrap every lexeme in a {:root {<language> <lexeme}}.
        target-lexical-specs
        (map (fn [each-lexeme]
               {:root {language-name {language-name each-lexeme}}})
             (filter #(not (= (string/trim %) ""))
                     (:target_lex params)))

        ;; tenses are given as specs already, so no need to convert: just remove blanks.
        target-tenses-as-specs
        (filter #(not (= (string/trim %) ""))
                (:target_tenses params))

        ]

    (log/debug (str "Editing game with id= " game-id))
    (log/debug (str "Lexical specs: " target-lexical-specs))
    (log/debug (str "Target tenses: " (:target_tenses params)))
    (log/debug (str "Tense specs: " (string/join "," target-tenses-as-specs)))

    (let [target-lex-as-specs
          (str "ARRAY["
               (string/join ","
                            (map (fn [target-lexical-spec]
                                   (str "'"
                                        (json/write-str target-lexical-spec)
                                        "'"))
                                 target-lexical-specs))
               "]::jsonb[]")

          target-tenses-as-specs
          (str "ARRAY["
               (string/join ","
                            (map (fn [target-tense-spec]
                                   (str "'"
                                        target-tense-spec
                                        "'"))
                                 target-tenses-as-specs))
               "]::jsonb[]")

          sql (str "UPDATE game "
                   "SET (name,source,target,target_lex,target_grammar,city) "
                   "= (?,?,?," target-lex-as-specs "," target-tenses-as-specs ",?) WHERE id=?")]

      (log/trace (str "UPDATE sql: " sql))
      (if dump-sql
        {:headers {"Content-type" "application/json;charset=utf-8"}
         :body (json/write-str {:sql sql
                                :params params
                                :name (:name params)
                                :source (:source params)
                                :target (:target params)
                                :game-id (Integer. game-id)})}
          (do
            (k/exec-raw [sql
                         [(:name params)
                          (:source params)
                          (:target params)
                          (:city params)
                          (Integer. game-id)]]))))))

(defn game-id-to-game [game-id]
  (first (k/exec-raw ["SELECT game.id AS game_id,
                                         source_groupings,
                                         target_groupings,
                                         source_lex,
                                         source_grammar,
                                         target_lex,
                                         target_grammar,
                                         name,
                                         source,
                                         target,
                                         active,
                                         to_char(game.created_on, ?) AS created_on,
                                         trim(vc_user.given_name || ' ' || vc_user.family_name) AS created_by,
                                         city
                                    FROM game
                               LEFT JOIN vc_user
                                      ON (vc_user.id = game.created_by)
                                   WHERE game.id=?"
                                 [time-format game-id]] :results)))

(defn show-game [game-id & [ {refine :refine
                              class :class
                              show-as-teacher? :show-as-teacher?
                              show-as-owner? :show-as-owner?} ]]
  (let [game-id (Integer. game-id)
        ;; get game and user info
        game (first (k/exec-raw ["SELECT game.id AS game_id,
                                         source_groupings,
                                         target_groupings,
                                         source_lex,
                                         source_grammar,
                                         target_lex,
                                         target_grammar,
                                         name,
                                         source,
                                         target,
                                         active,
                                         to_char(game.created_on, ?) AS created_on,
                                         trim(vc_user.given_name || ' ' || vc_user.family_name) AS created_by,
                                         city
                                    FROM game
                               LEFT JOIN vc_user
                                      ON (vc_user.id = game.created_by)
                                   WHERE game.id=?"
                                 [time-format game-id]] :results))]
    (if (nil? game)
      ""
      (let [
        created-on (:created_on game)
        owner-info (first (k/exec-raw ["SELECT vc_user.email,
                                           trim(vc_user.given_name || ' ' || vc_user.family_name) AS owner
                                          FROM vc_user
                                    INNER JOIN game 
                                            ON (vc_user.id = game.created_by)
                                           AND (game.id = ?)"
                                       [game-id]] :results))
        owner (:owner owner-info)
        email (:email owner-info)
        refine (if refine (json-read-str refine) nil)]
        (html
       [:div {:style "float:left;width:100%;margin-top:1em"}
        [:button {:onclick (str "document.location='/tour/"
                                game-id "';")} "Play"]
        [:div {:style "border:0px dashed green;float:right;width:50%;"}
         (if show-as-owner?
           (game-editor-form game nil nil nil class)

           ;; else, not owner:
           (html
            [:div {:style "width:95%;margin:2%;float:left"}
             [:h2 "Game info"]
             
             [:table {:class "striped"}
              [:tr
               [:th "Owner"]

               (if (not show-as-owner?)
                 [:td
                  (if (not (empty? owner))
                    owner
                    (if (not (empty? email))
                      email
                      [:i "no owner"]))])
               ]
              [:tr
               [:th "Created On"]
               [:td (if created-on created-on [:i "No creation date"])]
               ]
              ]
           
             (if show-as-teacher?
               [:div {:style "width:100%;float:left;margin-top:1em"}
                [:p
                 "You can copy this game to edit a copy of it:"]
                [:form {:method "post"
                        :action (str "/game/clone/" game-id)}
                 [:button {:onclick "submit();"}
                  "Copy"
                  ]]
                ])
             
             ]

            ))]
            

        [:div {:style "border:0px dashed green;float:left;width:50%"}

         (if refine
           (html
            [:h3 (describe-spec refine)]
            [:div.spec (html/tablize refine)]
            (source-to-target-mappings game-id refine)))

         [:h3 "Verbs"]

         (verb-tense-table game {:refine refine})

         ;; TOFINISH: working on making a form that lets us submit stuff like:
         ;; (populate 50 en/small it/small (unify (pick one <target_grammar>) (pick one <target lex>)))
         ;; (populate 50 en/small it/small {:root {:italiano {:italiano "esprimere"}}})

        ]])))))

(defn source-to-target-mappings [game-id spec]
  (let [show-edit-buttons false
        sql
           "SELECT source.surface AS source,
                   array_sort_unique(array_agg(target.surface)) AS targets
              FROM game
        RIGHT JOIN expression AS source
                ON source.language = game.source
        RIGHT JOIN expression AS target
                ON target.language = game.target
               AND ((source.structure->'synsem'->'sem') @> (target.structure->'synsem'->'sem'))
               AND target.structure @> ?::jsonb
             WHERE game.id=?
          GROUP BY source.surface
          ORDER BY source"

        results (k/exec-raw [sql
                             [(json/write-str spec)
                              game-id] ]
                            :results)
        ]
    
    (if (empty? results)
      (html [:div [:p "No matches."]])
      (html
       [:table {:class "striped padded"}

        [:tr
         [:th {:style "width:1em;"}]
         (if show-edit-buttons [:th "" ])
         [:th "Source"]
         [:th "Targets"]
         (if show-edit-buttons [:th "" ])
         ]
          
        (map (fn [result]
               [:tr
                [:th {:style "text-align:right"} (first result)]

                (if show-edit-buttons
                  [:td
                   [:button {:disabled "disabled"} "Delete"]
                   ])

                [:td
                 (:source (second result))]

                [:td (string/join "," (.getArray (:targets (second result))))]

                (if show-edit-buttons
                  [:td
                   [:button {:disabled "disabled"} "Delete"]
                   ])
                ]
               )
             (sort
              (zipmap
               (range 1 (.size results) 1)
               results)))]))))

(defn verb-tense-table [game & [ {refine :refine}]]
  (let [language-short-name (:target game)
        language-keyword-name
        (str "" (sqlname-from-match (short-language-name-to-long language-short-name)) "")

        language-keyword (keyword language-keyword-name)

        sort-by-human-readable-name-of-tense (partial sort-by #(tenses-human-readable %))
        
        ;; TODO: sort by tenses-human-readable for each element.
        tenses (sort-by-human-readable-name-of-tense
                (map (fn [element]
                       (json-read-str element))
                      (.getArray (:target_grammar game))))
        
        lexemes-for-this-game
        (sort
         (let [lexemes (remove #(= % "{}") (.getArray (:target_lex game)))]
           (zipmap
            (range 0 (.size lexemes))
            (map (fn [x] x) lexemes))))]

    (if (empty? lexemes-for-this-game)
      (html [:i "No verbs and tenses chosen yet for this game."])

      (html

       [:table {:class "striped padded"}

        ;; <header row>
        [:tr
         [:th {:style "width:1em"}]
         [:th {:style "text-align:left"} "Verb"]

         ;; create the table's header row: one <th> per verb tense.
         (map (fn [grammar-spec]
                [:th.count (string/capitalize
                            (let [tenses-human-readable
                                  (get tenses-human-readable
                                       grammar-spec)]
                              (if tenses-human-readable
                                tenses-human-readable "")))])
              tenses)]
        ;; </header row>

        (map (fn [lexeme-spec]
               (let [lexeme-specification-json (second lexeme-spec)
                     lexeme-index (first lexeme-spec)
                     lexeme (str (get-in (json-read-str lexeme-specification-json)
                                         (language-to-spec-path language-short-name)))
                     lexeme-specification (json-read-str (second lexeme-spec)) 
                     ]

                 [:tr
                  [:th {:style "text-align:right"} (+ 1 lexeme-index)]
                  [:td lexeme]

                  (map (fn [tense-spec-and-index]
                         (do
                           (log/trace (str "tense-spec-and-index: " tense-spec-and-index))
                           (let [tense-spec (first tense-spec-and-index)
                                 tense-index (second tense-spec-and-index)
                                 tense-specification-json tense-spec
                                 tense-specification tense-specification-json
                                 tense (string/replace
                                        (let [tense-spec
                                              (get-in tense-specification
                                                      [:synsem :sem :tense])]
                                          (if tense-spec tense-spec ""))
                                        ":" "")
                                 refine-param ;; combine the tense and lexeme together.
                                 (unify 
                                  tense-spec
                                  lexeme-specification)
                                 ]
                             [:td
                              {:class (if (= refine-param refine)
                                        "selected count"
                                        "count")}
                              (let [dom-id (str "count-of-lex-" lexeme-index "-and-tense-"tense-index)]
                                [:span {:id dom-id
                                        :class "fa fa-spinner fa-spin"
                                        }
                                 
                                 [:script {:type "text/javascript"}
                                  (str "counts_per_verb_and_tense('" dom-id "'," (:game_id game) "," lexeme-index "," tense-index ");")
                                  ]
                                 ])]
                             )))
                       ;; end of map fn over all the possible tenses.

                       ;; create a list of pairs: <0,tense0>, <1,tense1>... for
                       ;; the list of tenses. (sort-by second..) assures the 0,1,2,.. ordering.
                       (sort-by second
                                (zipmap
                                 tenses
                                 (range 0 (.size tenses)))))

                  ] ;; :tr
                 )

               ) ;; end of map fn over all the possible lexemes for this game.
             
             ;; all possible lexemes for this game.
             lexemes-for-this-game)]))))

;; TODO: has fallen victim to parameter-itis (too many unnamed parameters)
(defn game-editor-form [game game-to-edit game-to-delete & [editpopup class] ]
  (let [game-id (:game_id game)
        language (:language game)
        debug (log/trace (str "ALL GAME INFO: " game))
        game-to-edit game-to-edit
        problems nil ;; TODO: should be optional param of this (defn)
        game-to-delete game-to-delete

        source-group-ids (:source_group_ids game)
        source-groups (if source-group-ids
                        (vec (remove #(or (nil? %)
                                          (= "" (string/trim (str %))))
                                     (.getArray (:source_group_ids game))))
                        [])
        target-group-ids (:target_group_ids game)
        target-groups (if target-group-ids
                        (vec (remove #(or (nil? %)
                                          (= "" (string/trim (str %))))
                                     (.getArray (:target_group_ids game))))
                        [])
        debug (log/debug (str "creating checkbox form with currently-selected source-groups: " source-groups))
        debug (log/debug (str "creating checkbox form with currently-selected target-groups: " target-groups))

        language-short-name (:target game)
        debug (log/debug (str "language-short-name:" language-short-name))
        language-keyword-name
        (str "" (sqlname-from-match (short-language-name-to-long language-short-name)) "")

        language-keyword
        (keyword language-keyword-name)

        debug (log/debug (str "language-keyword-name: " language-keyword-name))
        debug (log/debug (str "language-short-name: " language-short-name))

        ]
    [:div
     {:class (str "editgame " (if editpopup "editpopup"))
      :id (str "editgame" game-id)}

     [:div {:style "float:left;width:100%"}
      [:table
       [:tr
        [:th "Language"]
        [:td (string/capitalize language-keyword-name)]]
       [:tr
        [:th "Created"]
        [:td (:created_on game)]
       [:tr
        [:th "Created by"]
        [:td (:created_by game)]
        ]]]]
     
     (f/render-form
      {:action (str "/game/" game-id "/edit")
       :enctype "multipart/form-data"
       :method :post
       :fields (concat

                [{:name :name :size 50 :label "Name"}]
                [{:name :class :size 50 :type :hidden}]

                [{:name :city
                  :label "City"
                  :type :radio
                  :options (cond
                            (= language-short-name "es")
                            (map (fn [city]
                                   (merge
                                    {:value city :label city}
                                    (if (= (:city game) city)
                                      {:checked :checked}
                                      {})))
                                 ["Barcelona" "México D.F."])

                            (= language-short-name "fr")
                            (map (fn [city]
                                   (merge
                                    {:value city :label city}
                                    (if (= (:city game) city)
                                      {:checked :checked}
                                      {})))
                                 ["Paris"])

                            (= language-short-name "it")
                            (map (fn [city]
                                   (merge
                                    {:value city :label city}
                                    (if (= (:city game) city)
                                      {:checked :checked}
                                      {})))
                                 ["Firenze"]))}]

                [{:name :checkallverbs
                  :type :checkbox
                  :id "toggleverbs"
                  :label "Check All Verbs"
                  :onclick "checkAllVerbs(this);"}]

                [{:name :target_lex
                  :label "Verbs"
                  :disabled "disabled"
                  :id "verbcheckboxes"
                  :type :checkboxes
                  :cols 10
                  :options (map (fn [lexeme]
                                  {:label lexeme
;                                   :options {"disabled" "disabled"}
                                   :value lexeme})
                                (map #(:surface %)
                                     (k/exec-raw [(str "SELECT DISTINCT canonical AS surface
                                                          FROM lexeme 
                                                         WHERE language=?
                                                           AND structure->'synsem'->>'cat' = 'verb'
                                                           AND structure->'synsem'->>'infl' = 'top'
                                                      ORDER BY surface ASC")
                                                  [language-short-name]]
                                                  :results)))}]

                [{:name :target_tenses
                  :label "Tenses"
                  :type :checkboxes
                  :cols 12
                  :options (map (fn [row]
                                  {:label (:label row)
                                   :value (:value row)})
                                (cond (= language-short-name "fr")
                                      tenses-as-editables-french
                                      true
                                      tenses-as-editables))}]

                [{:name :source :type :hidden
                  :label "Source Language"
                  :options [{:value "en" :label "English"}
                            {:value "it" :label "Italian"}
                            {:value "es" :label "Spanish"}]}]

                [{:name :target :type :hidden
                  :label "Target Language"
                  :options [{:value "en" :label "English"}
                            {:value "it" :label "Italian"}
                            {:value "es" :label "Spanish"}]}]

                )

       :cancel-href (str "/" language)
       :values {:name (:name game)
                :target (:target game)
                :source (:source game)
                :class class
                :city (:city game)
                :source_groupings source-groups
                :target_groupings target-groups

                :target_lex (vec (remove #(or (nil? %)
                                              (= "" %))
                                         (map #(let [path [:root language-keyword language-keyword]
                                                     debug (log/trace (str "CHECKBOX TICK (:target_lex) (path=" path ": ["
                                                                           (get-in % path nil) "]"))]
                                                 (get-in % path nil))
                                              (map json-read-str (seq (.getArray (:target_lex game)))))))


                :target_tenses (vec (remove #(or (nil? %)
                                                 (= "" %))
                                            (map #(let [debug (log/debug (str "tense: " %))
                                                        data (json-read-str %)]
                                                    (log/trace (str "target tense spec: " data))
                                                    (json/write-str data))
                                                 (seq (.getArray (:target_grammar game))))))
                }

       :validations [[:required [:name]
                      :action "/game"
                      :method "post"
                      :problems problems]]})

     [:div.editgame
      [:p "You can make a new copy of this game."]
      [:form {:method "post"
              :action (str "/game/clone/" game-id)}
       [:button {:onclick "submit();"}
        "Copy"
        ]]]
     
     [:div.dangerzone
      [:h4 "Delete game"]

      [:div {:style "float:right"}
       [:form
        {:method "post"
         :action (str "/game/delete/" (if language (str language "/")) game-id)}
        [:button.confirm_delete {:onclick (str "submit();")} "Delete Game"]]]
      ]
     ]))

(defn onload []
  (string/join " "
               (map (fn [game]
                      (let [game-id (:game game)
                            source (:source game)
                            target (:target game)]
                        (str
                         "log(INFO,'editor onload: loading gen_per_verb( " game-id " )');"
                         "gen_per_verb('" game-id "','" source "','" target "');")))
                    (k/exec-raw [(str "SELECT games_to_use.game,games.source,games.target
                                         FROM games_to_use
                                   INNER JOIN games
                                           ON games_to_use.game = games.id")] :results))))

(defn games-per-class [class-id]
  (let [results (k/exec-raw
                 ["SELECT 'resume',game.id,game.name AS game,city,last_move AS position,
                          game.target AS language,class.name AS class,class.id AS class_id
                     FROM game
                LEFT JOIN student_in_game
                       ON (student_in_game.game = game.id)
               INNER JOIN game_in_class
                       ON (game_in_class.game = game.id)
                LEFT JOIN class
                       ON (class.id = game_in_class.class)
                LEFT JOIN student_in_class
                       ON (student_in_class.class = game_in_class.class)
                    WHERE class.id = ?"
                  [class-id]] :results)]
    results))

(defn games-per-user [user-id]
  (let [results (k/exec-raw
                 ["SELECT 'resume',game.id,game.name AS game,city,last_move AS position,
                          game.target AS language,class.name AS class,class.id AS class_id
                     FROM game
                LEFT JOIN student_in_game
                       ON (student_in_game.game = game.id)
               INNER JOIN game_in_class
                       ON (game_in_class.game = game.id)
                LEFT JOIN class
                       ON (class.id = game_in_class.class)
                LEFT JOIN student_in_class
                       ON (student_in_class.class = game_in_class.class)
                    WHERE (student_in_class.student = ?)
                      AND (student_in_game.student = ? OR student_in_game.student IS NULL)"
                  [user-id]] :results)]
    results))

(defn tenses-of-game-as-human-readable [tenses]
  (map #(html [:b (str %)])
       (remove nil?
               (string/join ", "
                            (map #(get tenses-human-readable %)
                                 (map json-read-str
                                      (.getArray tenses)))))))

(defn verbs-of-game-as-human-readable [verbs language]
  "Convert JSON-formatted verbs in a human readable, non-JSON format"
  (let [language-short-name language
        language-keyword-name
        (sqlname-from-match (short-language-name-to-long language-short-name))
        language-keyword (keyword language-keyword-name)]
    (string/join ", "
                 (map #(html [:i %])
                      (map #(get-in % [:root language-keyword language-keyword])
                           (map json-read-str
                                (.getArray verbs)))))))
