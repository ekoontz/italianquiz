(ns italianverbs.core
  (:require
   [cemerick.friend :as friend]
   [cemerick.friend [workflows :as workflows]]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [compojure.core :refer [context defroutes GET PUT POST DELETE ANY]]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [environ.core :refer [env]]
   [friend-oauth2.workflow :as oauth2]
   [hiccup.page :as h]
   [italianverbs.about :as about]
   [italianverbs.admin :as admin]
   [italianverbs.authentication :as auth]
   [italianverbs.auth.google :as google]
   [italianverbs.class :as class]
   [italianverbs.editor :as editor]
   [italianverbs.game :as game]
   [italianverbs.html :as html]
   [italianverbs.me :as me]
   [italianverbs.student :as student]
   [italianverbs.studenttest :as studenttest]
   [italianverbs.tour :as tour]
   [italianverbs.user :as user]
   [italianverbs.verb :as verb]
;   [italianverbs.workbook :as workbook]

   [ring.adapter.jetty :as jetty]
   [ring.middleware.session :as session]
   [ring.middleware.session.cookie :as cookie]
   [ring.middleware.stacktrace :as trace]
   [ring.util.codec :as codec]
   [ring.util.response :as resp]

))

;; TODO: add service env to just redirect temporarily (302) to
;;  /undergoing-maintenance"undergoing maintenance" and 
;; for now we just use 'heroku maintenance:on -a verbcoach'

;; not used at the moment, but might be handy someday:
(def server-hostname (.getHostName (java.net.InetAddress/getLocalHost)))

(defroutes main-routes
  ;; TODO: redirect to /games
  (GET "/" request
       (resp/redirect "/class"))

  (context "/admin" []
           admin/routes)

  (context "/auth" []
           auth/routes)

  (context "/class" []
           class/routes)

  (context "/editor" []
           editor/routes)

  (context "/game" []
           game/routes)

  ;; TODO: disable
  (context "/gen" []
           verb/routes)

  (context "/me" []
           me/routes)

  (context "/student" []
           student/routes)

    ;; TODO: disable
  (context "/test" []
           studenttest/routes)

  (context "/tour" []
           tour/routes)

  (context "/user" []
           user/routes)

  ;; TODO: uncomment and make dependent on a environment (i.e. non-production).
;  (context "/workbook" []
;           workbook/routes)

  (GET "/about" request
       about/routes)

  (GET "/verb" request
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body "verb"})

  (route/resources "/webjars" {:root "META-INF/resources/webjars/foundation/4.0.4/"})
  (route/resources "/")

  ;; TODO: how to show info about the request (e.g. request path and error code).

  ;; currently, we show this '404' error regardless of what the error actually is (might be 500 or otherwise).
;  (route/not-found (html/page "Non posso trovare questa pagina (page not found)." (str "Non posso trovare questa pagina. Sorry, page not found. ")))
)

;; TODO: clear out cache of sentences-per-user session when starting up.
(def app
  (handler/site
   (-> main-routes
       (friend/authenticate
        {:allow-anon? true
         :login-uri "/auth/internal/login"
         :default-landing-uri "/"
         :unauthorized-handler #(-> 
                                 (html/page "Unauthorized" (h/html5 
                                                             [:div {:class "major tag"}
                                                              [:h2 "Unauthorized"]
                                                              [:p "You do not have sufficient privileges to access " (:uri %) "."]]) %)
                                 resp/response
                                 (resp/status 401))
         :credential-fn #(auth/credential-fn %)
         :workflows [

                     ;; form-based auth (insecure unless using HTTPS to POST form data)
                     (workflows/interactive-form)

                     ;; Google OpenAuth (auth info protected by HTTPS)
                     (oauth2/workflow google/auth-config)

                     ;; Simply uses sets the user's ring-session as their authentication (if the client
                     ;; does not yet have a ring-session - if they have one, use that).
                     (auth/no-auth)

                     ;; add additional authentication methods below, e.g. Facebook, Twitter, &c.
                     ]}))))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (handler/site))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

