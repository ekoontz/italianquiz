(ns italianverbs.menubar
  (:use
   [hiccup core page]
   [ring.util.codec :as codec])
  (:require
   [clojure.tools.logging :as log]
   [italianverbs.user :as user]))

(declare menuitem)

(defn menubar [session-row current-url haz-authentication & [suffixes]]
  (let [roles (:roles haz-authentication)
        haz-admin? (user/haz-admin?)
        haz-teacher? (user/has-teacher-role)
        ]

    (log/debug (str "Drawing menubar with current-url=" current-url))
    (log/debug (str "Menubar with suffixes: " suffixes))
    (html
     [:div#menubar

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"/login" current-url))
                     (= current-url "/auth/login")
                     (and (not (nil? current-url))
                          (re-find #"/about" current-url)))
                 :show? true
                 :current-url current-url 
                 :text "About" 
                 :url-for-this-item "/about"
                 :requires-admin false
                 :requires-authentication false})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"/cloud" current-url))
                     (= current-url "/cloud")
                     (and (not (nil? current-url))
                          (re-find #"/cloud" current-url)))
                 :show? false
                 :current-url current-url 
                 :text "Cloud Game" 
                 :url-for-this-item "/cloud"
                 :requires-admin false
                 :requires-authentication false})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"/editor" current-url))
                     (= current-url "/editor")
                     (and (not (nil? current-url))
                          (re-find #"/editor" current-url)))
                 :show? (or haz-admin? haz-teacher?)
                 :current-url current-url 
                 :text "Edit Games"
                 :url-for-this-item "/editor"})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"/editor" current-url))
                     (= current-url "/editor")
                     (and (not (nil? current-url))
                          (re-find #"/editor" current-url)))
                 :show? (or haz-admin? haz-teacher?)
                 :current-url current-url 
                 :text "My Students"
                 :url-for-this-item "/student"})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"/tour" current-url))
                     (= current-url "/tour")
                     (and (not (nil? current-url))
                          (re-find #"/tour" current-url)))
                 :show? false
                 :current-url current-url 
                 :text "Map Tour" 
                 :url-for-this-item "/tour"
                 :requires-admin false
                 :requires-authentication false})

      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"/class" current-url))
                 :current-url current-url
                 :text "Classes"
                 :url-for-this-item (str "/class" (if (get suffixes :class)
                                                    (get suffixes :class)))
                 :show? (and false haz-admin?)})

      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"/test" current-url))
                 :current-url current-url
                 :text "Tests"
                 :url-for-this-item "/test"
                 :show? (and false haz-admin?)})

      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"/me" current-url))
                 :current-url current-url
                 :text "My Profile"
                 :url-for-this-item "/me"
                 :show? true})

      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"/admin" current-url))
                 :current-url current-url
                 :text "Admin"
                 :requires-admin true
                 :url-for-this-item "/admin"
                 :show? haz-admin?})
      
      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"/class" current-url))
                 :current-url current-url
                 :text "My Classes"
                 :url-for-this-item (str "/class/my" (if (get suffixes :class)
                                                       (get suffixes :class)))
                 :show? (and false haz-authentication (not haz-admin?))})])))

(defn- menuitem [ {selected? :selected?
                   show? :show?
                   current-url :current-url
                   text :text
                   url-for-this-item :url-for-this-item
                   requires-admin :requires-admin
                   requires-authentication :requires-authentication
                   haz-admin :haz-admin
                   haz-authentication :haz-authentication}]
  (if show?
    [:div
     (if (or selected?
             (= current-url url-for-this-item))
       {:class "selected"})
     [:a {:href url-for-this-item} text]]))



