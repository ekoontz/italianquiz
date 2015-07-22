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

                     [:h2 "Classes I'm teaching"]

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
                    [:h2 "Classes I'm enrolled in"]

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
                        class-map (first (k/exec-raw ["SELECT * FROM class WHERE id=?"
                                                      [class]] :results))]
                   [:div#students {:class "major"}
                    [:h2 (banner [{:href "/class"
                                   :content "My Classes"}
                                  {:href nil
                                   :content (:name class-map)}])]
                    [:div {:style "padding:1em"}
                     "(class info goes here.)"]])
                request
                resources))}))
   ))

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


