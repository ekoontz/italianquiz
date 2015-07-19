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
                {:onload "student_onload();"
                 :css ["/css/student.css"]
                 :jss ["/js/student/js"]})}))

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
                  {:onload "student_onload();"
                   :css ["/css/student.css"]
                   :jss ["/js/student/js"]}))}))))

(defn show-students [request]
  (str "students.."))

(def enroll-form
  {:action "/student/new"
   :fields [{:name :name  :size 50 :label "Name"}
            {:name :email :size 50 :label "Email" :type :email}]
   :method "post"
   :validations [[:required [:name :email]]]})

(defn show-enroll-form [ params & {:keys [problems]}]
  (let [defaults {}]
    (html
     [:div.form {:style "float:left;width:100%;margin:0.5em"}
      [:h3 "Enroll a new student:"]
      (f/render-form (merge enroll-form
                            {:values (merge defaults (:form-params params))
                             :action "/student/new"
                             :method "post"
                             :problems problems}))])))

(defn show [ & [ request enroll-form ] ]
  (let [students
        (db/fetch :user)
        enroll-form (if enroll-form enroll-form
                        (show-enroll-form (:form-params request)))]
    (html
     [:div.major
      [:h2 "Students"]
      (table students)
      enroll-form])))

(defn table [ students & [form-column-fns]]
  (html
   (if (empty? students)
     [:p "No students." ]
     [:table.classes.table-striped
      [:tr
       [:th]
       [:th "Name"]
       [:th "Email"]]
      (tr students 1 form-column-fns)])))

(defn tr [rows & [i form-column-fns]]
  (let [i (if i i 1)]
    (if (not (empty? rows))
      (let [row (first rows)]
        (html
         [:tr
          [:th.num i]
          [:td [:a {:href (str "/student/" (:id row) )} (:fullname row)]]
          [:td [:a {:href (str "/student/" (:id row) )} (:email row)]]
          (if form-column-fns
            [:td
             (form-column-fns row)])
          ]
         
         (tr (rest rows) (+ 1 i) form-column-fns))))))

(defn classes-for-student [student-id]
  (let [student-id (Integer. student-id)]
    (k/exec-raw ["SELECT  * 
                    FROM  classes 
              INNER JOIN  students_in_classes
                      ON  (students_in_classes.class = classes.id)
                     AND  (students_in_classes.student = ?)" [student-id]]
                :results)))

(defn classes-student-not-in [student-id]
  (let [student-id (Integer. student-id)]
    (k/exec-raw ["SELECT  * 
                    FROM  classes
                   WHERE classes.id NOT IN (SELECT classes.id 
                                             FROM  classes
                                       INNER JOIN  students_in_classes
                                               ON  (students_in_classes.class = classes.id)
                                              AND  (students_in_classes.student = ?))" [student-id]]
                :results)))

(defn show-one [student-id & [haz-admin]]
  (let [haz-admin (if haz-admin haz-admin false)
        student-id (Integer. student-id)
        student (first (db/fetch :student {:_id student-id}))]
    (html
     [:div.major
      [:h2 [:a {:href "/student"} "Students"] " &raquo; " (:fullname student)]
      
      (if (= true haz-admin)
        [:div.testeditor {:style "margin-left:0.25em;float:left;width:100%;"}

         [:h3 "Classes for this student"]
         [:div {:style "float:left;width:100%"}
          (html/table (classes-for-student student-id)
                      :haz-admin haz-admin
                      :none "This student is not enrolled in any classes yet."
                      :th (fn [key] (case key
                                      :class html/hide
                                      :created html/hide
                                      :id [:th "Remove from class"]
                                      :name [:th "Name"]
                                      :student html/hide
                                      :updated html/hide
                                      (html/default-th key)))
                      :td (fn [row key] (case key
                                          :class html/hide
                                          :created html/hide
                                          :id [:td 
                                                 [:form
                                                  {:action (str "/class/" (:id row) "/removeuser/" student-id)
                                                   :method "post"}

                                                   [:input {:type "hidden" :name "redirect"
                                                           :value (str "/student/" student-id)}]
                                                  
                                                  [:button {:onclick "submit()"} "Remove"]]]

                                          :name [:td [:a {:href (str "/class/" (get row :id))}
                                                      (get row :name)
                                                        ]]
                                          :student html/hide
                                          :updated html/hide
                                          (html/default-td (get row key)))))]

         [:h3 "Add this student to a class"]
         [:div {:style "float:left;width:100%"}
          (html/table (classes-student-not-in student-id)
                      :haz-admin haz-admin
                      :none "This student is in every class."
                      :th (fn [key] (case key
                                      :class html/hide
                                      :created html/hide
                                      :id [:th "Add"]
                                      :name [:th "Name"]
                                      :student html/hide
                                      :updated html/hide
                                      [:th key]))
                      :td (fn [row key] (case key
                                          :class html/hide
                                          :created html/hide
                                          :name [:td [:a {:href (str "/class/" (get row :id))}
                                                      (get row key)]]
                                          :id [:td 
                                                 [:form
                                                  {:action (str "/class/" (:id row) "/add/" student-id)
                                                   :method "post"}
                                                  [:input {:type "hidden" :name "redirect"
                                                           :value (str "/student/" student-id)}]
                                                  [:button {:onclick "submit()"} "Add"]]]
                                          :student html/hide
                                          :updated html/hide
                                          [:td (get row key)])))]

         ;; TODO: use formative form here rather than handmade form.
         [:h3 "Update student details"]
         [:div {:style "float:left;width:100%"}
          [:form {:action (str "/student/" (:id student) "update") :method "post"}
           [:input {:label "Full Name" :name "fullname" :value (:fullname student)}]
           [:input {:label "Email" :name "email" :value (:email student)}]
           [:button {:onclick "submit()"} "Update"]]]

         [:h3 "Delete student"]
         [:div {:style "float:left;width:100%"}
          [:form {:action (str "/student/" (:id student) "/delete")
                  :method "post"}
           [:button {:onclick "submit()"} "Delete"]]]])])))

;; TODO: show this student's:
;;  - class history
;;  - test history

(defn delete [student-id]
  (let [student-id (Integer. student-id)]
    (log/info (str "deleting student: " student-id))
    (db/fetch-and-modify :student (db/object-id student-id) {} true)
    {:message "deleted student."}))

;; TODO: check for duplicate insertions.
(defn new [request]
  (log/info (str "class/new with request: " (:form-params request)))
  (fp/with-fallback #(html/page "Students"
                                (show request
                                      (show-enroll-form request :problems %))
                                request)
    (let [values (fp/parse-params enroll-form (:form-params request))]
      (let [created-at (t/now)]
        (let [new-student
              (db/insert! :student {:created (str created-at)
                                    :updated (str created-at)
                                    :fullname (get (:form-params request) "name")
                                    :username (get (:form-params request) "email") ;; for now, username is simply email.
                                    :email (get (:form-params request) "email")})
              new-student-id
              (:id new-student)]
          {:status 302
           :headers {"Location" (str "/student/" new-student-id "?message=created")}})))))

(defn delete-class-from-student [ & args])

(defn add-class-to-student [ & args])

