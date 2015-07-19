(ns italianverbs.student
  (:use [hiccup core])
  (:require
   [clj-time.core :as t]
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [formative.core :as f]
   [formative.parse :as fp]
   [hiccup.core :refer (html)]
   [italianverbs.authentication :as authentication]
   [italianverbs.config :refer [time-format]]
   [italianverbs.html :as html :refer [banner page rows2table]]
   [italianverbs.korma :as db]
   [italianverbs.user :refer [do-if-teacher username2userid]]
   [korma.core :as k]))

(declare headers)
(declare table)
(declare tr)
(declare show-students)
(def html-headers {"Content-Type" "text/html;charset=utf-8"})
(def json-headers {"Content-type" "application/json;charset=utf-8"})
(def resources {:onload "student_onload();"
                :css ["/css/student.css"]
                :jss ["/js/student/js"]})

(def routes
  (compojure/routes
   (GET "/" request
        (do-if-teacher
         {:body
          (page "My Students"
                (let [teacher (username2userid (authentication/current request))
                      results (k/exec-raw
                               ["SELECT id,to_char(created,?),
                                  email,trim(given_name || ' ' || family_name) AS name,
                                  picture,teacher
                             FROM vc_user WHERE teacher=?"
                                [time-format teacher]] :results)]
                  [:div#students {:class "major"}
                   [:h2 "My Students"]
                   (rows2table results
                               {:cols [:name :picture :email]
                                :th-styles {:name "width:10em;"}
                                :col-fns {:name (fn [result] (html [:a {:href (str "/student/" (:id result))}
                                                                    (:name result)]))
                                          :picture (fn [result] (html [:img {:width "50px" :src (:picture result)}]))}}
                               )])
                request
                resources)}))

   (GET "/:student" request
        (do-if-teacher
         {:body
          (let [student (Integer. (:student (:route-params request)))]
            (page "Student"
                  (let [teacher (username2userid (authentication/current request))
                        result (first (k/exec-raw
                                        ["SELECT id,to_char(created,?),
                                  email,trim(given_name || ' ' || family_name) AS name,
                                  picture,teacher
                             FROM vc_user WHERE teacher=? AND id=?"
                                         [time-format teacher student]] :results))]
                    [:div#students {:class "major"}
                     [:h2 (banner [{:href "/student"
                                    :content "My Students"}
                                   {:href nil
                                    :content (:name result)}])]
                     [:div {:style "padding:1em"}
                      [:img {:width "50px" :src (:picture result)}]]])
                  request
                  resources))}))))
