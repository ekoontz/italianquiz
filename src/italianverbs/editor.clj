(ns italianverbs.editor
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as string]

   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]

   [formative.core :as f]

   [italianverbs.authentication :as authentication]
   [italianverbs.borges.reader :refer :all]
   [italianverbs.html :as html]
   [italianverbs.lexiconfn :refer [infinitives]]
   [italianverbs.unify :refer [get-in strip-refs unify]]
   [italianverbs.user :refer [do-if do-if-admin username2userid]]
   [hiccup.core :refer (html)]
   [korma.core :as k]))

(declare banner)
(declare body)
(declare delete-game)
(declare describe-spec)
(declare expressions-for-game)
(declare game-editor-form)
(declare get-game-from-db)
(declare headers)
(declare insert-game)
(declare is-owner-of?)
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
        (do-if-admin
         {:body
          (body ""
                (show-games
                 (conj request
                       {:user-id (username2userid (authentication/current request))}))
                request)
                      :status 200
          :headers headers}))

   ;; alias for '/editor' (above)
   (GET "/home" request
        (do-if-admin
         {:status 302
          :headers {"Location" "/editor"}}))

   ;; language-specific: show only games and lists appropriate to a given language
   (GET "/:language" request
        (do-if-admin {:body (body (str (short-language-name-to-long (:language (:route-params request))))
                                  (show-games (conj request
                                                    {:user-id (username2userid (authentication/current request))
                                                     :language (:language (:route-params request))}))
                                  request)
                      :status 200
                      :headers headers}))

   (GET "/:language/" request
         {:status 302
          :headers {"Location" (str "/editor/" (:language (:route-params request)))}})

   (POST "/game/activate/:game" request
         ;; toggle activation of this game
         (do-if-admin
          (let [debug (log/debug (str "PARAMS: " (:params request)))
                message (toggle-activation (:game (:route-params request)) (= "on" (:active (:params request))))
                language (:language (:params request))]
            {:status 302
             :headers {"Location" (str "/editor/" language "?message=" message)}})))

   (POST "/game/clone/:game-id" request
         (let [game-id (:game-id (:route-params request))]
           {:status 302
            :headers {"Location" (str "/editor/game/" game-id)}}))
   
   (POST "/game/delete/:game-to-delete" request
         (do-if-admin
          (let [message (delete-game (:game-to-delete (:route-params request)))]
            {:status 302
             :headers {"Location" (str "/editor/" "?message=" message)}})))

   (POST "/game/edit/:game-to-edit" request
         (let [game-id (:game-to-edit (:route-params request))
               user (authentication/current request)]
           (do-if
            (fn []
              (log/debug (str "Edit game: " game-id ": can user: '" user "' edit this game?"))
              (is-owner-of? (Integer. game-id) user))
            (fn []
              (do
                (log/warn (str "User:" user " is authorized to update this game."))
                (update-game (:game-to-edit (:route-params request))
                             (multipart-to-edn (:multipart-params request)))
                {:status 302
                 :headers {"Location" (str "/editor/game/" game-id "?message=Edited+game:" game-id)}}))
            (fn []
              (do
                (log/warn (str "User:" user " tried to edit game: " game-id " but was denied authorization to do so."))
                {:status 302
                 :headers {"Location" (str "/editor/game/" game-id "?message=Unauthorized+to+edit+game:" game-id)}})))))

   (GET "/game/:game/:verb/:tense" request
        ;; Return translation pairs for this game's target and source language such that
        ;; the target member of the pair has the given verb and tense.
        (do-if-admin
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
            :headers headers})))

   (GET "/game/:game" request
        (do-if-admin
         (let [game-id (Integer. (:game (:route-params request)))
               game (first (k/exec-raw ["SELECT * FROM game WHERE id=?" [(Integer. game-id)]] :results))]
           {:body (body (:name game) (show-game (:game (:route-params request))
                                                {:show-as-owner? (is-owner-of?
                                                                 (Integer. game-id)
                                                                 (authentication/current request))
                                                 :refine (:refine (:params request))})
                        request)
            :status 200
            :headers headers})))

   (POST "/game/new" request
         (do-if-admin
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
            (let [game (insert-game (:name params) "en" language
                                    (:source_groupings params)
                                    (:target_groupings params))]
              {:status 302 :headers {"Location" 
                                     (str "/editor/game/" (:id game)
                                          "?message=Created game:" (:name game))}}))))))

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
                     :content 
                     (describe-spec (json-read-str (:refine (:params request))))
                    }]

                   ) ;; (if (:refine
                 ) ;; concat
                ) ;; banner
        ] ;; :h2

      content])
     request
     {:css "/css/editor.css"
      :jss ["/js/editor.js" "/js/gen.js"]
      :onload (onload)})))

(declare language-dropdown)
(declare show-game-table)

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

(defn show-games [ & [ {language :language
                        user-id :user-id
                        }]]
  (log/debug (str "GAME-CHOOSING LANGUAGE: " language))
  (let [show-source-lists false
        language (if language language "")]
    (html
     (language-dropdown language)
     
     [:div {:style "margin-top:0.5em;"}
      [:h3 "My games"]
      (let [sql "SELECT game.name AS name,game.id AS id,active,
                        source,target,target_lex,target_grammar
                   FROM game
                  WHERE ((1=1) OR (game.target = ?) OR (? = ''))
                    AND (game.created_by = ?)
               ORDER BY game.name"
            debug (log/debug (str "MY GAMES: GAME-CHOOSING SQL: " (string/replace sql "\n" " ")))
            results (k/exec-raw [sql
                                 [language language user-id]]
                                :results)
            debug (log/debug (str "Number of results: " (.size results)))]
        (show-game-table results {:show-as-owner? true}))
      ]

     [:div {:style "margin-top:0.5em;"}
      [:h3 "Other teachers' games"]

      (let [sql "SELECT game.name AS name,game.id AS id,active,
                        source,target,target_lex,target_grammar
                   FROM game
                  WHERE ((game.target = ?) OR (? = ''))
                    AND ((game.created_by IS NULL) OR
                         (game.created_by != ?))
               ORDER BY game.name"

            debug (log/debug (str "OTHER'S GAMES: GAME-CHOOSING SQL: " (string/replace sql "\n" " ")))
            results (k/exec-raw [sql
                                 [language language user-id]]
                                :results)
            debug (log/debug (str "Number of results: " (.size results)))]
        (show-game-table results {:show-as-owner? false}))
      ]
     

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

     )
    )
  )

(defn show-game-table [results & [{show-as-owner? :show-as-owner?}]]
  (log/debug (str "showing as owner?: " show-as-owner?))
  (html
   (if (empty? results)
     [:div [:i "None."]]
     [:table {:class "striped padded"}
      [:tr
       (if (= true show-as-owner?)
         [:th {:style "width:2em"} "Active?"])

       [:th {:style "width:15em"} "Name"]
       [:th {:style "width:auto"} "Verbs"]
       [:th {:style "width:auto"} "Tenses"]
       ]

      (map
       (fn [result]
         (let [debug (log/trace (str "result info: " result))
               game-name-display
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

            (if show-as-owner?
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
                                 {:checked "on"}))]]])
            [:td
             ;; show as link
             [:a {:href (str "/editor/game/" game-id)} game-name-display]]
            [:td (string/join ", " (map #(html [:i %])
                                        (map #(get-in % [:root language-keyword language-keyword])
                                             (map json-read-str (.getArray (:target_lex result))))))]
            [:td (string/join ", " (map #(html [:b (str %)])
                                        (remove nil?
                                                (map #(get tenses-human-readable %)
                                                     (map json-read-str (.getArray (:target_grammar result)))))))]
            ]
           ))
       results)])))

(defn language-dropdown [language]
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
             {:value "it" :label "Italiano"}])]]]]))

(defn show-game [game-id & [ {refine :refine
                              show-as-owner? :show-as-owner?} ]]
  (let [game-id (Integer. game-id)
        game (first (k/exec-raw ["SELECT * 
                                    FROM game 
                                   WHERE id=?"
                                 [game-id]] :results))
        owner (:email
               (first (k/exec-raw ["SELECT email 
                                      FROM vc_user
                                INNER JOIN game 
                                        ON (vc_user.id = game.created_by)
                                       AND (game.id = ?)"
                                   [game-id]] :results)))
        refine (if refine (json-read-str refine) nil)]
    (html

       [:div {:style "float:left;width:100%;margin-top:1em"}

        [:div {:style "border:0px dashed green;float:right;width:50%;"}
         (if show-as-owner?
           (game-editor-form game nil nil)
           (html
            [:h2 "Game info"]

            [:table {:class "striped"}
             [:th "Owner"]
             [:td (if owner owner [:i "No owner"])]]
           
            [:div {:style "width:100%;float:left"}
             [:p
              "You are not owner of this game, so you cannot edit it. You can clone it though:"]

             [:form {:method "post"
                     :action (str "/editor/game/clone/" game-id)}
              [:button {:onclick "submit();"}
               "Clone"
               ]]]))]
            

        [:div {:style "border:0px dashed green;float:left;width:50%"}

         (if refine
           (html
            [:h3 (describe-spec refine)]
            [:div.spec
             (html/tablize refine)]

            (source-to-target-mappings game-id refine)

            ))

         [:h3 "Verbs"]

         (verb-tense-table game {:refine refine})

         ;; TOFINISH: working on making a form that lets us submit stuff like:
         ;; (populate 50 en/small it/small (unify (pick one <target_grammar>) (pick one <target lex>)))
         ;; (populate 50 en/small it/small {:root {:italiano {:italiano "esprimere"}}})

        ]])))

(def headers {"Content-Type" "text/html;charset=utf-8"})

(defn describe-spec [refine]
  (let [lexeme
        (cond
         (string? (get-in refine [:root :italiano :italiano]))
         (get-in refine [:root :italiano :italiano])

         (string? (get-in refine [:root :english :english]))
         (get-in refine [:root :english :english])

         (string? (get-in refine [:root :espanol :espanol]))
         (get-in refine [:root :espanol :espanol])

         true (type refine))
        
        aspect (get-in refine [:synsem :sem :aspect] :top)
        tense (get-in refine [:synsem :sem :tense] :top)

        tense-spec (unify (if (not (= aspect :top))
                            {:synsem {:sem {:aspect aspect}}}
                            :top)
                          (if (not (= tense :top))
                            {:synsem {:sem {:tense tense}}}
                            :top))
        human-readable-tense (get tenses-human-readable tense-spec)]
    (string/join ", " (remove #(nil? %) (list lexeme human-readable-tense)))))

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
                            :results)]
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
                [:td
                 (string/join "," (.getArray (:targets (second result))))]

                (if show-edit-buttons
                  [:td
                   [:button {:disabled "disabled"} "Delete"]
                   ])
                ]
               )
             (sort
              (zipmap
               (series 1 (.size results) 1)
               results)))]))))

(defn expressions-for-game [game-id]
  (let [game (first (k/exec-raw ["SELECT * FROM game WHERE id=?" [(Integer. game-id)]] :results))
        language-short-name (:target game)
        language (sqlname-from-match (short-language-name-to-long language-short-name))
        grouped-by-source-sql
        (str 
            "SELECT (array_agg(target.structure->'root'->'" language "'->'" language "'))[1] AS infinitive,
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
                AND game.target_lex != ARRAY['{}'::jsonb]
                AND game.target_grammar != ARRAY['{}'::jsonb]
           GROUP BY source.surface
           ORDER BY infinitive LIMIT 10")

        grouped-by-source-results (k/exec-raw [grouped-by-source-sql
                                               [game-id] ]
                                              :results)]
    (if (empty? grouped-by-source-results)
      [:div {:style "float:left; border:0px dashed blue"}
       [:i "Choose some verbs and tenses."]]

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
                 [:th {:style "text-align:right"} (first result)]
                 [:th
                  (string/replace 
                   (let [infinitive (:infinitive (second result))]
                     (if infinitive infinitive ""))
                   "\"" "")]
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
)))

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
      (html [:i "Choose some verbs and tenses for this game."])

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
                                  (str "counts_per_verb_and_tense('" dom-id "'," (:id game) "," lexeme-index "," tense-index ");")
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

(defn language-to-spec-path [short-language-name]
  "Take a language name like 'it' and turn it into an array like: [:root :italiano :italiano]."
  (let [language-keyword-name (sqlname-from-match (short-language-name-to-long short-language-name))
        language-keyword (keyword language-keyword-name)]
    [:root language-keyword language-keyword]))

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
                   "SET (name,source,target,target_lex,target_grammar) "
                   "= (?,?,?," target-lex-as-specs "," target-tenses-as-specs ") WHERE id=?")]

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
                          (Integer. game-id)]]))))))

(defn multipart-to-edn [params]
  (log/trace (str "multipart-to-edn input: " params))
  (let [output
        (zipmap (map #(keyword %)
                     (map #(string/replace % ":" "")
                          (map #(string/replace % "[]" "")
                               (keys params))))
                (vals params))]
    (log/trace (str "multipart-to-edn output: " output))
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
  (log/debug (str "source-set with commas: " 
                  (str "ARRAY[" 
                       (string/join "," (map #(str "" (string/join "," %) "") source-set)) "]")))
  (log/debug (str "target-set with commas: " 
                  (str "ARRAY[" 
                       (string/join "," (map #(str "" (string/join "," %) "") target-set)) "]")))

  (let [source-grouping-str 
        (str "ARRAY[" 
             (string/join "," (map #(str "" (string/join "," %) "") source-set)) "]::integer[]")
        target-grouping-str (str "ARRAY[" (string/join "," (map #(str "" (string/join "," %) "") target-set)) "]::integer[]")
        sql (str "INSERT INTO game (name,source_groupings,target_groupings,source,target)
                       SELECT ?, " source-grouping-str "," target-grouping-str ",?,? RETURNING id")]
    (log/debug (str "inserting new game with sql: " sql))
    ;; return the row ID of the game that has been inserted.
    (first (k/exec-raw [sql [name source target]] :results))))

;; TODO: has fallen victim to parameteritis (too many unnamed parameters)
(defn game-editor-form [game game-to-edit game-to-delete & [editpopup] ]
  (let [game-id (:id game)
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

     (f/render-form
      {:action (str "/editor/game/edit/" game-id)
       :enctype "multipart/form-data"
       :method :post
       :fields (concat

                [{:name :name :size 50 :label "Name"}]

                [{:name :target_lex
                  :label "Verbs"
                  :disabled "disabled"
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

;; TODO: replace with standard (range).
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
    :value (json/write-str {:synsem {:sem {:tense :futuro}}})}

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
   {:synsem {:sem {:tense :futuro}}} "future"
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
