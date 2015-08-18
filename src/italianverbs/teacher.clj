(ns italianverbs.teacher
  ;; probably bad idea to exclude this (class), at least from the
  ;; point of view of readability by other developers, since they are
  ;; basic.
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
   [italianverbs.user :refer [do-if do-if-authenticated do-if-teacher login-form menubar-info-for-user
                              username2userid]]
   [korma.core :as k]))

(def html-headers {"Content-Type" "text/html;charset=utf-8"})
(def json-headers {"Content-type" "application/json;charset=utf-8"})

(defn resources [request]
  {:onload ""
   :css ["/css/student.css"]
   :jss ["/js/student.js"]
   :show-login-form (login-form request)
   :menubar (menubar (menubar-info-for-user request))})

(declare body)
(declare class-table)

(def routes
  (compojure/routes
   (GET "/:teacher" request
        (do-if-authenticated
         {:headers html-headers
          :body
          (page
           (str "Choose your class")
           (body (:teacher (:route-params request))
                 "Choose your class"
                 ".."
                 request))
          :status 200}))))

(defn onload []
  "")

(defn class-table [teacher-id]
  (html
   (let [results (k/exec-raw ["SELECT id,name AS class
                                 FROM class
                                WHERE teacher=?"
                              [(Integer. teacher-id)]] :results)]
     [:div.rows2table
      (html/rows2table results

                       {:cols [:class]
                        :col-fns {:class (fn [class]
                                           (html [:a {:href (str "/class/" (:id class))}
                                                  (:class class)]))}}
                       )])))

(defn body [teacher-id title content request]
  (let [teacher (:name (first (k/exec-raw ["SELECT given_name || ' ' || family_name AS name,email 
                                              FROM vc_user 
                                             WHERE id=?"
                                           [(Integer. teacher-id)]] :results)))]
    (html/page
     title
     (html
      [:div {:class "major"}
       [:h2 
        (banner (concat
                 [{
                   :content teacher}]
                 ) ;; concat
               ) ;; banner
        ] ;; :h2]

       (class-table teacher-id)

       ])
     request
     resources)))





                     
