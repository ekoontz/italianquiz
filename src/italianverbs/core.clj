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
   [italianverbs.auth.google :as google-auth]
   [italianverbs.auth.internal :as internal-auth]
   [italianverbs.class :as class]
   [italianverbs.editor :as editor]
   [italianverbs.game :as game]
   [italianverbs.html :as html]
   [italianverbs.me :as me]
   [italianverbs.student :as student]
   [italianverbs.studenttest :as studenttest]
   [italianverbs.teacher :as teacher]
   [italianverbs.tour :as tour]
   [italianverbs.user :as user]
;   [italianverbs.workbook :as workbook]

   [ring.adapter.jetty :as jetty]
   [ring.middleware.session :as session]
   [ring.middleware.session.cookie :as cookie]
   [ring.middleware.stacktrace :as trace]
   [ring.util.codec :as codec]
   [ring.util.response :as resp]))

;; TODO: add service env to just redirect temporarily (302) to
;;  /undergoing-maintenance"undergoing maintenance" and 
;; for now we just use 'heroku maintenance:on -a verbcoach'

;; not used at the moment, but might be handy someday:
(def server-hostname (.getHostName (java.net.InetAddress/getLocalHost)))

(defroutes main-routes
  ;; TODO: redirect to /games
  (GET "/" request
       (resp/redirect "/about"))

  (GET "/about" request
       about/routes)

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

  (context "/me" []
           me/routes)

  (context "/student" []
           student/routes)

    ;; TODO: disable
  (context "/test" []
           studenttest/routes)

  (context "/teacher" []
           teacher/routes)

  (context "/tour" []
           tour/routes)

  (context "/user" []
           user/routes)

  (route/resources "/webjars" {:root "META-INF/resources/webjars/foundation/4.0.4/"})
  (route/resources "/")

  ;; TODO: how to show info about the request (e.g. request path and error code).

  ;; currently, we show this '404' error regardless of what the error actually is (might be 500 or otherwise).
;  (route/not-found (html/page "Non posso trovare questa pagina (page not found)." (str "Non posso trovare questa pagina. Sorry, page not found. ")))
)

;; TODO: clear out cache of sentences-per-user session when starting up.
(def app
  (handler/site 
   (friend/authenticate
    main-routes

    ;; <TODO: move this route to auth/internal.clj>
    {:allow-anon? true
     :login-uri "/login"
     :default-landing-uri "/"
     :unauthorized-handler #(-> 
                             (html/page "Unauthorized" (h/html5 

                                                        [:div {:class "major tag"}
                                                         [:h2 "Unauthorized"]
                                                         [:p "You do not have sufficient privileges to access " (:uri %) "."]]) %)
                             resp/response
                             (resp/status 401))
     :credential-fn #(internal-auth/credential-fn %)
     ;; </TODO: move this route to auth/internal.clj>

     :workflows [(workflows/interactive-form)
                 (oauth2/workflow google-auth/auth-config)]})))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (handler/site {:session {:store store}}))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

