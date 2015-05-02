(ns italianverbs.editor
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as string]

   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]

   [formative.core :as f]

   [italianverbs.auth :refer [is-admin]]
   [italianverbs.borges.reader :refer :all]
   [italianverbs.borges.writer :refer [populate populate-from]]
   [italianverbs.html :as html]
   [italianverbs.unify :refer [get-in strip-refs unify]]

   [hiccup.core :refer (html)]
   [korma.core :as k]))

(declare banner)
(declare body)
(declare delete-game)
(declare expressions-for-game)
(declare game-editor-form)
(declare get-game-from-db)
(declare headers)
(declare insert-game)
(declare json-read-str)
(declare language-to-spec-path)
(declare multipart-to-edn)
(declare onload)
(declare series)
(declare show-games)
(declare short-language-name-to-edn)
(declare short-language-name-to-long)
(declare short-language-name-from-match)
(declare show-game)
(declare source-to-target-mappings)
(declare sqlname-from-match)
(declare tenses-as-editables)
(declare tenses-human-readable)
(declare toggle-activation)
(declare update-game)
(declare verb-tense-table)

(def routes
  (compojure/routes
   (GET "/" request
        (is-admin {:body (body "" (show-games request) request)
                   :status 200
                   :headers headers}))

   ;; alias for '/editor' (above)
   (GET "/home" request
        (is-admin
         {:status 302
          :headers {"Location" "/editor"}}))

   ;; language-specific: show only games and lists appropriate to a given language
   (GET "/:language" request
        (is-admin {:body (body (str (short-language-name-to-long (:language (:route-params request))))
                               (show-games (conj request
                                                {:language (:language (:route-params request))}))
                               request)
                   :status 200
                   :headers headers}))

   (GET "/:language/" request
         {:status 302
          :headers {"Location" (str "/editor/" (:language (:route-params request)))}})

   (POST "/game/activate/:game" request
         ;; toggle activation of this game
         (is-admin
          (let [debug (log/debug (str "PARAMS: " (:params request)))
                message (toggle-activation (:game (:route-params request)) (= "on" (:active (:params request))))
                language (:language (:params request))]
            {:status 302
             :headers {"Location" (str "/editor/" language "?message=" message)}})))

   (GET "/game/delete/:game-to-delete" request
        (is-admin
         (let [game-to-delete (:game-to-delete (:route-params request))]
           {:body (body (str "Editor: Confirm: delete game: " game-to-delete)
                        (show-games {:game-to-delete game-to-delete}) 
                        request)
            :status 200
            :headers headers})))

   (POST "/game/delete/:game-to-delete" request
         (is-admin
          (let [message (delete-game (:game-to-delete (:route-params request)))]
            {:status 302
             :headers {"Location" (str "/editor/" "?message=" message)}})))

   (POST "/game/edit/:game-to-edit" request
         (do (log/debug (str "Doing POST /game/edit/:game-to-edit with request: " request))
             (is-admin (let [game-id (:game-to-edit (:route-params request))]
                         (update-game (:game-to-edit (:route-params request))
                                      (multipart-to-edn (:multipart-params request)))
                         {:status 302
                          :headers {"Location" (str "/editor/game/" game-id "?message=Edited+game:" game-id)}}))))

   (GET "/game/:game" request
        (is-admin
         (let [game-id (Integer. (:game (:route-params request)))
               game (first (k/exec-raw ["SELECT * FROM game WHERE id=?" [(Integer. game-id)]] :results))]
           {:body (body (:name game) (show-game (:game (:route-params request))
                                                {:refine (:refine (:params request))})
                        request)
            :status 200
            :headers headers})))

   (POST "/game/new" request
         (is-admin
          (let [params (multipart-to-edn (:multipart-params request))
                language (cond (and (:language params)
                                    (not (= (string/trim (:language params)) "")))
                               (:language params)

                               true
                               (do (log/debug (str "POST /game/new: trying to guess language from game's name: " (:name params)))
                                   (let [result (short-language-name-from-match (:name params))]
                                     (log/debug (str "guess was: " result))
                                     result)))]
            ;; Defaults: source language=English.
            (insert-game (:name params) "en" language
                         (:source_groupings params)
                         (:target_groupings params))
            {:status 302 :headers {"Location" (str "/editor/" language)}})))))

(defn body [title content request]
  (let [language (:language (:route-params request))
        game-id (:game (:route-params request))
        game (if game-id (first (k/exec-raw ["SELECT * FROM game WHERE id=?" [(Integer. game-id)]] :results)))
        language (if language language
                     (:target game))]
    (html/page
     title
     (html
      [:div {:class "major"}
       [:h2 
        (banner (concat 
                 [{:href "/editor" 
                   :content "Game Editor"}]
                 (if language
                   [{:href (str "/editor/" language)
                     :content (short-language-name-to-long language)}])
                 (if game
                   [{:href (if (:refine (:params request))
                             (str "/editor/game/" game-id))
                     :content (if (= "" (string/trim title)) "(untitled)" title)}])

                 (if (:refine (:params request))
                   [{:href nil
                     :content "Refine"}])))]

      content])
     request
     {:css "/css/editor.css"
      :jss ["/js/editor.js" "/js/gen.js"]
      :onload (onload)})))

(defn show-games [ & [ {editor-is-popup :editor-is-popup
                        game-to-edit :game-to-edit
                        game-to-delete :game-to-delete
                        name-of-game :name-of-game
                        language :language} ]]
  (let [show-source-lists false
        game-to-edit (if game-to-edit (Integer. game-to-edit))
        game-to-delete (if game-to-delete (Integer. game-to-delete))
        language (if language language "")
        debug (log/debug (str "THE LANGUAGE OF THE GAME IS: " language))

        ;; TODO: doing SELECT DISTINCT surface,structure is too expensive below and gets worse as the number of games increase,
        ;; but not doing DISTINCT is inaccurate.
        sql "SELECT game.name AS name,game.id AS id,active,
                    source,target,
                    target_lex,target_grammar,counts.expressions_per_game
               FROM game
          LEFT JOIN (SELECT game.id AS game,
                            count(*) AS expressions_per_game
                       FROM (SELECT surface,structure
                               FROM expression) AS expression
                 INNER JOIN game
                         ON structure @> ANY(target_lex)
                        AND structure @> ANY(target_grammar)
                   GROUP BY game.id) AS counts
                 ON (counts.game = game.id)
              WHERE ((game.target = ?) OR (? = ''))
           ORDER BY game.name"
        debug (log/debug (str "GAME-CHOOSING LANGUAGE: " language))
        debug (log/debug (str "GAME-CHOOSING SQL: " sql))
        results (k/exec-raw [sql
                             [language language] ]
                            :results)]
    (html

     [:div.dropdown {:style "margin-left:0.5em;float:left;width:auto"}
      "Show games for:"
      [:div {:style "float:right;padding-left:1em;width:auto;"}
       [:form {:method "get"}
        [:select#edit_which_language {:name "language"
                                      :onchange (str "document.location='/editor/' + this.value")}
         (map (fn [lang-pair]
                (let [label (:label lang-pair)
                      value (:value lang-pair)]
                  [:option (conj {:value value}
                                 (if (= language (:value lang-pair))
                                   {:selected "selected"}
                                   {}))
                   label]))
              [{:value "" :label "All Languages"}
               {:value "es" :label "Español"}
               {:value "it" :label "Italiano"}])]]]]

     [:table {:class "striped padded"}
      [:tr
       [:th {:style "width:2em"} "Active?"]
       [:th {:style "width:15em"} "Name"]
       [:th {:style "width:auto"} "Verbs"]
       [:th {:style "width:auto"} "Tenses"]
       [:th {:style "width:auto;text-align:right"} "Count"]
       ]

      (map
       (fn [result]
             (let [game-name-display
                   (let [game-name (string/trim (:name result))]
                     (if (= game-name "")
                       "(untitled)"
                       game-name))
                   game-id (:id result)
                   language-short-name (:target result)
                   debug (log/debug (str "language-short-name:" language-short-name))
                   language-keyword-name
                   (str "" (sqlname-from-match (short-language-name-to-long language-short-name)) "")
                   language-keyword
                   (keyword language-keyword-name)]

               [:tr

                [:td
                 [:form {:method "post"
                         :enctype "multipart/form-data"
                         :action (str "/editor/game/activate/" game-id)}
                  [:input {:name "language"
                           :type "hidden"
                           :value language-short-name}]
                  [:input (merge {:type "checkbox"
                                  :onclick "submit()"
                                  :name "active"}
                                 (if (:active result)
                                   {:checked "on"}))]]]

                [:td
                 (if (= game-to-edit game-id)
                   [:input {:size (+ 5 (.length (:name result)))
                            :value (:name result)}]
                   (if (not editor-is-popup)
                     ;; show as link
                     [:a {:href (str "/editor/game/" game-id)} game-name-display]

                     ;; show as popup
                     [:div.edit_game {:onclick (str "edit_game_dialog(" game-id ")")} game-name-display]))]

                [:td (string/join ", " (map #(html [:i %])
                                           (map #(get-in % [:head language-keyword language-keyword])
                                                (map json-read-str (.getArray (:target_lex result))))))]

                [:td (string/join ", " (map #(html [:b (str %)])
                                            (remove nil?
                                                    (map #(get tenses-human-readable %)
                                                         (map json-read-str (.getArray (:target_grammar result)))))))]


                [:td {:style "text-align:right"} [:a {:href (str "/editor/game/" game-id)   }
                                                  (if (nil? (:expressions_per_game result))
                                                    0
                                                    (:expressions_per_game result))]]

                ]
               ))
           results)]

     (if (not (= "" language)) ;; don't show "New Game" if no language - confusing to users.
       [:div.new
        [:form {:method "post"
                :enctype "multipart/form-data"
                :action "/editor/game/new"}

         [:input {:onclick "submit_new_game.disabled = false;"
                  :name "name" :size "50" :placeholder (str "Type the name of a new " (short-language-name-to-long language) " game")} ]
         [:input {:type "hidden" :name "language" :value language} ]

         [:button {:name "submit_new_game" :disabled true :onclick "submit();"} "New Game"]
         ]])

     ;; make the hidden game-editing forms.
     (map (fn [result]
            (game-editor-form result game-to-edit game-to-delete true))
          results))))

(defn show-game [game-id & [ {refine :refine} ]]
  (let [game-id (Integer. game-id)
        game (first (k/exec-raw ["SELECT * FROM game WHERE id=?" [(Integer. game-id)]] :results))
        refine (if refine (read-string refine) nil)]
    (html
     (let [

           grouped-by-source-sql
           "SELECT (array_agg(target.structure->'head'->'italiano'->'italiano'))[1] AS infinitive,
                   source.surface AS source,
                   array_sort_unique(array_agg(target.surface)) AS targets
              FROM game
        RIGHT JOIN expression AS source
                ON source.language = game.source
        RIGHT JOIN expression AS target
                ON target.language = game.target
               AND ((source.structure->'synsem'->'sem') @> (target.structure->'synsem'->'sem'))
               AND target.structure @> ANY(game.target_lex)
               AND target.structure @> ANY(game.target_grammar)
             WHERE game.id=?
          GROUP BY source.surface
          ORDER BY infinitive"

           grouped-by-source-results (k/exec-raw [grouped-by-source-sql
                                                  [game-id] ]
                                                 :results)

           ]

       [:div {:style "float:left;width:100%;margin-top:1em"}

        [:div {:style "border:0px dashed green;float:right;width:50%;"}
         (if game (game-editor-form game nil nil))]

        [:div {:style "border:0px dashed green;float:left;width:50%"}

         (if refine
           (html
            [:h3 "Refine"]
            [:div.spec
             (html/tablize refine)]

            (source-to-target-mappings game-id refine)

            [:button {:disabled "disabled"} "Generate more.."]))

         [:h3 "Verb/Tense Table"]

         (verb-tense-table game {:refine refine})

         [:h3 "Expressions"]

         (expressions-for-game game-id)

         ;; TOFINISH: working on making a form that lets us submit stuff like:
         ;; (populate 50 en/small it/small (unify (pick one <target_grammar>) (pick one <target lex>)))
         ;; (populate 50 en/small it/small {:head {:italiano {:italiano "esprimere"}}})

         ;; <form>
         [:div {:style "border:0px dashed blue;float:left;display:block"}
          (let [game (get-game-from-db game-id)]

            
            [:table

             [:tr
              [:td {:colspan "2"}

               [:textarea  {:style "font-family:monospace" :cols "40" :rows "10"}
                (str "(take 10 (repeatedly #(populate-from " 
                     (:source game) "/small " (:target game) "/small "
                     "(:target_lex (get-game-from-db " game-id "))"
                     "(:target_grammar (get-game-from-db " game-id ")))))"
                     )
                ]]]

             [:tr
              [:th "target_grammar"]
              [:td
               [:textarea {:style "font-family:monospace" :rows "10"}
                (string/join "," (:target_grammar game))]]]

             [:tr
              [:th "target_lex"]
              [:td
               [:textarea {:style "font-family:monospace" :rows "10"}
                (string/join "," (:target_lex game))]]]

             [:tr
              [:th "source"]
              [:td (:source game)]]

             [:tr
              [:th "target"]
              [:td (:target game)]]


             ])]

         ;; </form>



         ]]))))

(def headers {"Content-Type" "text/html;charset=utf-8"})

(defn source-to-target-mappings [game-id spec]
  (let [sql
           "SELECT source.surface AS source,
                   (array_agg(target.structure->'head'->'italiano'->'italiano'))[1] AS infinitive,
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
          ORDER BY infinitive"

        results (k/exec-raw [sql
                             [(json/write-str spec)
                              game-id] ]
                            :results)]
    (html
     [:table {:class "striped padded"}

      [:tr
       [:th {:style "width:1em;"}]
       [:th "Source"]
       [:th "Targets"]
       ]
          
      (if results
        (map (fn [result]
               [:tr
                [:th (first result)]
                [:td
                 (:source (second result))]
                [:td
                 (string/join "," (.getArray (:targets (second result))))]
                
                ]
               )
             (sort
              (zipmap
               (series 1 (.size results) 1)
               results))))])))

(defn expressions-for-game [game-id]
  (let [grouped-by-source-sql
           "SELECT (array_agg(target.structure->'head'->'italiano'->'italiano'))[1] AS infinitive,
                   source.surface AS source,
                   array_sort_unique(array_agg(target.surface)) AS targets
              FROM game
        RIGHT JOIN expression AS source
                ON source.language = game.source
        RIGHT JOIN expression AS target
                ON target.language = game.target
               AND ((source.structure->'synsem'->'sem') @> (target.structure->'synsem'->'sem'))
               AND target.structure @> ANY(game.target_lex)
               AND target.structure @> ANY(game.target_grammar)
             WHERE game.id=?
          GROUP BY source.surface
          ORDER BY infinitive"

        grouped-by-source-results (k/exec-raw [grouped-by-source-sql
                                               [game-id] ]
                                              :results)]

         ;; <grouped by source>
         [:table {:class "striped padded"}

          [:tr
           [:th {:style "width:1em;"}]
           [:th "Infinitive"]
           [:th "Source"]
           [:th "Targets"]
           ]
          
          (if grouped-by-source-results
            (map (fn [result]
                   [:tr
                    [:th (first result)]
                    [:th
                     (string/replace (:infinitive (second result)) "\"" "")]
                    [:td
                     (:source (second result))]
                    [:td
                     (string/join "," (.getArray (:targets (second result))))]
                    
                    ]
                   )
                 (sort
                  (zipmap
                   (series 1 (.size grouped-by-source-results) 1)
                   grouped-by-source-results))))]

         ;; </grouped by source>

))


(defn verb-tense-table [game & [ {refine :refine}]]
  (let [language-short-name (:target game)

        language-keyword-name
        (str "" (sqlname-from-match (short-language-name-to-long language-short-name)) "")

        language-keyword (keyword language-keyword-name)]

    (html

     [:table.tense
      [:tr
       [:th {:style "width:1em"}]
       [:th {:style "text-align:center"} "Verb"]
       
       (map (fn [grammar-spec]
              [:th (string/capitalize 
                    (get tenses-human-readable
                         grammar-spec))])
            (map json-read-str (.getArray (:target_grammar game))))]
      (map (fn [lexeme-spec]
             
             (let [lexeme-specification-json (second lexeme-spec)
                   lexeme (str (get-in (json-read-str lexeme-specification-json)
                                       (language-to-spec-path language-short-name)))
                   lexeme-specification (json-read-str (second lexeme-spec)) 
                   ]

               [:tr
                [:th (first lexeme-spec)]
                [:td lexeme]

                (map (fn [tense-spec]
                       (let [tense-specification-json tense-spec
                             tense-specification (json-read-str tense-specification-json)
                             tense (string/replace 
                                    (get-in tense-specification
                                            [:synsem :sem :tense]) ":" "")
                             refine-param ;; combine the tense and lexeme together.
                             (unify 
                              (json-read-str tense-spec)
                              lexeme-specification)]
                         [:td
                          {:class (if (= refine-param refine)
                                    "selected count" "count")}
                                      
                          [:a {:href (str "/editor/game/" (:id game) "?refine=" refine-param)}
                           (str (:count (first (k/exec-raw ["SELECT count(*) 
                                                          FROM expression 
                                                         WHERE structure @> ?::jsonb 
                                                           AND structure @> ?::jsonb"
                                                            [tense-spec
                                                             (second lexeme-spec)] ] :results))))]
                          
                          ])) ;; end of map fn over all the possible tenses.

                     ;; coerce the database's value (of type jsonb[]) into a Clojure array.
                     (map (fn [x] x) (.getArray (:target_grammar game))))

                ] ;; :tr
               )

             ) ;; end of map fn over all the possible lexemes.

           (sort
            (zipmap
             (series 1 (.size (map (fn [x] x) (.getArray (:target_lex game)))) 1)
             (map (fn [x] x) (.getArray (:target_lex game))))))])))

(defn language-to-spec-path [short-language-name]
  "Take a language name like 'it' and turn it into an array like: [:head :italiano :italiano]."
  (let [language-keyword-name (sqlname-from-match (short-language-name-to-long short-language-name))
        language-keyword (keyword language-keyword-name)]
    [:head language-keyword language-keyword]))

;; TODO: throw exception rather than "???" for unknown languages.
(defn short-language-name-to-long [lang]
  (cond (= lang "it") "Italian"
        (= lang "en") "English"
        (= lang "es") "Spanish"
        true "???"))

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
        params (multipart-to-edn params)
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

        ;; wrap every lexeme in a {:head {<language> <lexeme}}.
        target-lexical-specs
        (map (fn [each-lexeme]
               {:head {language-name {language-name each-lexeme}}})
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
                   "SET (name,source,target,target_lex,target_grammar) "
                   "= (?,?,?," target-lex-as-specs "," target-tenses-as-specs ") WHERE id=?")]

      (log/debug (str "UPDATE sql: " sql))
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
                          (Integer. game-id)]]))))))

(defn multipart-to-edn [params]
  (log/debug (str "multipart-to-edn input: " params))
  (let [output
        (zipmap (map #(keyword %)
                     (map #(string/replace % ":" "")
                          (map #(string/replace % "[]" "")
                               (keys params))))
                (vals params))]
    (log/debug (str "multipart-to-edn output: " output))
    output))

;; TODO: throw exception rather than "(no shortname for language)"
(defn short-language-name-from-match [match-string]
  (cond (nil? match-string)
        (str "(no shortname for language: (nil) detected).")
        (re-find #"espanol" (string/lower-case match-string))
        "es"
        (re-find #"español" (string/lower-case match-string))
        "es"
        (re-find #"english" (string/lower-case match-string))
        "en"
       (re-find #"italiano" (string/lower-case match-string))
        "it"
        (re-find #"italian" (string/lower-case match-string))
        "it"
        (re-find #"spanish" (string/lower-case match-string))
        "es"
        :else
        (str "(no shortname for language:" match-string " detected.")))

(defn insert-game [name source target source-set target-set]
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
        sql (str "INSERT INTO game (name,source_groupings,target_groupings,source,target)
                       SELECT ?, " source-grouping-str "," target-grouping-str ",?,? RETURNING id")]
    (log/debug (str "inserting new game with sql: " sql))
    ;; return the row ID of the game that has been inserted.
    (:id (first (k/exec-raw [sql [name source target]] :results)))))

;; TODO: has fallen victim to parameteritis (too many unnamed parameters)
(defn game-editor-form [game game-to-edit game-to-delete & [editpopup] ]
  (let [game-id (:id game)
        language (:language game)
        debug (log/debug (str "ALL GAME INFO: " game))
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

        debug (log/debug
               (str "SELECT DISTINCT structure->'head'->" language-keyword-name "->" language-keyword-name "
                                            AS lexeme
                                          FROM expression
                                         WHERE language=" language-short-name "
                                      ORDER BY lexeme"))

        debug (log/debug (str "language-keyword-name: " language-keyword-name))
        debug (log/debug (str "language-short-name: " language-short-name))

        ]
    [:div
     {:class (str "editgame " (if editpopup "editpopup"))
      :id (str "editgame" game-id)}
;;     [:h2 (str "Editing game: " (:name game))]

     (f/render-form
      {:action (str "/editor/game/edit/" game-id)
       :enctype "multipart/form-data"
       :method :post
       :fields (concat

                [{:name :name :size 50 :label "Name"}]

                [{:name :target_lex
                  :label "Verbs"
                  :type :checkboxes
                  :cols 10
                  :options (map (fn [row]
                                  (let [lexeme (if (:lexeme row)
                                                 (:lexeme row)
                                                 "")
                                        lexeme (string/replace lexeme "\"" "")]
                                    {:label lexeme
                                     :value lexeme}))
                                ;; TODO: be more selective: show only infinitives, and avoid irregular forms.

                                (k/exec-raw [(str "SELECT DISTINCT lexeme
                                                              FROM (SELECT surface,structure->'head'->?->? AS lexeme,
                                                                           structure->'head'->?->'exception'::text AS exception
                                                                      FROM expression WHERE language=?) AS expression_heads
                                                                     WHERE expression_heads.exception IS NULL
                                                                       AND lexeme IS NOT NULL
                                                                  ORDER BY lexeme ASC")
                                             [language-keyword-name
                                              language-keyword-name
                                              language-keyword-name
                                              language-short-name]
                                             ]
                                            :results))}]

                [{:name :target_tenses
                  :label "Tenses"
                  :type :checkboxes
                  :cols 12
                  :options (map (fn [row]
                                  {:label (:label row)
                                   :value (:value row)})
                                tenses-as-editables)}]

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

       :cancel-href (str "/editor/" language)
       :values {:name (:name game)
                :target (:target game)
                :source (:source game)
                :source_groupings source-groups
                :target_groupings target-groups

                :target_lex (vec (remove #(or (nil? %)
                                              (= "" %))
                                         (map #(let [path [:head language-keyword language-keyword]
                                                     debug (log/debug (str "CHECKBOX TICK (:target_lex) (path=" path ": ["
                                                                           (get-in % path nil) "]"))]
                                                 (get-in % path nil))
                                              (map json-read-str (seq (.getArray (:target_lex game)))))))


                :target_tenses (vec (remove #(or (nil? %)
                                                 (= "" %))
                                            (map #(let [debug (log/debug (str "tense: " %))
                                                        data (json-read-str %)]
                                                    (log/debug (str "the data is: " data))
                                                    (json/write-str data))
                                                 (seq (.getArray (:target_grammar game))))))
                }

       :validations [[:required [:name]
                      :action "/editor"
                      :method "post"
                      :problems problems]]})

     [:div.dangerzone
      [:h4 "Delete game"]

      [:div {:style "float:right"}
       [:form
        {:method "post"
         :action (str "/editor/game/delete/" (if language (str language "/")) game-id)}
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

;; sqlnames: 'english','espanol' <- note: no accent on 'n' ,'italiano', ..
(defn sqlname-from-match [match-string]
  (cond (nil? match-string)
        (str "(no sqlname for language: (nil) detected).")
        (re-find #"espanol" (string/lower-case match-string))
        "espanol"
        (re-find #"español" (string/lower-case match-string))
        "espanol"
        (re-find #"english" (string/lower-case match-string))
        "english"
        (re-find #"italiano" (string/lower-case match-string))
        "italiano"
        (re-find #"italian" (string/lower-case match-string))
        "italiano"
        (re-find #"spanish" (string/lower-case match-string))
        "espanol"
        :else
        (str "(no sqlname for language:" match-string " detected.")))

(defn json-read-str [json]
  (json/read-str json
                 :key-fn keyword
                 :value-fn (fn [k v]
                             (cond
                              (and (or (= k :english)
                                       (= k :espanol)
                                       (= k :italiano))
                                   (not (map? v)))
                              (str v)

                              (and (string? v)
                                   (= (nth v 0) \:))
                              (keyword (string/replace-first v ":" ""))

                              (string? v)
                              (keyword v)
                              :else v))))

;; TODO: throw exception rather than "unknown language"
(defn short-language-name-to-edn [lang]
  (cond (= lang "it") :italiano
        (= lang "en") :english
        (= lang "es") :espanol
        true (str "unknown lang: " lang)))

(defn series [from to increment]
  (if (or (< from to) (= from to))
    (cons
     from
     (series (+ from increment) to increment))))

(defn banner [segments]
  (if (not (empty? segments))
    (html (if (:href (first segments)) 
            [:a {:href (:href (first segments))}
             (:content (first segments))]
            (:content (first segments)))
          (if (not (empty? (rest segments))) " : ")
          (banner (rest segments)))))

(def tenses-as-editables
  [{:label "conditional"
    :value (json/write-str {:synsem {:sem {:tense :conditional}}})}

   {:label "future"
    :value (json/write-str {:synsem {:sem {:tense :future}}})}

   {:label "imperfect"
    :value (json/write-str {:synsem {:sem {:tense :past
                                           :aspect :progressive}}})}
   {:label "past"
    :value (json/write-str {:synsem {:sem {:tense :past
                                           :aspect :perfect}}})}
   {:label "present"
    :value (json/write-str {:synsem {:sem {:tense :present}}})}])

(def tenses-human-readable
  {{:synsem {:sem {:tense :conditional}}} "conditional"
   {:synsem {:sem {:tense :future}}} "future"
   {:synsem {:sem {:tense :past :aspect :progressive}}} "imperfect"
   {:synsem {:sem {:tense :past :aspect :perfect}}} "past"
   {:synsem {:sem {:tense :present}}} "present"})

(defn get-game-from-db [game-id]
  (let [result (first (k/exec-raw ["SELECT * FROM game WHERE id=?" [game-id]] :results))]
    (merge (dissoc 
            (dissoc 
             (dissoc 
              (dissoc result :target_lex)
              :source_lex)
             :target_grammar)
            :source_grammar)
           {:source_grammar (map json-read-str (.getArray (:source_grammar result)))
            :target_grammar (map json-read-str (.getArray (:target_grammar result)))
            :target_lex (map json-read-str (.getArray (:target_lex result)))
            :source_lex (map json-read-str (.getArray (:source_lex result)))})))
