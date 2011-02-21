(ns foo.core
  (:use compojure.core)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(load-file "src/foo/html.clj")

(defroutes main-routes
  (GET "/" 
       {session :session}
       {
       :session session
       :body (str banner (message "Welcome.") (sessiondata session) footer)
       })

  (GET "/test/" 
       {session :session}
       (str banner (message "Tests go here.")
	    (sessiondata session)
	    footer))

  (GET "/session/"
       {session :session}
       {
       :body (str banner (sessiondata session) footer)
       })

  (GET "/session/set/"  
       {session :session}
       {
       :session {:val 123 :fruit "grape"}
       :status 302
       :headers {"Location" "/?msg=set"}
       })

  (GET "/session/clear/" 
       {} 
       {
       :session {}
       :status 302
       :headers {"Location" "/?msg=cleared"}
       })

  (route/resources "/")
  (route/not-found (str banner (message "Sorry, page not found.") footer)))

; http://weavejester.github.com/compojure/compojure.handler-api.html
; site function

;Usage: (site routes & [opts])

;Create a handler suitable for a standard website. This adds the
;following middleware to your routes:
;  - wrap-session
;  - wrap-cookies
;  - wrap-multipart-params
;  - wrap-params
;  - wrap-nested-params
;  - wrap-keyword-params
; A map of options may also be provided. These keys are provided:
;  :session - a map of session middleware options

(def app
  (handler/site main-routes))

