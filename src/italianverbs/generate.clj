(ns italianverbs.generate
  (:use [clojure.stacktrace]
        [italianverbs.morphology :only (fo)]
        [clojure.core :exclude (get-in)])
  (:require
   [clojure.tools.logging :as log]
   [italianverbs.lev :as lev]
   ;; TODO: remove this: generate should not need access to lexfn/ at all.
   [italianverbs.lexiconfn :as lexfn]
   ;; TODO: remove this: generate should not need access to morph/ at all.
   [italianverbs.morphology :as morph]
   [italianverbs.unify :as unify]
   [italianverbs.config :as config]
   [italianverbs.html :as html]
   ;; TODO: remove this: generate should not need access to lex/ at all.
   [italianverbs.lexicon :as lex]
   [italianverbs.search :as search]
   [clojure.string :as string]))

(defn printfs [fs & filename]
  "print a feature structure to a file. filename will be something easy to derive from the fs."
  (let [filename (if filename (first filename) "foo.html")]  ;; TODO: some conventional default if deriving from fs is too hard.
    (spit filename (html/static-page (html/tablize fs) filename))))

(defn plain [expr]
  {:plain expr})

(defn moreover-head [parent child]
  (do
    (log/debug (str "moreover-head (candidate) parent: " (fo parent)))
    (log/debug (str "moreover-head (candidate) parent sem: " (unify/get-in parent '(:synsem :sem) :no-semantics)))
    (log/debug (str "moreover-head (candidate) head:" (fo child)))
    (let [result (lexfn/unify parent
                              (lexfn/unify {:head child}
                                           {:head {:synsem {:sem (lexfn/sem-impl (unify/get-in child '(:synsem :sem)))}}}))]
      (if (not (unify/fail? result))
        (let [debug (log/debug (str "moreover-head " (get-in parent '(:comment)) " (SUCCESS) result sem: " (unify/get-in result '(:synsem :sem))))
              debug (log/debug (str "moreover-head (SUCCESS) parent (2x) sem: " (unify/get-in parent '(:synsem :sem))))]
          (merge {:head-filled true}
                 result))
        (let [debug (log/debug (str "moreover-head " (fo child) "/" (get-in parent '(:comment)) "," (fo child) "/" (get-in child '(:comment))))
              fail-path (unify/fail-path result)
              debug (log/debug (str " fail-path: " fail-path))
              debug (log/debug (str " path to head-value-at-fail:" (rest fail-path)))
              debug (log/debug (str " head: " child))
              debug (log/debug (str " head-value-at-fail: " (unify/get-in child (rest fail-path))))
              debug (log/debug (str " parent-value-at-fail: " (unify/get-in parent fail-path)))]
          :fail)))))

(defn moreover-comp [parent child]
  (log/debug (str "moreover-comp parent: " (fo parent)))
  (log/debug (str "moreover-comp comp:" (fo child)))
  (let [result
        (lexfn/unify parent
                     (lexfn/unify {:comp child}
                                  {:comp {:synsem {:sem (lexfn/sem-impl (unify/get-in child '(:synsem :sem)))}}}))]
    (if (not (unify/fail? result))
      (let [debug (log/debug (str "moreover-comp " (get-in parent '(:comment)) " (SUCCESS) result sem: " (unify/get-in result '(:synsem :sem))))
            debug (log/debug (str "moreover-comp (SUCCESS) parent (2x) sem: " (unify/get-in parent '(:synsem :sem))))]
        (let [result
              (merge {:comp-filled true}
                     result)]
          (log/debug (str "moreover-comp (SUCCESS) merged result: " (fo result)))
          result))
      (do
        (log/debug "moreover-comp: fail at: " (unify/fail-path result))
        (if (unify/get-in child '(:head))
          (throw (Exception. (str "failed to add complement: " (fo child) "  to: phrase: " (fo parent)
                                  ". Failed path was: " (unify/fail-path result)
                                  ". Value of parent at path is: "
                                  (unify/get-in parent (unify/fail-path result))
                                  "; Synsem of child is: "
                                  (unify/get-in child '(:synsem) :top)))))
        (log/debug "moreover-comp: complement synsem: " (unify/get-in child '(:synsem)))
        (log/debug "moreover-comp:  parent value: " (unify/get-in parent (unify/fail-path result)))
        :fail))))

(defn over3 [parent child]
  (log/debug (str "string? child: " (string? child)))
  (log/debug (str "seq? child: " (string? child)))
  (cond
   (string? child) (map (fn [each-child]
                          (over3 parent each-child))
                        (lex/it1 child))

   (seq? child) (map (fn [each-child]
                       (over3 parent each-child))
                     child)

   (= (unify/get-in parent '(:head-filled)) true) ;; won't work in general: only works if complement is first (e.g. cc10)
   (moreover-comp parent child)

   :else
   (moreover-head parent child)))

(defn gen14-inner [phrase-with-head complements complement-filter-fn post-unify-fn recursion-level [ & filtered-complements]]
  (let [debug (log/debug (str "gen14-inner begin: recursion level: " recursion-level))
        debug (log/debug (str "gen14-inner phrase-with-head: " (fo phrase-with-head)))
        debug (log/debug (str "gen14-inner complements: " (fo complements)))
        recursion-level (+ 1 recursion-level)
        debug-inner (log/debug (str "gen14-inner: type of complements: " (type complements)))
        debug-inner (log/debug (str "gen14-inner: complement-filter-fn: " complement-filter-fn))
        check-filter-fn (if (nil? complement-filter-fn)
                          (let [error-message (str "complement-filter-fn is nil.")]
                            (log/debug error-message)
                            (throw (Exception. error-message))))
        debug-inner (log/debug (str "gen14-inner: fn? complements: " (fn? complements)))
        debug-inner (log/debug (str "gen14-inner: seq? complements: " (seq? complements)))
        debug-inner (log/debug (str "gen14-inner: map? complements: " (map? complements)))
        debug-inner (if (= (type complements)
                           clojure.lang.PersistentVector)
                      (log/debug (str "gen14-inner: vector? complements: "
                                      (= (type complements) clojure.lang.PersistentVector)
                                      " with size: " (.size complements))))
        maps-as-complements-not-allowed
        (if (map? complements)
          ;; TODO: support map by simply re-calling with a list with one element: the map.
          (let [error-message (str "complements should be either a sequence or a function: maps not supported at this time.")]
            (log/debug error-message)
            (throw (Exception. error-message))))
        complements (if filtered-complements filtered-complements
                        (cond (fn? complements)
                              (do (log/debug (str "gen14-inner: treating complements as a fn."))
                                  ;; probably don't need lazy-seq here, so simply leaving this here, commented out, in case I'm wrong:
                                  ;; (lazy-seq (apply complements (list (apply complement-filter-fn (list phrase-with-head)))))
                                  (apply complements (list phrase-with-head)))

                              (seq? complements)
                              (filter (fn [complement]
                                        (log/debug (str "FILTERING COMPLEMENT: " (fo complement)))
                                        (apply
                                         (fn [x] (apply complement-filter-fn (list phrase-with-head)))
                                         (list complement)))
                                      filtered-complements)
                              :else
                              (let [error-message (str "neither fn? nor seq?: " (type complements))]
                                (do (log/error error-message)
                                    (throw (Exception. error-message))))))
        empty-complements
        (empty? complements)
        complement
        (cond (fn? complements)
              (first (apply complements nil))
              (seq? complements)
              (first complements)
              (= (type complements) clojure.lang.PersistentVector)
              (first complements))
        debug-inner (log/debug "gen14-inner: before doing (rest complements)..")
        rest-complements
        (if (fn? complements)
          (lazy-seq (rest (apply complements nil)))
          (lazy-seq (rest complements)))
        debug-inner (log/debug "gen14-inner: after doing (rest complements)..")]
    (log/debug (str "gen14-inner: comp-emptiness: " empty-complements))
    (log/debug (str "(fo phrase-with-head): " (fo phrase-with-head)))
    (log/debug (str "complement(comment): " (unify/get-in complement '(:comment))))
    (log/debug (str "complement: " (fo complement)))
    (if (not empty-complements)
      (let [comp complement]
        (let [result
              (moreover-comp
               phrase-with-head
               comp)]
          (if (not (unify/fail? result))
            (do
              (log/debug (str "gen14-inner: unifies: recursion level: " recursion-level))
              (log/debug (str "gen14-inner: unifies head: " (fo phrase-with-head)))
              (log/debug (str "gen14-inner: unifies comp: " (fo comp)))
              ;; test: in italian, is complement first?
              (if (= \c (nth (get-in phrase-with-head '(:comment)) 0))
                ;; yes, italian strings complement is first.
                (log/debug (str "gen14-inner:"
                               (get-in phrase-with-head '(:comment)) " => "
                               (fo comp)
                               " + "
                               (fo (unify/get-in phrase-with-head '(:head))) " => TRUE"))
                ;; italian head first.
                (log/debug (str "gen14-inner:"
                               (get-in phrase-with-head '(:comment)) " => "
                               (fo (unify/get-in phrase-with-head '(:head)))
                               " + "
                               (fo comp) " => TRUE")))
              (let [with-impl (if post-unify-fn (post-unify-fn result) result)]
                  (if (unify/fail? with-impl)
                    (gen14-inner phrase-with-head rest-complements complement-filter-fn post-unify-fn recursion-level nil)
                    (lazy-seq
                     (cons
                      with-impl
                      (gen14-inner phrase-with-head rest-complements complement-filter-fn post-unify-fn recursion-level rest-complements))))))
            (do
              (log/debug (str "gen14-inner: fail: " result))
              (if (= \c (nth (get-in phrase-with-head '(:comment)) 0))
                ;; comp first ('c' is first character of comment):
                (log/info (str "gen14-inner :"
                                (get-in phrase-with-head '(:comment)) " => "
                                (fo comp)
                                " + "
                                (fo (unify/get-in phrase-with-head '(:head))) " => FAIL"))
                ;; head first ('c' is not first character of comment):
                (log/info (str "gen14-inner :"
                               (get-in phrase-with-head '(:comment)) " => "
                               (fo (unify/get-in phrase-with-head '(:head)))
                               " + "
                               (fo comp) " => FAIL.")))

              (lazy-seq (gen14-inner phrase-with-head rest-complements complement-filter-fn post-unify-fn recursion-level rest-complements)))))))))

(defn gen14 [phrase heads complements filter-against post-unify-fn recursion-level]
  (if (or (fn? heads) (not (empty? heads)))
    (do
      (if (unify/fail? phrase)
        (throw (Exception. (str "gen14: phrase is fail: " (type phrase)))))
      (log/debug (str "gen14: starting now: recursion-level: " recursion-level))
      (log/debug (str "gen14: type of heads: " (type heads)))
      (log/debug (str "gen14: filter-against: " filter-against))
      (log/debug (str "gen14: phrase: " (unify/get-in phrase '(:comment))))
      (log/debug (str "gen14: fo(first phrase): " (fo phrase)))
      (log/debug (str "gen14: type of comps: " (type complements)))
      (log/debug (str "gen14: emptyness of comps: " (and (not (fn? complements)) (empty? complements))))
      (let [recursion-level (+ 1 recursion-level)
            phrase (lexfn/unify phrase
                                (lexfn/unify
                                 filter-against
                                 {:synsem {:sem (lexfn/sem-impl
                                                 (lexfn/unify
                                                  (get-in phrase '(:synsem :sem) :top)
                                                  (get-in filter-against '(:synsem :sem) :top)))}}))
            heads (cond (fn? heads)
                        (let [filter-against
                              (unify/get-in phrase
                                            '(:head) :top)]
                          (log/debug (str "gen14: PHRASE IS:" phrase))
                          (if (unify/fail? filter-against)
                            (throw (Exception. (str "Reload ug: filter-against contains :fail:"
                                                    filter-against "     at    " (unify/fail-path filter-against)))))
                          (log/debug (str "gen14: treating heads as a function and applying against filter:"  filter-against))
                          (apply heads (list filter-against)))
                        :else
                        heads)
            debug (log/debug "TYPE OF HEADS: " (type heads))
            head (first heads)]
        (let [check (if (nil? head) (log/warn "head candidate is null - heads was a function, which, when called, returned an empty set of candidates."))
              logging (log/debug (str "gen14: head candidate: " (fo head)))
              logging (log/debug (str "gen14: phrase: " (unify/get-in phrase '(:comment))))
              phrase-with-head (moreover-head phrase head)
              debug (log/debug (str "gen14: phrase-with-head: " (fo phrase-with-head)))
              is-fail? (unify/fail? phrase-with-head)
              ]
          (if (nil? head)
            nil
            (if (not is-fail?)
              (do
                (log/debug (str "gen14: head: " (fo (dissoc head :serialized))
                                (if (unify/get-in head '(:comment))
                                  (str "(" (unify/get-in head '(:comment))) ")")
                                " added successfully to " (unify/get-in phrase '(:comment)) "."))
                (log/debug (str "gen14: phrase: " (unify/get-in phrase '(:comment)) " => head: " (fo head)
                                (if (unify/get-in head '(:comment))
                                  (str "(" (unify/get-in head '(:comment)) ")")
                                  "")))
                (lazy-cat
                 (let [complement-filter-function (unify/get-in phrase '(:comp-filter-fn))
                       ;; enhance with (get-in phrase-with-head's value for the possible complements:
                       applied-complement-filter-fn (apply
                                                     complement-filter-function
                                                     (list phrase-with-head))]
                   (gen14-inner phrase-with-head
                                complements
                                (apply applied-complement-filter-fn (list phrase-with-head))
                                post-unify-fn 0 nil))
                 (gen14 phrase
                        (rest heads)
                        complements
                        filter-against
                        post-unify-fn
                        recursion-level)))
              (gen14 phrase
                     (rest heads)
                     complements
                     filter-against
                     post-unify-fn
                     recursion-level))))))))

;; see example usage in grammar.clj.
(defmacro rewrite-as [name value]
  (if (ns-resolve *ns* (symbol (str name)))
    `(def ~name (cons ~value ~name))
    `(def ~name (list ~value))))

;; thanks to Boris V. Schmid for lazy-shuffle:
;; https://groups.google.com/forum/#!topic/clojure/riyVxj1Qbbs
(defn lazy-shuffle [coll]
;  (shuffle coll))
  (let [size (count coll)]
    (if (> size 0)
      (let [rand-pos (rand-int size)
            [prior remainder]
            (split-at rand-pos coll)
            elem (nth coll rand-pos)]
        (lazy-seq
         (cons elem
               (lazy-shuffle (concat prior (rest remainder)))))))))

(defn gen15 [phrase heads comps]
  (do
    (log/debug (str "gen15 start: " (get-in phrase '(:comment)) "," (type heads)))
    (gen14 phrase heads comps nil 0)))

(defn gen17 [phrase heads comps filter-against post-unify-fn]
  (gen14 phrase heads comps filter-against post-unify-fn 0))

(defn log-candidate-form [candidate & [label]]
  (cond (and (map? candidate)
             (:schema candidate)
             (:head candidate)
             (:comp candidate))
        (if (= \c (nth (str (:schema candidate)) 0))
          ;; complement is first (in italian)
          (str (if label (:label candidate) " -> ")
               "C:" (:comp candidate) "(" (fo (:head candidate)) ") "
               "H:" (:head candidate) "(" (fo (:comp candidate)))
          ;; head is first (in italian)
          (str (if label (:label candidate) " -> ")
               "H:" (:head candidate) "(" (fo (:head candidate)) ") "
               "C:" (:comp candidate) "(" (fo (:comp candidate)) ")"))
        (map? candidate)
        (str (if label (str label)))
        true
        (str (if label (str label)))))

(defn gen-all [alternatives label filter-against]
  (if (not (empty? alternatives))
    (let [candidate (first alternatives)
          label (if label label (if (map? label) (:label candidate)))]
      (lazy-cat
       (cond (and (symbol? candidate)
                  (seq? (eval candidate)))
             (do
               (log/info (str "gen-all: candidate: " candidate " evals to a seq."))
               (gen-all
                (lazy-shuffle
                 (eval candidate))
                (str label " -> " candidate)
                filter-against))

             (and (map? candidate)
                  (not (nil? (:schema candidate))))
             (let [schema (:schema candidate)
                   head (:head candidate)
                   comp (:comp candidate)]

               ;; schema is a tree with 3 nodes: a parent and two children: a head child, and a comp child.
               ;; all possible schemas are defined above, after the "BEGIN SCHEMA DEFINITIONS" comment.
               ;; in a particular order (i.e. either head is first or complement is first).
               ;; head is either 1) or 2):
               ;; 1) a rule consisting of a schema, a head rule, and a comp rule.
               ;; 2) a sequence of lexemes.

               ;; comp is either 1) or 2):
               ;; 1) a rule consisting of a schema, a head rule, and a comp rule.
               ;; 2) a sequence of lexemes.

               ;; (eval schema) is a 3-node tree (parent and two children) as described
               ;; above: schema is a symbol (e.g. 'cc10 whose value is the tree, thus
               ;; allowing us to access that value with (eval schema).
               (log/info (str "gen-all: testing for schema: " schema " being fail."))
               (if (unify/fail? (eval schema))
                 (throw (Exception. (str "schema is fail."))))
               ;; (str "schema: " (if (fn? schema) "fn" schema) " is fail: " (unify/fail-path (eval schema))) " : to fix, reload ug.")))
               (gen17 (unify/copy (eval schema))
                      ;; head (1) (see below for complements)
                      (fn [inner-filter-against]
                        (gen-all (lazy-shuffle (eval head))
                                 (if false ;; show or don't show schema (e.g. cc10)
                                   (str label ":" schema " -> {H:" head "}")
                                   (str label " -> {H:" head "}"))
                                 (unify/get-in (lexfn/unify filter-against
                                                            {:head inner-filter-against})
                                               '(:head) :top)))

                      ;; complement: filter-by will filter candidate complements according to each head
                      ;; generated in immediately above, in (1).
                      (fn [parent-with-head]
                        (gen-all (if (symbol? comp)
                                   (lazy-shuffle (eval comp)) (lazy-shuffle comp))
                                 (if false ;; show or don't show schema (e.g. cc10)
                                   (str label " : " schema " -> {C:" comp "}")
                                   (str label " -> {C: " comp "}"))
                                 (unify/get-in
                                  (lexfn/unify filter-against
                                               parent-with-head) '(:comp) :top)))
                      filter-against

                      (:post-unify-fn candidate)))

             (and (map? candidate)
                  (not (nil? filter-against)))
             (let [result (lexfn/unify filter-against candidate)]
               (if (not (unify/fail? result))
                 (do (log/info (str "gen-all: " (log-candidate-form candidate label) " -> " (fo candidate) ": ok."))
                     (list result))
                 (do (log/info (str "gen-all: " (log-candidate-form candidate label) " -> " (fo candidate) ": failed."))
                     nil)))

             true (throw (Exception. (str "don't know what to do with this; type=" (type candidate)))))

       (gen-all (rest alternatives) label filter-against)))))

(defmacro gen-ch21 [head comp]
  `(do ~(log/debug "gen-ch21 macro compile-time.")
       (gen15 ch21
              ~head
              ~comp)))

(defmacro gen-hh21 [head comp]
  `(do ~(log/debug "gen-hh21 macro compile-time.")
       (gen15 hh21
              ~head
              ~comp)))

(defmacro gen-cc10 [head comp]
  `(do ~(log/debug "gen-cc10 macro compile-time.")
       (gen15 cc10
              ~head
              ~comp)))

