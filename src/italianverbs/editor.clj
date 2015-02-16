(ns italianverbs.editor
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [formative.core :as f]
   [formative.parse :as fp]
   [italianverbs.auth :refer [is-admin is-authenticated]]
   [italianverbs.english_rt :as en]
   [italianverbs.html :as html]
   [italianverbs.italiano_rt :as it]
   [italianverbs.morphology :refer (fo)]
   [italianverbs.korma :as db]
   [italianverbs.unify :refer [get-in]]
   [italianverbs.verb :refer [generation-table predicates-from-lexicon]]
   [hiccup.core :refer (html)]
   [korma.core :as k]
))

(def route-graph
  {:home {:create {:get "Create new game"}}
   :create {:home {:get "Cancel"}
            :create {:post {:button "New Game"}}}
   :read {:home {:get "Show all games"}}})

(declare body)
(declare control-panel)
(declare create)
(declare create-form)
(declare delete)
(declare delete-form)
(declare home-page)
(declare links)
(declare onload)
(declare read-request)
(declare show-inflections-per-game)
(declare show-words-per-game)
(declare update)
(declare update-form)
(declare set-as-default)
(declare tenses-per-game)
(declare verbs-per-game)
(declare short-language-name-to-long)

(def headers {"Content-Type" "text/html;charset=utf-8"})

(def routes
  (compojure/routes
   (GET "/" request
        (is-admin {:body (body "Editor: Top-level" (home-page request) request)
                   :status 200
                   :headers headers}))

   ;; alias for '/editor' (above)
   (GET "/home" request
        {:status 302
         :headers {"Location" "/editor"}})
  
   (GET "/create" request
        (is-admin (create-form request)))

   (POST "/create" request
         (is-admin (create request)))

   (GET "/read" request
        (is-admin
         {:body (read-request request)
          :headers headers}))

   (POST "/update/:game" request
        (is-admin
         (update request)))

   (GET "/delete/:game" request
        (is-admin
         {:headers headers
          :body (delete-form request)}))

   (POST "/delete/:game" request
        (is-admin (delete request)))

   ;; alias for '/read' (above)
   (GET "/:game" request
        (is-admin {:body (read-request request)
                   :headers headers
                   :status 200}))

   ;; which game(s) will be active (more than one are possible).
   (POST "/use" request
         (is-admin (set-as-default request)))))

(def all-inflections
  (map #(string/replace-first (str %) ":" "")
       [:conditional :present :future :imperfect :passato]))

(defn predicates-from-db []
  (map #(string/replace (str (:predicate %)) "\"" "")
       (k/exec-raw [(str "SELECT DISTINCT structure->'synsem'->'sem'->'pred'::text
                                       AS predicate
                                     FROM expression
                                 ORDER BY predicate")] :results)))

(def game-form
  {:fields [{:name :name :size 50 :label "Name"}
            {:name :source :type :select 
             :label "Source Language"
             :options [{:value "en" :label "English"}
                       {:value "it" :label "Italian"}
                       {:value "es" :label "Spanish"}]}
            {:name :target :type :select 
             :label "Target Language"
             :options [{:value "en" :label "English"}
                       {:value "it" :label "Italian"}
                       {:value "es" :label "Spanish"}]}
            {:name :game :type :hidden}
            {:name :words
             :label "Predicates for this group"
             :cols 3
             :type :checkboxes
             :datatype :strs
             :options (predicates-from-db)}
            {:name :inflections
             :label "Tenses for this group"
             :type :checkboxes
             :datatype :strs
             :options all-inflections}
            ]
   :validations [[:required [:name]]
                 [:min-length 1 :verbs "Select one or more verbs"]]})

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

(defn body [title content request]
  (html/page 
   title
   (html
    [:div {:class "major"}
     [:h2 "Verb List Editor"]
     content])
   request
   {:css "/css/editor.css"
    :jss ["/js/editor.js" "/js/gen.js"]
    :onload (onload)}))

;;                                        ON source.structure->'synsem'->'sem' @> target.structure->'synsem'->'sem'       

(defn examples-per-game [game-id]
  (let [results (k/exec-raw [(str "SELECT source.surface AS source, 
                                          target.surface AS target,
                                          source.structure->'synsem'->'sem' AS source_sem,
                                          target.structure->'synsem'->'sem' AS target_sem
                                     FROM expression AS source
                               INNER JOIN expression AS target
                                       ON ((source.structure->'synsem'->'sem')::jsonb = (target.structure->'synsem'->'sem')::jsonb)
                                    WHERE source.language='en' AND target.language='it'
                                      AND source.structure->'synsem'->'sem'->'pred' 
                                       IN (SELECT ('\"'||word||'\"')::jsonb 
                                             FROM words_per_game WHERE game=?)
                                 ORDER BY source.id LIMIT 10") [(Integer. game-id)]]
                            :results)]
    results))

(defn example-table [request]
  (html
   [:div {:style "width:100%"}
    [:h4 "Examples"]
    (let [examples (examples-per-game (:game (:params request)))]
      [:div#exampletable
       [:table.striped
        [:tr
         [:th "Source"]
         [:th "Target"]
         [:th "Source sem"]
         [:th "Target sem"]
         ]

        (map (fn [row]
               (let [debug (log/debug (str "source expression:"
                                           (:source row)))
                     source (:source row)
                     target (:target row)
                     source_sem (let [sem (str (:source_sem row))]
                                  (json/read-str sem
                                                 :key-fn keyword
                                                 :value-fn (fn [k v]
                                                             (cond (string? v)
                                                                   (keyword v)
                                                                   :else v))))
                     target_sem (let [sem (str (:target_sem row))]
                                  (json/read-str sem
                                                 :key-fn keyword
                                                 :value-fn (fn [k v]
                                                             (cond (string? v)
                                                                   (keyword v)
                                                                   :else v))))]
                 [:tr 
                  [:td source]
                  [:td target]
                  [:td [:div {:onclick "toggle_expand(this);" :style "height:100px; width:200px ;overflow:scroll"} (html/tablize source_sem)]]
                  [:td [:div {:onclick "toggle_expand(this);" :style "height:100px; width:200px ;overflow:scroll"} (html/tablize target_sem)]]]))
             examples)]])]))

(defn read-request [request]
  "show a single game, along with UI to edit or delete the game."
  (let [game-id (:game (:params request))
        game-row (first (k/exec-raw [(str "SELECT * FROM games WHERE id=?") [(Integer. game-id)] ] 
                                    :results))]
    (body (str "Showing game: " (:name game-row))
          (html
           [:h3 (:name game-row)]

           [:div.gameinfo
            [:div
             [:h4 "Predicates"]
             (let [verbs (show-words-per-game request)]
               (if (empty? verbs)
                 [:p "No predicates chosen."]
                [:ul
                 (map (fn [word]
                        [:li (str word)])
                      (map #(:word %)
                           verbs))]))]
            [:div
             [:h4 "Tenses"]
             (let [inflections (show-inflections-per-game request)]
               (if (empty? inflections)
                 [:p "No tenses chosen."]
                 [:ul
                  (map (fn [inflection]
                         [:li (str inflection)])
                       (map #(:inflection %)
                            inflections))]))]

            (example-table request)

            ] ;; div.gameinfo
                 

           [:h4 {:style "float:left;width:100%;"} "Edit"]

           ;; allows application to send messages to user after redirection via URL param: "&message=<some message>"
           [:div.user-alert (:message (:params request))]

           (update-form request game-row)
           
           [:div.delete
            [:button {:onclick (str "javascript:window.location.href = '/editor/delete/' + '" game-id "';")}
             "Delete" ]]
           (links request :read))
          request)))

(defn update-verb-for-game [game-id words]
  (if (not (empty? words))
    (let [word (first words)]
      (log/info (str "INSERT INTO words_per_game (game,word) " game-id "," word))
      (k/exec-raw [(str "INSERT INTO words_per_game (game,word) VALUES (?,?)") [(Integer. game-id)
                                                                                     word]])
      (update-verb-for-game game-id (rest words)))))

(defn update-verbs-for-game [game-id words]
  (let [words (remove #(= % "")
                      (get words "words[]"))]
    (log/info (str "updating verbs for this group: " game-id " with: " (string/join "," words)))
    (k/exec-raw [(str "DELETE FROM words_per_game WHERE game=?") [(Integer. game-id)]])
    (update-verb-for-game game-id words)))

(defn update-inflection-for-game [game-id inflections]
  (if (not (empty? inflections))
    (let [inflection (first inflections)]
      (log/info (str "INSERT INTO inflections_per_game (game,inflection) " game-id "," inflection))
      (k/exec-raw [(str "INSERT INTO inflections_per_game (game,inflection) VALUES (?,?)") [(Integer. game-id)
                                                                                     inflection]])
      (update-inflection-for-game game-id (rest inflections)))))

(defn update-inflections-for-game [game-id inflections]
  (let [inflections (remove #(= % "")
                      (get inflections "inflections[]"))]
    (log/info (str "updating verbs for this group: " game-id " with: " (string/join "," inflections)))
    (k/exec-raw [(str "DELETE FROM inflections_per_game WHERE game=?") [(Integer. game-id)]])
    (update-inflection-for-game game-id inflections)))

(defn update [request]
  (let [game-id (:game (:params request))
        game-row (first (k/exec-raw [(str "SELECT * FROM games WHERE id=?") [(Integer. game-id)] ] :results))]
    (log/debug (str "editor/update with request: " (:form-params request)))
    (fp/with-fallback #(update-form request game-row :problems %)
      (let [debug (log/debug (str "editor/update..about to fp/parse-params with params: " (:form-params request)))
            values (fp/parse-params game-form (:form-params request))
            debug (log/debug (str "values parsed from form params: " values))
            results (k/exec-raw ["UPDATE games SET (name, source,target) = (?,?,?) WHERE id=? RETURNING id" [(:name values)
                                                                                                             (:source values)
                                                                                                             (:target values)
                                                                                                             (Integer. (:game values))]] 
                                :results)
            edited-game-id (:id (first results))]
        (update-verbs-for-game edited-game-id (:form-params request))
        (update-inflections-for-game edited-game-id (:form-params request))
        {:status 302
         :headers {"Location" (str "/editor/" edited-game-id "?message=updated.")}}))))

(defn set-each-as-default [params]
  (if (not (empty? params))
    (let [param (first params)]
      (log/debug (str "use param: " param))
      (let [k (first param)
            v (second param)]
      (if (= v "on")
        (k/exec-raw [(str "INSERT INTO games_to_use (game) VALUES (?)") [(Integer. k)]]))
      (set-each-as-default (rest params))))))

(defn set-as-default [request]
  (let [games-to-use (:params request)]
    (k/exec-raw [(str "DELETE FROM games_to_use")])
    (set-each-as-default games-to-use)
    {:status 302
     :headers {"Location" (str "/editor/?message=updated.")}}))

(defn delete [request]
  (let [game-id (:game (:params request))
        game-row (first (k/exec-raw [(str "SELECT * FROM games WHERE id=?") [(Integer. game-id)]] :results))]
    (k/exec-raw [(str "DELETE FROM words_per_game WHERE game=?") [(Integer. game-id)]])
    (k/exec-raw [(str "DELETE FROM inflections_per_game WHERE game=?") [(Integer. game-id)]])
    (k/exec-raw [(str "DELETE FROM games_to_use WHERE game=?") [(Integer. game-id)]])
    (k/exec-raw [(str "DELETE FROM games WHERE id=?") [(Integer. game-id)]])
    {:status 302
     :headers {"Location" (str "/editor/" "?message=deleted+game:" (:name game-row))}}))

(defn delete-form [request]
 (let [game-id (:game (:params request))
       game-row (first (k/exec-raw [(str "SELECT * FROM games WHERE id=?") [(Integer. game-id)]] :results))]
    (body (str "Deleting game: " (:name game-row))
          (html
           [:h3 (str "Confirm - deleting '" (:name game-row) "'")]

           [:p "Are you sure you want to delete this group?"]

           [:form {:method "post"
                   :action (str "/editor/delete/" game-id)}
            [:button {:onclick (str "javascript:submit();")}
             "Confirm" ]]

           ;; allows application to send messages to user after redirection via URL param: "&message=<some message>"
           [:div.user-alert (:message (:params request))]

           (links request :read))
          request)))

(def games-table "games")
(def words-per-game-table "words_per_game")
(def inflections-per-game-table "inflections_per_game")

(defn show [request]
  (k/exec-raw [(str "SELECT * FROM " games-table " ORDER BY name")] :results))

(defn show-inflections-per-game [request]
  (let [table-name inflections-per-game-table
        game-id (:game (:params request))]
    (k/exec-raw [(str "SELECT * FROM " table-name " WHERE game = ? ORDER BY inflection") [(Integer. game-id)]] :results)))

(defn show-words-per-game [request]
  (let [table-name words-per-game-table
        game-id (:game (:params request))]
    (k/exec-raw [(str "SELECT * FROM " table-name " WHERE game = ? ORDER BY word") [(Integer. game-id)]] :results)))

(defn home-page [request]
  (let [links (links request :home)
        games (show request)
        games-to-use (set (mapcat vals (k/exec-raw ["SELECT * FROM games_to_use"] :results)))]
    (html
     [:div.user-alert (:message (:params request))]
     [:div
      (cond
       (empty? games)
       [:div "No groups."]

       true
       ;; TODO: derive <td> links from route-graph, like (links) does.
       [:form {:method "post"
               :action "/editor/use"}
        [:table.striped
         [:tr 
          [:th  {:style "padding-right:1em"} "Use?"]
          [:th "Verb List"]]
          
         (map (fn [each]
                [:tr 
                 [:th [:input 
                       (merge
                        {:name (str (:id each)) :type "checkbox"}
                        (if (some #(= % (:id each)) games-to-use)
                          {:checked "checked"}))
                       ]]
                 [:td [:a {:href (str "/editor/" (:id each))} (:name each)]]])
              games)]

        links
        
        [:div {:style "width:100%;float:left;margin-top:0.5em"}
         [:input {:type "submit"
                  :value "Select verb lists to use"}]]





       [:div  {:style "width:100%"}
        [:h3  "Examples" ]
        (if (empty? games-to-use)
          [:div.advice "No games selected to generate examples." ]

          (map (fn [each]
                 (if (some #(= % (:id each)) games-to-use)
                   (html
                    
                    [:div {:class "game_container"}
                     [:h4 {:style "width:30%;float:left;padding-right:1em"} [:span {:style ""} [:a {:href (str "/editor/" (:id each))}(:name each) ] ":"] [:span {:style "padding-left:1em"} [:i (string/join "," (verbs-per-game (:id each)))]]]
                     [:div {:style "width:30%; float:left"}
                      [:h4 [:span {:style "padding-left:1em"} 
                            [:i (short-language-name-to-long (:source each))] 
                       " ⇒ "
                            [:i (short-language-name-to-long (:target each))]]]]

                     [:div {:style "width:30%; float:right"}
                      [:h4 "Tenses:"  [:span {:style "padding-left:1em"} [:i (string/join "," (tenses-per-game (:id each)))]]]]
                     (generation-table (verbs-per-game (:id each))
                                       :id_prefix (:id each)
                                       :source (:source each)
                                       :target (:target each)
                                       :tenses (tenses-per-game (:id each))
                                       )]

                    )))

               games))
        ]])

      links])))

(defn tenses-per-game [game-id]
  (log/info (str "verbs-per-game: " game-id))
  (map #(:inflection %)
       (k/exec-raw [(str "SELECT inflection
                                  FROM " inflections-per-game-table 
                               " WHERE game = ? ORDER BY inflection") [(Integer. game-id)]] :results)))

(defn verbs-per-game [game-id]
  (log/info (str "verbs-per-game: " game-id))
  (let [verbs
        (map #(:word %)
             (k/exec-raw [(str "SELECT word
                                  FROM " words-per-game-table 
                               " WHERE game = ? ORDER BY word") [(Integer. game-id)]] :results))]
    (log/debug (str "check these boxes: " (string/join "," verbs)))
    verbs))

(defn inflections-per-game [game-id]
  (let [inflections
        (map #(:inflection %)
             (k/exec-raw [(str "SELECT inflection
                                  FROM " inflections-per-game-table 
                               " WHERE game = ? ORDER BY inflection") [(Integer. game-id)]] :results))]
    (log/debug (str "check these boxes: " (string/join "," inflections)))
    inflections))

(def game-default-values {})

(defn create [request]
  (log/info (str "editor/new with request: " (:form-params request)))
  (fp/with-fallback #(create-form request :problems %)
    (let [values (fp/parse-params game-form (:form-params request))
          debug (log/debug (str "editor/new: values: " values))
          results 
          (try
            (k/exec-raw [(str "INSERT INTO games (id,name) VALUES (DEFAULT,?) RETURNING id") [(:name values)]] :results)
            (catch Exception e
              (let [message (.getMessage e)]
                (log/error (str "Failed inserting into games:{:name=>" (:name values) "}"))
                ;; TODO: maybe there's something we can do to remedy the problem..?
                (throw e))))
          new-game-id (:id (first results))]
      (update-verbs-for-game new-game-id (:form-params request))
      {:status 302
       :headers {"Location" (str "/editor/" new-game-id "?message=created.")}})))

(defn links [request current]
  ;; TODO: _current_ param can be derived from request.
  (let [method :get]
    (html
     [:div {:class "links"}

      ;; TODO: vary these links depending on context (which can be obtained by loooking in request).
      [:span [:a {:href (str "/editor/" (string/replace-first (str :create) ":" ""))} "Create new list"]]
      [:span [:a {:href (str "/editor/" (string/replace-first (str :home) ":" ""))} "Show all lists"]]

      ])))

(defn create-form [request & [:problems problems]]
  {:status 200
   :body (body "Editor: Create a new game"
               (let [links (links request :create)]
                 (html
                  [:div
                   (f/render-form (assoc game-form
                                    :values (merge game-default-values (:form-params request))
                                    :action "/editor/create"
                                    :method "post"
                                    :problems problems))
                   ]))
               request)
   :headers headers})

;; TODO: update-form should be a complete request structure (with :status, :body, and :headers), just like
;; (create-form) above.
(defn update-form [request game & [:problems problems]]
  (log/debug (str "update-form with game: " game))
  (let [game-id (:id game)]
    (html
     [:div
      (f/render-form (assoc game-form
                       :values (merge game-default-values
                                      (:form-params request)
                                      {:name (:name game)
                                       :source (:source game)
                                       :target (:target game)
                                       :game game-id
                                       :words (verbs-per-game game-id)
                                       :inflections (inflections-per-game game-id)})
                       :action (str "/editor/update/" (:id game))
                       :method "post"
                       :problems problems))])))

(defn short-language-name-to-long [lang]
  (cond (= lang "it") "Italian"
        (= lang "en") "English"
        (= lang "es") "Spanish"
        true "???"))

  
