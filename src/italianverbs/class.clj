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
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [formative.core :as f]
   [hiccup.core :refer (html)]
   [italianverbs.authentication :as authentication]
   [italianverbs.config :refer [short-language-name-to-long time-format]]
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
                                 ;; TODO: use (config/language-radio-buttons)
                                 {:name :lang :label "Language" :type "radios"
                                  :options [{:value "es" :label "Español"}
                                            {:value "it" :label "Italiano"}]}]})
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

                     ;; TODO: change LEFT JOIN to INNER JOIN after development is done.
                     [:h3 "Games"]
                     (let [games (k/exec-raw
                                  ["SELECT game.id,game.name AS game,
                                           to_char(game_in_class.added,?) AS added
                                      FROM game
                                 LEFT JOIN game_in_class
                                        ON (game_in_class.game=game.id)"
                                   [time-format]] :results)]
                       [:div.rows2table
                        (rows2table games
                                    {:cols [:game :added]
                                     :col-fns
                                     {:game (fn [game] (html [:a {:href (str "/game/" (:id game))}
                                                              (:game game)]))}}
                                     )])
                     [:div.add 
                      [:a {:href (str "/class/" class "/game/add")}
                       "Add a new game"]]

                     ]])
                request
                resources))}))


      (GET "/:class/game/add" request
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
                                 LEFT JOIN game_in_class
                                        ON (game_in_class.game=game.id)
                                       AND (game.target=?)
                                 LEFT JOIN vc_user AS game_creator
                                        ON (game.created_by = game_creator.id)"
                                   [time-format (:language class-map)]] :results)]
                       [:div.rows2table
                        (rows2table games
                                    {:cols [:add :game :created :creator :creator_email]
                                     :col-fns
                                     {:add (fn [game] (html [:button "Add"]))
                                      :game (fn [game] (html [:a {:href (str "/game/" (:id game))}
                                                              (:game game)]))
                                      :created (fn [game] (html [:a {:href (str "/game/" (:id game))}
                                                                 (:created game)]))
                                      }}
                                     )])

                     ]])

                request
                resources))}))))


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


