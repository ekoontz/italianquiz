(defproject italianverbs "1.0.0-SNAPSHOT"
  :description "Italian language learning app"
  :dependencies [[clj-time "0.7.0"]
                 [clojail "1.0.6"]

                 [com.cemerick/drawbridge "0.0.6"
                  :exclusions [ring/ring-core]] ;; https://github.com/cemerick/drawbridge/issues/8


                 [compojure "1.1.6"]

                 [environ "0.2.1"]

                 [hiccup "1.0.1"]

                 [javax.servlet/servlet-api "2.5"]


                 [log4j/log4j "1.2.16" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]

                 [org.clojure/clojure "1.5.1"]
                 ;; this is the latest version as of March 19, 2014:
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"] 

                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/tools.logging "0.2.6"]


                 [org.xerial/sqlite-jdbc "3.7.2"]


                 [ring/ring-jetty-adapter "1.1.0"]
                 [ring/ring-devel "1.1.0"]
                 [ring-basic-authentication "1.0.1"]

                 ;; database drivers:
                 [korma "0.3.0-RC5"]
                 [org.postgresql/postgresql "9.2-1002-jdbc4"]
                 [congomongo "0.4.4"]


]

  :java-agents [[com.newrelic.agent.java/newrelic-agent "2.19.0"]]

  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.2.1"]
            [lein-ring "0.7.3"]]

  :hooks [environ.leiningen.hooks]


  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]
                        [org.clojure/tools.nrepl "0.2.0-beta10"]]}
   :production {:env {:production true}}}
  ;; italianverbs.core/app is defined in src/italianverbs/core.clj.
  :ring {:handler italianverbs.core/app})
