(ns italianverbs.student
  (:use [hiccup core])
  (:require
   [clj-time.core :as t]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [formative.core :as f]
   [formative.parse :as fp]
   [italianverbs.html :as html]
   [italianverbs.korma :as db]
   [italianverbs.korma :as db]))

(declare tr-students)

(defn show [request]
  (let [students
        (db/fetch :user)]; {:type "student"})]
    (html
     [:div.major
      [:h2 "Students"]
      (if (empty? students)
        [:p "No students." ]
        [:table.classes.table-striped
         [:tr
          [:th]
          [:th "Name"]
          [:th "Email"]]
         (tr-students students)])

      [:div {:style "float:left;width:100%"} [:a {:href "/student/new"}
                                              "Enroll a new student"]]])))

(defn tr-students [students])

(defn show-one [student-id]
  (html
   [:div.major
    [:h2 "Students (show one)"]]))

(defn delete [ & args])

(defn new [ & args])

(defn delete-class-from-student [ & args])

(defn add-class-to-student [ & args])

