(ns italianverbs.menubar
  (:use
   [hiccup core page]
   [ring.util.codec :as codec])
  (:require
   [clojure.tools.logging :as log]))

(declare menuitem)
(declare request-to-suffixes)

(defn menubar [{session-row :session-row
                current-url :uri
                request :request
                authenticated? :authenticated?
                haz-admin? :haz-admin?
                haz-teacher? :haz-teacher?
                extra-menu-item :extra-menu-item}]
  (let [suffixes (if request (request-to-suffixes request))]
    (log/debug (str "Drawing menubar with current-url=" current-url))
    (log/debug (str "Drawing menubar with authenticated?=" authenticated?))
    (log/debug (str "Menubar with suffixes: " suffixes))
    (html
     [:div#menubar

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"^/login" current-url))
                     (= current-url "/auth/login")
                     (and (not (nil? current-url))
                          (re-find #"^/about" current-url)))
                 :show? true
                 :current-url current-url 
                 :text "About" 
                 :url-for-this-item "/about"
                 :requires-admin false
                 :requires-authentication false})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"^/cloud" current-url))
                     (= current-url "/cloud")
                     (and (not (nil? current-url))
                          (re-find #"^/cloud" current-url)))
                 :show? false
                 :current-url current-url 
                 :text "Cloud Game" 
                 :url-for-this-item "/cloud"
                 :requires-admin false
                 :requires-authentication false})

      ;; deprecated: remove: using /game instead.
      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"^/editor" current-url))
                     (= current-url "/editor")
                     (and (not (nil? current-url))
                          (re-find #"^/editor" current-url)))
                 :show? (and false (or haz-admin? haz-teacher?))
                 :current-url current-url 
                 :text "Edit Games"
                 :url-for-this-item "/editor"})

      (menuitem {:show? haz-teacher?
                 :selected?
                 (or (and (not (nil? current-url))
                          (re-find #"^/game" current-url))
                     (= current-url "/game")
                     (and (not (nil? current-url))
                          (re-find #"^/game" current-url)))
                 :current-url current-url 
                 :text "My Games"
                 :url-for-this-item "/game"})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"^/class" current-url))
                     (= current-url "/class")
                     (and (not (nil? current-url))
                          (re-find #"^/class" current-url)))
                 :show? haz-teacher?
                 :current-url current-url 
                 :text "My Classes"
                 :url-for-this-item "/class"})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"^/tour" current-url))
                     (= current-url "/tour")
                     (and (not (nil? current-url))
                          (re-find #"^/tour" current-url)))
                 :show? false
                 :current-url current-url 
                 :text "Map Tour" 
                 :url-for-this-item "/tour"
                 :requires-admin false
                 :requires-authentication false})

      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"^/test" current-url))
                 :current-url current-url
                 :text "Tests"
                 :url-for-this-item "/test"
                 :show? (and false haz-admin?)})

      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"^/me" current-url))
                 :current-url current-url
                 :text "My Profile"
                 :show? (and false authenticated?)
                 :url-for-this-item "/me"})

      (menuitem {:selected?
                 (or (and (not (nil? current-url))
                          (re-find #"^/student" current-url))
                     (= current-url "/student")
                     (and (not (nil? current-url))
                          (re-find #"^/student" current-url)))
                 :show? (and false (or haz-admin? haz-teacher?))
                 :current-url current-url
                 :text "My Students"
                 :url-for-this-item "/student"})

      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"^/admin" current-url))
                 :current-url current-url
                 :text "Admin"
                 :requires-admin true
                 :url-for-this-item "/admin"
                 :show? haz-admin?})
      
      (menuitem {:selected?
                 (and (not (nil? current-url))
                      (re-find #"^/class" current-url))
                 :current-url current-url
                 :text "My Classes"
                 :url-for-this-item (str "/class/my" (if (get suffixes :class)
                                                       (get suffixes :class)))
                 :show? (and false authenticated? (not haz-admin?))})

      (if extra-menu-item extra-menu-item)])))

(defn menuitem [ {selected? :selected?
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

(defn request-to-suffixes [request]
  "menubar uses this to make the menubar links context-specific.."
  ;; ...e.g. if you are looking at a particular group, 
  ;; the 'generate' link should have that group id so that if you
  ;;  click the link, you will generate with that group"
  (let [route-params (:route-params request)]
    (log/debug (str "req-to-suff params: " route-params))
    {:generate (if (and route-params (:tag route-params))
                 (str (:tag route-params) "/"))}))


