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
   [italianverbs.auth :as auth]
   [italianverbs.auth.google :as google]
   [italianverbs.class :as class]
   [italianverbs.editor :as editor]
   [italianverbs.html :as html]
   [italianverbs.studenttest :as studenttest]
   [italianverbs.tour :as tour]
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
  ;; top-level page: currently redirects to the "Cloud" game.
  (GET "/" request
       (resp/redirect "/about"))

  (context "/auth" []
           auth/routes)

  (context "/class" []
           class/routes)

  (context "/editor" []
           editor/routes)

  (context "/gen" []
           verb/routes)

  (context "/test" []
           studenttest/routes)

  (context "/tour" []
           tour/routes)

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

(defn no-auth [& {:keys [login-uri credential-fn login-failure-handler redirect-on-auth?]
                  :as form-config
                  :or {redirect-on-auth? true}}]
  (fn [{:keys [request-method params form-params]
        :as request}]
    (let [ring-session (get-in request [:cookies "ring-session" :value])
          attempted-to-set-session? (get-in request [:query-params "setsession"])]
      (log/trace "Checking session config for this unauthenticated, sessionless user: request: " request)
      (cond
       (and (not ring-session)
            attempted-to-set-session?)
       (do
         (log/debug "User is blocking session cookies: not attempting to set.")
         nil)
       
       (not ring-session)
            (do
              (log/debug "Creating a session for this unauthenticated, sessionless user: request: " request)
              (let [response
                    {:status 302
                     :headers {"Location" "?setsession=true"}
                     :session {}}]
                response))
            true
            (do (log/debug "Unauthenticated user has an existing session: " ring-session)
                nil)))))

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
                     (workflows/interactive-form)
                     (oauth2/workflow google/auth-config)
                     (no-auth)
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
  (let [port (Integer. (or port (env :port) 5000))
        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (handler/site))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

