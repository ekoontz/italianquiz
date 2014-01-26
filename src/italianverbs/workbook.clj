(ns italianverbs.workbook
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:require
   [clojure.core :exclude [get-in]]
   [clojure.core :as core] ;; This allows us to use core's get-in by doing "(core/get-in ..)"

   [clojure.set :refer :all]
   [clojure.string :as string]

   [clojail.core :refer [sandbox]]
   [clojail.testers :refer :all]
   [clojure.tools.logging :as log]
   [hiccup.core :refer :all]

   [italianverbs.generate :refer :all]
   [italianverbs.grammar :refer :all]
   [italianverbs.html :as html]
   [italianverbs.lexicon :refer :all]
   [italianverbs.lexiconfn :refer :all]
   [italianverbs.morphology :refer [finalize fo fo-ps]]
   [italianverbs.over :refer :all]
   [italianverbs.rules :refer :all]
   [italianverbs.ug :refer :all]
   [italianverbs.unify :refer :all :exclude [unify]]

   [somnium.congomongo :as mongo]))

;; seem to need this sometimes, to avoid initialization errors:
;(def populate-ns (sentence))

;; Sandbox specification derived from:
;;    https://github.com/flatland/clojail/blob/4d3f58f69c2d22f0df9f0b843c7dea0c6a0a5cd1/src/clojail/testers.clj#L76
;;    http://docs.oracle.com/javase/6/docs/api/overview-summary.html
;;    http://richhickey.github.com/clojure/api-index.html
(def workbook-sandbox
  (sandbox
   (conj
    clojail.testers/secure-tester-without-def
    (blacklist-nses '[
                      clojure.main
                      java
                      javax
                      org.omg
                      org.w3c
                      org.xml
                      ])
    (blacklist-objects [
                        clojure.lang.Compiler
                        clojure.lang.Ref
                        clojure.lang.Reflector
                        clojure.lang.Namespace
                        clojure.lang.Var clojure.lang.RT
                        ]))
   :refer-clojure false
   ;; using 60000 for development: for production, use much smaller value.
   :timeout 60000
;   :timeout 15000
   :namespace 'italianverbs.workbook))
(defn workbookq [expr notused]
  (do
    ;; TODO: add timing information for each evaluation.
    (log/info (str "workbookq: evaluating expression: \"" expr "\""))
    (if expr
      (let [output
            (string/join " "
                         (let [loaded
                               (try
                                 (workbook-sandbox (read-string expr))
                                 ;; TODO: how can I show the stack trace for the
                                 ;; attempt to process the expression?
                                 (catch Exception e
                                   (log/error (str "failed to sandbox-load-string: " expr ":" e))
                                   (str e)))]
                           (list
                            (str
                             "<div class='evalinput'>"
                             expr
                             "</div>"
                             "<div class='evalresult'>"
                             (cond

                              (set? loaded)
                              (html/tablize loaded)

                              (and (or (set? loaded) (seq? loaded))
                                   (> (.size loaded) 1))
                              (str "<ol class='workbook'>"
                                   (string/join " "
                                                (map (fn [elem]
                                                       (str "<li>" (html/tablize elem) "</li>"))
                                                     (seq loaded)))
                                   "</ol>")

                              (= (type loaded)
                                 clojure.lang.LazySeq)
                              (string/join " "
                                           (map (fn [elem]
                                                  (html/tablize elem))
                                                (seq loaded)))

                              (or (list? loaded) (set? loaded)
                                  (= (type loaded) clojure.lang.Cons))
                              (string/join " "
                                           (map (fn [elem]
                                                  (html/tablize elem))
                                                loaded))

                              (= (type loaded) clojure.lang.Var)
                              (str (eval loaded))

                              (and (map? loaded)
                                   (= (keys loaded) '(:plain)))
                              (str "<div style='font-family:monospace'>" (strip-refs (:plain loaded)) "</div>")

                              (map? loaded)
                              (html/tablize loaded)

                              (= (type loaded) nil)
                              (str "<b>nil</b>")
                              :else
                              ;; nothing formattable: just stringify result of
                              ;; evaluation.
                              (str "<div style='font-family:monospace'>" loaded " (<b>" (type loaded) "</b>)" "</div>"))
                             "</div>"))))]
        (log/info (str "workbookq: done evaluating: \"" expr "\""))
        output))))

(defn workbook-ui [request]
  (let [search-query (get (get request :query-params) "search")]
    (html
     [:div#workbook-ui {:class "quiz-elem"}
      [:h2 "Libro di Lavoro"]
      [:div#searchbar
       [:textarea {:cols 80 :rows 4 :id "workbookq" }
        (if search-query
          search-query
          "(fo (take 1 (lightning-bolt)))")
        ]
       [:button {:onclick "workbook()"} "evaluate"]]
      [:div#workbooka
       (if search-query
         (workbookq search-query))]])))

;; (take 1 (overall "domani" (overall "io" (overall "avere" (overall "potere" "dormire")))))))
(defn overall [child1 & [child2]]
  (over (list cc10 ch21 hc11 hh10 hh21 hh32) child1 child2))

(defn overha [child1 & [child2]]
  (log/debug (str "overha child1: " (fo child1)))
  (log/debug (str "overha child2: " (fo child2)))
  (let [with-head
        (reduce #'concat
                (map (fn [parent]
                       (overh parent child1))
                     (list cc10 ch21 hc11 hh10 hh21 hh32)))]
    (if child2
      (reduce #'concat
              (map (fn [each-with-head]
                     (overc each-with-head child2))
                   with-head))
      with-head)))

