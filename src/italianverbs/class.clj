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
   [italianverbs.about :as about]
   [italianverbs.authentication :as authentication]
   [italianverbs.config :refer [language-radio-buttons short-language-name-to-long time-format]]
   [italianverbs.game :refer [new-game-form tenses-of-game-as-human-readable verbs-of-game-as-human-readable]]
   [italianverbs.html :as html :refer [banner page rows2table]]
   [italianverbs.menubar :refer [menubar]]
   [babel.korma :as db]
   [italianverbs.user :refer [do-if do-if-authenticated do-if-teacher
                              do-if-not-teacher has-admin-role?
                              login-form menubar-info-for-user
                              username2userid]]
   [korma.core :as k]))

(declare add-existing-game-to-this-class)
(declare delete-class)
(declare headers)
(declare is-teacher-of-class?)
(declare table)
(declare tr)
(declare show-games-for-class)
(declare show-students)
(declare show-classes)

(def html-headers {"Content-Type" "text/html;charset=utf-8"})
(def json-headers {"Content-type" "application/json;charset=utf-8"})
(defn resources [request]
  {:onload "class_onload();"
   :css ["/css/class.css"]
   :jss ["/js/class.js"]
   :show-login-form (login-form request)
   :menubar (menubar (menubar-info-for-user request))})
   
(def routes
  (compojure/routes
   (GET "/" request
        (do-if-authenticated
         {:headers html-headers
          :body
          (page "Classes"
                (let [user-id (username2userid (authentication/current request))]
                  [:div#classes {:class "major"}

                   (do-if-teacher
                    [:div {:class "classlist"}
                     [:h2 (banner [{:content "My Classes"}])]

                     [:h3 "Classes I'm teaching"]

                     (let [results (k/exec-raw
                                    ["SELECT id AS class_id,class.name AS class,language,
                                             to_char(class.created,?) AS created
                                        FROM class
                                       WHERE teacher=?"
                                     [time-format user-id]] :results)]
                       (rows2table results {:cols [:class :language :created]
                                            :col-fns
                                            {:language (fn [class]
                                                         (short-language-name-to-long (:language class)))
                                             :class (fn [game-in-class]
                                                      [:a {:href (str "/class/" (:class_id game-in-class))}
                                                       (if (and (:class game-in-class)
                                                                (not (empty? (:class game-in-class))))
                                                         (:class game-in-class)
                                                         "(untitled class)")
                                                       ])}}))]
                    "") ;; if not a teacher, show emptystring.

                   (do-if-teacher
                     [:div.new
                      [:h3 "Create a new class:"]
                      (f/render-form
                       {:action "/class/new"
                        :enctype "multipart/form-data"
                        :method "post"
                        :id "create_new_class"
                        :onsubmit "return validate_new_class('create_new_class');"
                        :submit-label "Create"
                        :fields [(language-radio-buttons)
                                 {:name :name
                                  :size 50
                                  :label "Name"
                                  :placeholder "Type the name of the class (e.g. 'Italian 1')"}]})]
                    "" ;; if not a teacher show emptystring
                    )

                   (do-if-not-teacher
                    (let [current-classes
                          (k/exec-raw
                           ["SELECT class.id AS class_id,'leave',class.name AS class,
                                              trim(teacher.given_name || ' ' || teacher.family_name) AS teacher,
                                              teacher.email AS email,
                                              class.language
                                         FROM class
                                    LEFT JOIN vc_user AS teacher 
                                           ON (teacher.id = class.teacher)
                                        WHERE class.id 
                                           IN (SELECT class 
                                                 FROM student_in_class 
                                                WHERE student=?)"
                            [user-id]] :results)]
                    
                    [:div ;; <currently-enrolled-and-enrollable>
                     [:h2 (banner [{:content "My Classes"}])]
                     [:div {:class "classlist"}
                      [:h3 "Classes I'm enrolled in"]
                      (let [results (k/exec-raw
                                     ["SELECT class.id AS class_id,'leave',class.name AS class,
                                              trim(teacher.given_name || ' ' || teacher.family_name) AS teacher,
                                              teacher.email AS email,
                                              class.language
                                         FROM class
                                    LEFT JOIN vc_user AS teacher 
                                           ON (teacher.id = class.teacher)
                                        WHERE class.id 
                                           IN (SELECT class 
                                                 FROM student_in_class 
                                                WHERE student=?)"
                                      [user-id]] :results)]
                        (rows2table results
                                    {:col-fns {:language (fn [class]
                                                           (short-language-name-to-long (:language class)))
                                               :class (fn [class]
                                                        [:a {:href (str "/class/" (:class_id class))}
                                                         (:class class)])
                                               :teacher (fn [class]
                                                          (if (or (nil? (:teacher class))
                                                                  (= "" (:teacher class)))
                                                            "(unnamed teacher)"
                                                            (:teacher class)))
                                               :leave (fn [class]
                                                        [:form {:action (str "/class/disenroll/" (:class_id class))
                                                                :method "post"}
                                                         [:button {:onclick "submit()"} "Leave"]])}
                                     :th-styles {:leave "text-align:center;width:3em"}
                                     :cols [:leave :class :language :teacher :email]}))]
                     [:div ;; <enroll>
                      [:h3 "Enroll in a new class"]
                      [:form {:action "/class/join"
                              :method "post"}]
                      (let [enrollable-classes
                            (k/exec-raw
                             ;; Show only classes for which both of the following are true:
                             ;;
                             ;; 1. classes which the student is not already enrolled
                             ;; 2. classes for which teacher the student is enrolled in a class with.
                             ;;
                             ["SELECT class.id AS class_id,'enroll',class.name AS class,
                                              trim(teacher.given_name || ' ' || teacher.family_name) AS teacher,
                                              teacher.email AS email,
                                              class.language
                                         FROM class
                                   INNER JOIN vc_user AS teacher 
                                           ON (teacher.id = class.teacher)

                                        WHERE class.id NOT IN (
                                                SELECT class 
                                                  FROM student_in_class 
                                                 WHERE student=?)

                                          AND teacher.id IN (
                                                     SELECT teacher.id
                                                       FROM vc_user AS teacher 
                                                 INNER JOIN class 
                                                         ON (class.teacher = teacher.id)
                                                 INNER JOIN student_in_class 
                                                         ON student_in_class.class = class.id
                                                        AND student_in_class.student = ?)"
                              [user-id user-id]] :results)]

                        (rows2table enrollable-classes
                                    {:cols [:enroll :class :language :teacher :email]
                                     :if-empty-show-this-instead
                                     (if (empty? current-classes)
                                     [:div
                                      "First, please "
                                      [:a {:href "/about"} "find your teacher"] " to see classes that you can join."
                                      ]
                                     [:div
                                      "There are no more classes to join."])

                                     :th-styles {:enroll "text-align:center;width:3em"}
                                     :col-fns {:language (fn [class]
                                                           (short-language-name-to-long (:language class)))
                                               :class (fn [class]
                                                        [:a {:href (str "/class/" (:class_id class))}
                                                         (if (or (nil? (:class class))
                                                                 (= "" (:class class)))
                                                           "(unnamed class)"
                                                           (:class class))])
                                               :teacher (fn [class]
                                                         (if (or (nil? (:teacher class))
                                                                 (= "" (:teacher class)))
                                                           "(unnamed teacher)"
                                                           (:teacher class)))
                                               :enroll (fn [class]
                                                         [:form {:action (str "/class/enroll/" (:class_id class))
                                                                 :method "post"}
                                                          [:button {:onclick "submit()"} "Enroll"]])}}
                                    )
                        )
                      ] ;; </enroll>

                     
                     (if (not (empty? current-classes))
                       [:div
                        [:h3 "Not the right teacher?"]
                        [:div {:style "float:left"}
                         "Please leave your current classes in order to find your teacher."
                         ]
                        ])

                     ] ;; </currently-enrolled-and-enrollable>
                    )

                    "" ;; <- this empty string is the 'else' of the (do-if-not-teacher) above: i.e. show this (emptystring)
                    ;; if user *is* a teacher.

                    )


                   
                   ]
                  )
                request
                resources)}
         ;; else, not authenticated
         {:status 302 :headers {"Location"
                                "/?message=please login to view classes"}}))

   (POST "/new" request
         (do-if-teacher
          (let [user-id (username2userid (authentication/current request))
                params (:params request)]
            (log/info (str "creating new class with params: " (:params request)))
            (let [new-game-id
                  (:id (first
                        (k/exec-raw ["
INSERT INTO class (name,teacher,language)
     VALUES (?,?,?) RETURNING id"
                         [(:name params)
                          user-id
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
                  (let [user-id (username2userid (authentication/current request))
                        student-of-class?
                        (not (nil? (first
                                    (k/exec-raw
                                     ["SELECT 1 FROM student_in_class WHERE student=? and class=?"
                                      [user-id class]
                                      ] :results))))
                        class-map (first
                                   (k/exec-raw
                                    ["SELECT class.id,
                                             class.name,
                                             class.language,
                                             to_char(class.created,?) AS created,
                                             trim(teacher.given_name || ' ' || teacher.family_name) AS teacher,
                                             teacher.email AS teacher_email,
                                             teacher.id AS teacher_user_id
                                        FROM class
                                  INNER JOIN vc_user AS teacher
                                          ON teacher.id = class.teacher
                                       WHERE class.id=?"
                                     [time-format class]] :results))
                        teacher-of-class? (= user-id (:teacher_user_id class-map))]
                   [:div {:class "major"}
                    [:h2 (banner [{:href "/class"
                                   :content "Classes"}
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
                      ]]

                    (do-if (or teacher-of-class? (has-admin-role?))
                           (show-students class)
                           "")

                    (do-if (or teacher-of-class? (has-admin-role?))
                           (show-games-for-class class user-id)
                           "")

                    ;; TODO: see games.clj for showing play vs. resume
                    (do-if student-of-class?
                           (html
                            [:h3 "Games you can play in this class"]
                            (let [games
                                  (k/exec-raw
                                   ["SELECT 'play',game.id,game.name AS game, game.city
                                       FROM game
                                 INNER JOIN game_in_class
                                         ON (game_in_class.game=game.id
                                        AND  game_in_class.class=?)"
                                    [class]] :results)]
                              [:div.rows2table
                               (rows2table games
                                           {:col-fns
                                            {:play (fn [game]
                                                     (html [:button {:onclick (str "document.location='/tour/"
                                                                                   (:id game) "';")}
                                                            "Play"]))}
                                            :th-styles
                                            {:play "text-align:center;width:3em"}
                                            :cols [:play :game :city]})]))
                            "")

                    (do-if teacher-of-class?
                           (html
                            [:div.dangerzone
                             [:h4 "Delete class"]
                             [:div {:style "float:right"}
                              [:form
                               {:method "post"
                                :action (str class "/delete")}
                               [:button.confirm_delete {:onclick (str "submit();")} "Delete Class"]]]
                             ]
                            )
                           "")
                    
                    ])

                  request
                  resources))}))

   (GET "/:class/game/add"
        request
        (let [class-id (Integer. (:class (:route-params request)))
              user (username2userid (authentication/current request))]
          (do-if (or (is-teacher-of-class? class-id user)
                     (has-admin-role?))
                 {:headers html-headers
                  :body
                  (let [class (Integer. (:class (:route-params request)))]
                    (page "Add a game to this class"
                          (let [user-id (username2userid (authentication/current request))
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
                            [:div.major
                             [:h2 (banner [{:href "/class"
                                            :content "Classes"}
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

                              (add-existing-game-to-this-class class-id user-id)

                              ]])

                          request
                          resources
                          
                          ))}
                 
                 )
          ))

      (POST "/:class/game/:game/add" request
            (let [class-id (Integer. (:class (:route-params request)))
                  user (username2userid (authentication/current request))]
              (do-if (is-teacher-of-class? class-id user)
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
                  :headers {"Location" (str "/class/" class "?message=Added game.#games")}}))))

      (POST "/disenroll/:class" request
            (do-if-authenticated
             (let [debug (log/debug (str "/disenroll/:class with route params:" (:route-params request)))
                   class (Integer. (:class (:route-params request)))
                   student (username2userid (authentication/current request))]
               (k/exec-raw ["DELETE FROM student_in_class
                                   WHERE student = ? AND class=?" [student class]])
               {:status 302
                :headers {"Location" (str "/class?message=Left.")}})))

      (POST "/enroll/:class" request
            (do-if-authenticated
             (let [debug (log/debug (str "/enroll/:class with route params:" (:route-params request)))
                   class (Integer. (:class (:route-params request)))
                   student (username2userid (authentication/current request))]
               (k/exec-raw ["INSERT INTO student_in_class (student,class) 
                                  SELECT ?,?
                                   WHERE 
                              NOT EXISTS (SELECT student,class 
                                            FROM student_in_class 
                                           WHERE student = ? AND class=?)" [student class student class]])
               {:status 302
                :headers {"Location" (str "/class?message=Enrolled.")}})))

      (POST "/:class/game/:game/delete" request
            (let [class-id (Integer. (:class (:route-params request)))
                  user (username2userid (authentication/current request))]
              (do-if (is-teacher-of-class? class-id user)
                     (let [debug (log/debug (str "/:class/game/:game/add with route params:" (:route-params request)))
                           class (Integer. (:class (:route-params request)))
                           game (Integer. (:game (:route-params request)))]
                       (k/exec-raw ["DELETE FROM game_in_class WHERE game=? AND class=?" [game class]])
                       {:status 302
                        :headers {"Location" (str "/class/" class "?message=Removed game.#games")}}))))
      
      (POST "/:class-to-delete/delete" request
            (let [class-id (Integer. (:class-to-delete (:route-params request)))
                  user (username2userid (authentication/current request))]
              (do-if
               (do
                 (log/debug (str "Delete class: " class-id ": can user: '" user "' delete this class?"))
                 (is-teacher-of-class? class-id user))
               (do
                 (let [message (delete-class (:class-to-delete (:route-params request)))]
                   {:status 302
                    :headers {"Location" (str "/class/" "?message=" message)}}))
               (do
                 (log/warn (str "User:" user " tried to delete class: " class-id " but was denied authorization to do so."))
                 {:status 302
                  :headers {"Location" (str "/class/" class-id "?message=Unauthorized+to+delete+class:" class-id)}}))))
      )
  )

(defn show-students [class]
  (html
   [:h3 "Students"]
   (let [students (k/exec-raw
                   ["SELECT trim(given_name || ' ' || family_name) AS name,
                                               picture,email,
                                               to_char(student_in_class.enrolled,?) AS enrolled
                       FROM vc_user
                 INNER JOIN student_in_class
                         ON (student_in_class.student = vc_user.id) AND (class = ?)"
                    [time-format class]] :results)]
     [:div.rows2table
      (rows2table students
                  {:cols [:name :picture :email :enrolled]
                   :if-empty-show-this-instead "No students in this class yet."
                   :col-fns
                   {:picture (fn [picture] (html [:img {:width "50px" :src (:picture picture)}]))
                    :name (fn [student]
                            (html [:a {:href (str "/student/" (:id student))}
                                   (:name student)]))}})])
   [:div.add {:style "display:none"} ;; disabled: not implemented yet - students must self-enroll in classes.
    [:a {:href (str "/class/" class "/student/add")}
     "Add a new student"]]))

(defn show-games-for-class [class user-id]
  (html
   [:h3 "Games"]
   (let [class-map (first (k/exec-raw ["SELECT * FROM class WHERE id=?"
                                       [class]] :results))
         games (k/exec-raw
                ["SELECT 'remove',game.id,game.name AS game,game.city,
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
     [:div#games
      [:div.rows2table
       (rows2table games
                   {:cols [:remove :game :city :created_by :added]
                    :td-styles {:remove "text-align:center"}
                    :if-empty-show-this-instead "No games for this class yet."
                    :th-styles {:remove "text-align:center;width:3em"}
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
                   )]

      (add-existing-game-to-this-class class user-id)
      
      (new-game-form {:header "Create a new game for this class"
                      :class class
                      :target-language (:language class-map)
                      })])))

(defn is-teacher-of-class? [class-id user]
  "return true iff user (identified by their email) is the teacher of the class whose is class-id"
  (log/debug (str "is-owner-of: class-id:" class-id))
  (log/debug (str "is-owner-of: user:   " user))
  (let [result (first (k/exec-raw ["SELECT 1
                                      FROM class
                                INNER JOIN vc_user
                                        ON (vc_user.id = ?)
                                     WHERE class.id=?
                                       AND class.teacher = vc_user.id"
                                   [user class-id]]
                                  :results))]
    (not (nil? result))))

(defn delete-class [class-id]
  (if class-id
    (do
      (log/debug (str "DELETING CLASS: " class-id))
      (let [class-id (Integer. class-id)
            class-row (first (k/exec-raw ["SELECT * FROM class WHERE id=?" [class-id]] :results))]
        (log/debug (str "CLASS ROW: " class-row))
        (k/exec-raw ["DELETE FROM game_in_class WHERE class=?" [class-id]])
        (k/exec-raw ["DELETE FROM student_in_class WHERE class=?" [class-id]])
        (k/exec-raw ["DELETE FROM class WHERE id=?" [class-id]])
        (str "Deleted class: " (:name class-row))))

    ;; class-id is null:
    (let [error-message (str "Error: no class to delete: class-id was null.")]
      (log/error error-message)
      error-message)))

(defn add-existing-game-to-this-class [class user-id]
  ;; This query shows all games that are *not* in this game.
  (let [class-map (first (k/exec-raw ["SELECT * FROM class WHERE id=?"
                                       [class]] :results))
        games (k/exec-raw
               ["SELECT 'Add',game.id,game.name AS game,city,
                                           to_char(game.created_on,?) AS created,
                                           trim(game_creator.given_name || ' ' || game_creator.family_name) AS creator,
                                           game_creator.email AS creator_email
                                      FROM game
                                 LEFT JOIN vc_user AS game_creator
                                        ON (game.created_by = game_creator.id)
                                     WHERE game.target = ?
                                       AND game.created_by = ?
                                       AND game.id
                                    NOT IN (SELECT game
                                              FROM game_in_class
                                             WHERE class = ?)"
                                            [time-format (:language class-map) user-id class]] :results)]
    [:div
     [:h3 "Choose an existing game to add to this class"]
     [:div.rows2table
      (rows2table games
                  {:if-empty-show-this-instead
                   [:div [:i "You have no existing games that you can add to this class."]]
                   :cols [:add :game :city :created :creator :creator_email]
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
                  )]
     ]))
