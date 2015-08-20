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
   [italianverbs.game :refer [tenses-of-game-as-human-readable verbs-of-game-as-human-readable]]
   [italianverbs.html :as html :refer [banner page rows2table]]
   [italianverbs.menubar :refer [menubar]]
   [babel.korma :as db]
   [italianverbs.user :refer [do-if do-if-authenticated do-if-teacher
                              do-if-not-teacher
                              login-form menubar-info-for-user
                              username2userid]]
   [korma.core :as k]))

(declare delete-class)
(declare headers)
(declare is-teacher-of-class?)
(declare table)
(declare tr)
(declare show-games)
(declare show-students)
(declare show-classes)

(def html-headers {"Content-Type" "text/html;charset=utf-8"})
(def json-headers {"Content-type" "application/json;charset=utf-8"})
(defn resources [request]
  {:onload "class_onload();"
   :css ["/css/class.css"]
   :jss ["/js/class/class.js"]
   :show-login-form (login-form request)
   :menubar (menubar (menubar-info-for-user request))})
   
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
                                    ["SELECT id AS class_id,class.name AS class,language,
                                             to_char(class.created,?) AS created
                                        FROM class
                                       WHERE teacher=?"
                                     [time-format userid]] :results)]
                       (rows2table results {:cols [:class :language :created]
                                            :col-fns
                                            {:language (fn [class]
                                                         (short-language-name-to-long (:language class)))
                                             :class (fn [game-in-class]
                                                      [:a {:href (str "/class/" (:class_id game-in-class))}
                                                       (:class game-in-class)])}}))
                     [:div.new
                       [:h3 "Create a new class:"]
                      
                      (f/render-form
                       {:action "/class/new"
                        :enctype "multipart/form-data"
                        :method "post"
                        :fields [{:name :name :size 50 :label "Name"}
                                 (language-radio-buttons)]})
                      ]]
                    "")

                   (do-if-not-teacher
                    [:div.new
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
                                      [userid]] :results)]
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
                     [:div
                      [:h3 "Enroll in a new class:"]
                      [:form {:action "/class/join"
                              :method "post"}]
                      (let [results (k/exec-raw
                                     ["SELECT class.id AS class_id,'enroll',class.name AS class,
                                              trim(teacher.given_name || ' ' || teacher.family_name) AS teacher,
                                              teacher.email AS email,
                                              class.language
                                         FROM class
                                    LEFT JOIN vc_user AS teacher 
                                           ON (teacher.id = class.teacher)
                                        WHERE class.id 
                                       NOT IN (SELECT class 
                                                 FROM student_in_class 
                                                WHERE student=?)"
                                      [userid]] :results)]
                        (rows2table results
                                    {:cols [:enroll :class :language :teacher :email]
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
                      ]]

                    "" ;; <- this empty string is the 'else' of the (do-if-not-teacher) above.

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
                        student-of-class?
                        (not (nil? (first
                                    (k/exec-raw
                                     ["SELECT 1 FROM student_in_class WHERE student=? and class=?"
                                      [userid class]
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
                        teacher-of-class? (= userid (:teacher_user_id class-map))]
                   [:div {:class "major" :foo 42}
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
                      ]]

                    (do-if teacher-of-class?
                           (show-students class)
                           "")

                    (do-if teacher-of-class?
                           (show-games class)
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
                                           {:col-fns {:play (fn [game]
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
          (do-if (is-teacher-of-class? class-id user)
                 {:headers html-headers
                  :body
                  (let [class (Integer. (:class (:route-params request)))]
                    (page "Add a game to this class"
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
                            [:div#students {:class "major" :foo 43}
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

                              [:h3 "Choose a game to add to this class"]

                              ;; This query shows all games that are *not* in this game.
                              (let [games (k/exec-raw
                                           ["SELECT 'Add',game.id,game.name AS game,city,
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
                                             {:cols [:add :game :city :created :creator :creator_email]
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

                              [:div {:style "float:left;width:100%;margin:0.5em;"}
                               [:a {:href "/game#new"} "Create a new game"]
                               ]

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
                  :headers {"Location" (str "/class/" class "?message=Added game.")}}))))

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
                        :headers {"Location" (str "/class/" class "?message=Removed game.")}}))))
      
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
                   :col-fns
                   {:picture (fn [picture] (html [:img {:width "50px" :src (:picture picture)}]))
                    :name (fn [student]
                            (html [:a {:href (str "/student/" (:id student))}
                                   (:name student)]))}})])
   [:div.add {:style "display:none"} ;; disabled: not implemented yet - students must self-enroll in classes.
    [:a {:href (str "/class/" class "/student/add")}
     "Add a new student"]]))

(defn show-games [class]
  (html
   [:h3 "Games"]
   (let [games (k/exec-raw
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
     [:div.rows2table
      (rows2table games
                  {:cols [:remove :game :city :created_by :added]
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
     "Add a game to this class"]]))

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
