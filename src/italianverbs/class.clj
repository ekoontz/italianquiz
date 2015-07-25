;; Note: this file is about 'class' as in 'a class of students in a classroom', 
;; not 'class' in the programming language sense.
;; TODO: find a better name for "a set of students and a teacher associated for a given period of time" than "class".
;; maybe 'course-term' or something like that.
(ns italianverbs.class
  ;; probably bad idea to exclude this (class), at least from the
  ;; point of view of readability by other developers, since they are
  ;; basic.
  (:refer-clojure :exclude [class])
  (:require
   [clj-time.core :as t]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [formative.core :as f]
   [hiccup.core :refer (html)]
   [italianverbs.authentication :as authentication]
   [italianverbs.config :refer [language-radio-buttons short-language-name-to-long time-format]]
   [italianverbs.html :as html :refer [banner page rows2table]]
   [italianverbs.korma :as db]
   [italianverbs.user :refer [do-if-authenticated do-if-teacher username2userid]]
   [korma.core :as k]))

(declare headers)
(declare table)
(declare tr)
(declare show-students)
(declare show-classes)

(def html-headers {"Content-Type" "text/html;charset=utf-8"})
(def json-headers {"Content-type" "application/json;charset=utf-8"})
(def resources {:onload "class_onload();"
                :css ["/css/class.css"]
                :jss ["/js/class/js"]})
(def routes
  (compojure/routes
   (GET "/" request
        (do-if-authenticated
         {:headers html-headers
          :body
          (page "My Classes"
                (let [userid (username2userid (authentication/current request))]
                  [:div#classes {:class "major"}
                   (do-if-teacher
                    [:div {:class "classlist"}
                     [:h2 (banner [{:content "My Classes"}])]

                     [:h3 "Classes I'm teaching"]

                     (let [results (k/exec-raw
                                    ["SELECT id,name,language,
                                             to_char(class.created,?) AS created
                                        FROM class
                                       WHERE teacher=?"
                                     [time-format userid]] :results)]
                       (show-classes results :cols [:name :language :created]))

                     [:div.new
                       [:h3 "Create a new class:"]
                      
                      (f/render-form
                       {:action "/class/new"
                        :enctype "multipart/form-data"
                        :method "post"
                        :fields [{:name :name :size 50 :label "Name"}
                                 (language-radio-buttons)]})
                      ]]

                    )

                   [:div {:class "classlist"}
                    [:h3 "Classes I'm enrolled in"]

                    (let [results (k/exec-raw
                                   ["SELECT *
                                       FROM student_in_class"
                                     []] :results)]
                      (rows2table results
                                  {}))]

                     [:div.new
                      [:h3 "Join a new class:"]
                      [:form {:action "/class/join"
                              :method "post"}
                       ]
                      (let [results (k/exec-raw
                                     ["SELECT *
                                         FROM class"
                                      []] :results)]
                        (show-classes results))]
                   ])
                request
                resources)}))

   (POST "/new" request
         (do-if-teacher
          (let [userid (username2userid (authentication/current request))
                params (:params request)]
            (log/info (str "creating new class with params: " (:params request)))
            (let [new-game-id
                  (:id (first
                        (k/exec-raw ["
INSERT INTO class (name,teacher,language)
     VALUES (?,?,?) RETURNING id"
                         [(:name params)
                          userid
                          (:lang params)  ]] :results )))]
              {:status 302 :headers {"Location"
                                     (str "/class/" new-game-id 
                                          "?message=Created game")}}))))
   (GET "/:class" request
        (do-if-authenticated
         {:headers html-headers
          :body
          (let [class (Integer. (:class (:route-params request)))]
            (page "Class"
                  (let [userid (username2userid (authentication/current request))
                        class-map (first
                                   (k/exec-raw
                                    ["SELECT class.id,
                                             class.name,
                                             class.language,
                                             to_char(class.created,?) AS created,
                                             trim(teacher.given_name || ' ' || teacher.family_name) AS teacher,
                                             teacher.email AS teacher_email
                                        FROM class
                                  INNER JOIN vc_user AS teacher
                                          ON teacher.id = class.teacher
                                       WHERE class.id=?"
                                     [time-format class]] :results))]
                   [:div#students {:class "major"}
                    [:h2 (banner [{:href "/class"
                                   :content "My Classes"}
                                  {:href nil
                                   :content (:name class-map)}])]
                    [:div
                     [:table
                      [:tr
                       [:th "Language"]
                       [:td (short-language-name-to-long (:language class-map))]
                       [:th "Created"]
                       [:td (:created class-map)]
                       [:th "Teacher"]
                       [:td (:teacher class-map)]
                       [:th "Email"]
                       [:td (:teacher_email class-map)]
                       ]
                      ]

                     [:h3 "Students"]
                     ;; TODO: change LEFT JOIN to INNER JOIN after development is done.
                     (let [students (k/exec-raw
                                     ["SELECT trim(given_name || ' ' || family_name) AS name,
                                              picture,teacher,email,
                                              to_char(student_in_class.enrolled,?) AS enrolled
                                         FROM vc_user
                                    LEFT JOIN student_in_class
                                           ON (student_in_class.student = vc_user.id)"
                                      [time-format]] :results)]
                       [:div.rows2table
                        (rows2table students
                                    {:cols [:name :picture :email :enrolled]
                                     :col-fns
                                     {:picture (fn [picture] (html [:img {:width "50px" :src (:picture picture)}]))
                                      :name (fn [student]
                                              (html [:a {:href (str "/student/" (:id student))}
                                                     (:name student)]))}})])
                     [:div.add 
                      [:a {:href (str "/class/" class "/student/add")}
                       "Add a new student"]]

                     [:h3 "Games"]
                     (let [games (k/exec-raw
                                  ["SELECT 'remove',game.id,game.name AS game,
                                           trim(owner.given_name || ' ' || owner.family_name) AS created_by,
                                           owner.id AS owner_id,
                                           to_char(game_in_class.added,?) AS added
                                      FROM game
                                INNER JOIN game_in_class
                                        ON (game_in_class.game=game.id
                                       AND  game_in_class.class=?)
                                 LEFT JOIN vc_user AS owner 
                                        ON (owner.id = game.created_by)
                                  ORDER BY game_in_class.added DESC"
                                   [time-format class]] :results)]
                       [:div.rows2table
                        (rows2table games
                                    {:cols [:remove :game :created_by :added]
                                     :td-styles
                                     {:remove "text-align:center"}
                                     :th-styles
                                     {:remove "text-align:center;width:3em"}
                                     :col-fns
                                     ;; TODO: add some javascript confirmation "are you sure?" stuff rather
                                     ;; than simply removing the game from the class.
                                     {:remove (fn [game]
                                                (html [:form {:action (str "/class/" class
                                                                           "/game/" (:id game) "/delete")
                                                              :method "post"}
                                                       [:button {:onclick "submit()"} "Remove"]]))
                                      :created_by (fn [game] (html [:a {:href (str "/teacher/" (:owner_id game))}
                                                                    (:created_by game)]))
                                      :game (fn [game] (html [:a {:href (str "/game/" (:id game))}
                                                              (if (or (nil? (:game game))
                                                                      (not (= "" (string/trim (:game game)))))
                                                                (:game game) "(untitled)")]))}}
                                     )])
                     [:div.add 
                      [:a {:href (str "/class/" class "/game/add")}
                       "Add a new game"]]

                     ]])
                request
                resources))}))

   (GET "/:class/game/add"
        request
        ;; TODO: check if this user is the teacher of the game: add (is-teacher-of? <game> <user>)
        (do-if-authenticated
         {:headers html-headers
          :body
          (let [class (Integer. (:class (:route-params request)))]
            (page "Add a game"
                  (let [userid (username2userid (authentication/current request))
                        class-map (first
                                   (k/exec-raw
                                    ["SELECT class.id,
                                             class.name,
                                             class.language,
                                             to_char(class.created,?) AS created,
                                             trim(teacher.given_name || ' ' || teacher.family_name) AS teacher,
                                             teacher.email AS teacher_email
                                        FROM class
                                  INNER JOIN vc_user AS teacher
                                          ON teacher.id = class.teacher
                                       WHERE class.id=?"
                                     [time-format class]] :results))]
                   [:div#students {:class "major"}
                    [:h2 (banner [{:href "/class"
                                   :content "My Classes"}
                                  {:href (str "/class/" (:id class-map))
                                   :content (:name class-map)}
                                  {:href nil
                                   :content "Add a game"}])]
                    [:div
                     [:table
                      [:tr
                       [:th "Language"]
                       [:td (short-language-name-to-long (:language class-map))]
                       [:th "Created"]
                       [:td (:created class-map)]
                       [:th "Teacher"]
                       [:td (:teacher class-map)]
                       [:th "Email"]
                       [:td (:teacher_email class-map)]
                       ]
                      ]

                     [:h3 "Choose a game to add"]

                     ;; TODO: show all games that are *not* in this game.
                     (let [games (k/exec-raw
                                  ["SELECT 'Add',game.id,game.name AS game,
                                           to_char(game.created_on,?) AS created,
                                           trim(game_creator.given_name || ' ' || game_creator.family_name) AS creator,
                                           game_creator.email AS creator_email
                                      FROM game
                                 LEFT JOIN vc_user AS game_creator
                                        ON (game.created_by = game_creator.id)
                                     WHERE game.target = ?
                                       AND game.id
                                    NOT IN (SELECT game
                                              FROM game_in_class
                                             WHERE class = ?)"
                                   [time-format (:language class-map) class]] :results)]
                       [:div.rows2table
                        (rows2table games
                                    {:cols [:add :game :created :creator :creator_email]
                                     :td-styles
                                     {:add "text-align:center"}
                                     :th-styles
                                     {:add "text-align:center;width:3em"}
                                     :col-fns
                                     {:add (fn [game] (html
                                                       [:form {:action (str "/class/" class
                                                                            "/game/" (:id game) "/add")
                                                               :method "post"}
                                                        [:button {:onclick "submit()"} "Add"]]))
                                      :game (fn [game] (html [:a {:href (str "/game/" (:id game))}
                                                              (if (or (nil? (:game game))
                                                                      (not (= "" (string/trim (:game game)))))
                                                                (:game game) "(untitled)")]))
                                      :created (fn [game] (html [:a {:href (str "/game/" (:id game))}
                                                                 (:created game)]))
                                      }}
                                     )])

                     ]])

                request
                resources

                ))}

         )
        )

      (POST "/:class/game/:game/add" request
            ;; TODO: check if this user is the teacher of the game: add (is-teacher-of? <game> <user>)
            (do-if-teacher
             (let [debug (log/debug (str "/:class/game/:game/add with route params:" (:route-params request)))
                   class (Integer. (:class (:route-params request)))
                   game (Integer. (:game (:route-params request)))]
               (k/exec-raw ["INSERT INTO game_in_class (game,class) 
                                  SELECT ?,?
                                   WHERE 
                              NOT EXISTS (SELECT game,class 
                                            FROM game_in_class 
                                           WHERE game = ? AND class=?)" [game class game class]])
               {:status 302
                :headers {"Location" (str "/class/" class "?message=Added game.")}})))

      (POST "/:class/game/:game/delete" request
            ;; TODO: check if this user is the teacher of the game: add (is-teacher-of? <game> <user>)
            (do-if-teacher
             (let [debug (log/debug (str "/:class/game/:game/add with route params:" (:route-params request)))
                   class (Integer. (:class (:route-params request)))
                   game (Integer. (:game (:route-params request)))]
               (k/exec-raw ["DELETE FROM game_in_class WHERE game=? AND class=?" [game class]])
               {:status 302
                :headers {"Location" (str "/class/" class "?message=Removed game.")}})))
      )
  )

(defn show-classes [results & {cols :cols}]
  (html
   [:div.rows2table
    (rows2table results
                {:cols cols
                 :col-fns {:name (fn [result]
                                   (html [:a {:href (str "/class/" (:id result))}
                                          (:name result)]))
                           :language (fn [result]
                                       (short-language-name-to-long
                                        (:language result)))
                           }}
                )]))
