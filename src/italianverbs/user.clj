(ns italianverbs.user
  (:refer-clojure :exclude [get-in resolve])
  (:require
   [cemerick.friend :as friend]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [compojure.core :as compojure :refer [GET PUT POST DELETE ANY]]
   [environ.core :refer [env]]

   [italianverbs.authentication :as authentication]
   [dag-unify.core :as unify :refer [deserialize get-in ref? strip-refs unify]]

   [korma.core :as k]))

(declare add-role-to-user)

(declare is-admin?)
(declare remove-role-from-user)
(declare user-model)

(def routes
  (let [headers
        {"Content-Type" "text/html;charset=utf-8"}]
    (compojure/routes

     (GET "/" request
          {:headers headers
           :status 200
           :body "/user/"})

     (POST "/:user-id/add/:role" request
           (is-admin?
            (let [user-id (-> request :route-params :user-id)
                  role (-> request :route-params :role)]
              (log/debug "adding role: " role " to user: " user-id)
              (add-role-to-user user-id role)))
           )

     (POST "/:user-id/delete/:role" request
           (is-admin?
            (let [user-id (-> request :route-params :user-id)
                  role (-> request :route-params :role)]
              (log/debug "deleting role: " role " to user: " user-id)
              (remove-role-from-user user-id role))))

     )))

(defn add-role-to-user [user-id role]
  "add a given role e.g. :admin to a user with given user-id."
  (k/exec-raw [(str "INSERT INTO vc_user_role (user_id,role)
                          VALUES (?,?)")
               [user-id role]]))

(defn remove-role-from-user [user-id role]
  "add a given role e.g. :admin to a user with given user-id."
  (k/exec-raw [(str "DELETE FROM vc_user_role 
                           WHERE (user_id=?)
                             AND (role=?)")
               [user-id role]]))

(defn roles-of-user
  "return the roles of user whose id is user-id"
  ([user-id]
     (let [db-result
           (:roles
            (first
             (k/exec-raw [(str "SELECT array_accum(role) AS roles
                                  FROM vc_user_role 
                                 WHERE user_id=?
                              GROUP BY user_id")
                          [user-id]] :results)))]
       (if db-result
         (cons :student (map keyword (.getArray db-result)))))))

(defn roles-of-email
  "return the roles of user whose email is email"
  ([email]
     (let [db-result
           (:roles
            (first
             (k/exec-raw [(str "SELECT array_accum(role) AS roles
                                  FROM vc_user_role 
                            INNER JOIN vc_user 
                                    ON (vc_user_role.user_id = vc_user.id)
                                   AND (vc_user.email = ?)
                              GROUP BY user_id")
                          [email]] :results)))]
       (if db-result
         (cons :student (map keyword (.getArray db-result)))))))


(defn get-new-question [game-id & [{user-id :user-id}]]
  "Ask the expression database for a question for a given game 
   and user-id (if supplied). The question will be a map
   with the following fields:
    - a question id
    - the source expression
    - a list of the the possible correct expressions
  As a side-effect,
   the database will create a new row for the user (again, if supplied)
   for this question."
  )

(defn set-question-issued-at [timestamp]
  "")

(defn show-question-to-user [])

(defn handle-user-response [])

(defn user-model [request]
  [:table.user
   
   [:tr
    [:td
     [:div
      {:class "coord word"}
      "parlare"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "present"]
    ]
    [:td
     [:div
      {:class "coord p075"}
      "future"]
    ]
    [:td
     [:div
      {:class "coord p050"}
      "passato"
      ]
    ]
    [:td
     [:div
      {:class "coord p025"}
      "imperfect"
      ]
    ]
    [:td
     [:div
      {:class "coord p000"}
      "conditional"
      ]
    ]
    ]

   [:tr
    [:td
     [:div
      {:class "coord word"}
      "leggere"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "present"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "future"]
    ]
    [:td
     [:div
      {:class "coord p100"}
      "passato"
      ]
    ]
    [:td
     [:div
      {:class "coord p075"}
      "imperfect"
      ]
    ]
    [:td
     [:div
      {:class "coord p075"}
      "conditional"
      ]
    ]
    ]

   ]
  )

(defn username2userid [username]
  (when username
    (:id (first (k/exec-raw [(str "SELECT id FROM vc_user WHERE email=?")
                             [username]] :results)))))

(defn session2userid [session]
  (when session
    (:user_id (first (k/exec-raw [(str "SELECT user_id FROM session 
                                    WHERE ring_session=?::uuid 
                                      AND user_id IS NOT NULL")
                             [session]] :results)))))


(defn haz-admin? [ & [request]]
  (let [authentication (friend/current-authentication)]
    (log/debug (str "haz-admin: current authentication:" (if (nil? authentication) " (none - authentication is null). " authentication)))
    (if (= (:allow-internal-authentication env) "true")
      (log/warn (str "ALLOW_INTERNAL_ADMINS is enabled: allowing internally-authentication admins - should not be enabled in production")))

    (let [result
          (and (not (nil? authentication))
               (let [username (authentication/current request)]
                 (log/debug (str "haz-admin? username: " username))
                 (log/debug (str "roles of username: " (roles-of-email username)))
                 (some #(= % :admin) (roles-of-email username))))]
      (log/debug (str "ARE YOU ADMIN? " result))
      result)))

(defn has-admin-role [ & [request]]
  (let [authentication (friend/current-authentication)]
    (log/debug (str "haz-admin: current authentication:" (if (nil? authentication) " none " authentication)))
    (if (= (:allow-internal-authentication env) "true")
      (log/warn (str "ALLOW_INTERNAL_AUTHENTICATION is enabled: allowing internally-authentication admins - should not be enabled in production")))

    (and (not (nil? authentication))
         ;; Google-authenticated teachers - note that authorization is here, not within google/ - we make the decision about whether the user
         ;; is a teacher here.
         (let [username (authentication/current request)]
           (some #(= % :admin) (roles-of-email username))))))

(defn has-teacher-role [ & [request]]
  (let [authentication (friend/current-authentication)]
    (log/debug (str "haz-admin: current authentication:" (if (nil? authentication) " none " authentication)))
    (if (= (:allow-internal-authentication env) "true")
      (log/warn (str "ALLOW_INTERNAL_AUTHENTICATION is enabled: allowing internally-authentication admins - should not be enabled in production without SSL.")))

    (and (not (nil? authentication))
         ;; Google-authenticated teachers - note that authorization is here, not within google/ - we make the decision about whether the user
         ;; is a teacher here.
         (let [username (authentication/current request)]
           (some #(= % :teacher) (roles-of-email username))))))

(defmacro do-if-authenticated [what-to-do & [else]]
  `(if (not (nil? (friend/current-authentication)))
     ~what-to-do
     (if ~else ~else
         {:status 302
          :headers {"Location" "/?denied:+not+authenticated"}})))

(defmacro do-if [auth-fn do-if-authorized & [do-if-not-authorized]]
  `(if ~auth-fn
     ~do-if-authorized
     (if ~do-if-not-authorized
       ~do-if-not-authorized
       {:status 302
        :headers {"Location" "/?denied:+not+authenticated"}})))

(defn do-if-admin [what-to-do & [else]]
  (do-if (has-admin-role)
         (do (log/debug (str "Authorized attempt to access an admin-only function."))
             what-to-do)
         (if else else
             (do (log/warn (str "Unauthorized attempt to access an admin-only function."))
                 {:status 302
                  :headers {"Location" "/?message=Unauthorized: you+are+not+an+admin"}}))))

(defn do-if-teacher [what-to-do & [else]]
  (do-if (or (has-admin-role)
             (has-teacher-role))
         (do (log/debug (str "Authorized attempt to access a teacher-only function."))
             what-to-do)
         (if else else
             (do (log/warn (str "Unauthorized attempt to access a teacher-only function."))
                 {:status 302
                  :headers {"Location" "/?message=Unauthorized: you+are+not+a+teacher"}}))))



