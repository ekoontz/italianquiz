(ns italianverbs.generate
  (:use [clojure.stacktrace]
        [italianverbs.morphology :only (fo)]
        [clojure.core :exclude (get-in)])
  (:require
   [clojure.tools.logging :as log]
   [italianverbs.lev :as lev]
   [italianverbs.unify :as unify]
   [italianverbs.config :as config]
   [italianverbs.html :as html]
   [italianverbs.search :as search]
   [clojure.string :as string]))

(defn unifyc [ & args]
  "like fs/unify, but fs/copy each argument before unifying."
  (apply unify/unifyc args))

(defn printfs [fs & filename]
  "print a feature structure to a file. filename will be something easy to derive from the fs."
  (let [filename (if filename (first filename) "foo.html")]  ;; TODO: some conventional default if deriving from fs is too hard.
    (spit filename (html/static-page (html/tablize fs) filename))))

(defn plain [expr]
  {:plain expr})

(defn moreover-head [parent child lexfn-sem-impl]
  (do
    (log/debug (str "moreover-head (candidate) parent: " (fo parent)))
    (log/debug (str "moreover-head (candidate) parent sem: " (unify/get-in parent '(:synsem :sem) :no-semantics)))
    (log/debug (str "moreover-head (candidate) head child sem:" (unify/get-in child '(:synsem :sem))))
    (log/debug (str "moreover-head (candidate) head:" (fo child)))
    (let [result (unifyc parent
                              (unifyc {:head child}
                                           {:head {:synsem {:sem (lexfn-sem-impl (unify/get-in child '(:synsem :sem)))}}}))]
      (if (not (unify/fail? result))
        (let [debug (log/debug (str "moreover-head " (unify/get-in parent '(:comment)) " (SUCCESS) result sem: " (unify/get-in result '(:synsem :sem))))
              debug (log/debug (str "moreover-head (SUCCESS) parent (2x) sem: " (unify/get-in parent '(:synsem :sem))))]
          (merge {:head-filled true}
                 result))
        (let [debug (log/debug (str "moreover-head " (fo child) "/" (unify/get-in parent '(:comment)) "," (fo child) "/" (unify/get-in child '(:comment))))
              fail-path (unify/fail-path result)
              debug (log/debug (str " fail-path: " fail-path))
              debug (log/debug (str " path to head-value-at-fail:" (rest fail-path)))
              debug (log/debug (str " head: " child))
              debug (log/debug (str " head-value-at-fail: " (unify/get-in child (rest fail-path))))
              debug (log/debug (str " parent-value-at-fail: " (unify/get-in parent fail-path)))]
          (do
            (log/debug (str "FAIL: " fail-path))
            :fail))))))

(defn moreover-comp [parent child lexfn-sem-impl]
  (log/debug (str "moreover-comp parent: " (fo parent)))
  (log/debug (str "moreover-comp comp:" (fo child)))
  (let [result
        (unifyc parent
                     (unifyc {:comp child}
                                  {:comp {:synsem {:sem (lexfn-sem-impl (unify/get-in child '(:synsem :sem)))}}}))]
    (if (not (unify/fail? result))
      (let [debug (log/debug (str "moreover-comp " (unify/get-in parent '(:comment)) " (SUCCESS) result sem: " (unify/get-in result '(:synsem :sem))))
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

(defn over3 [parent child lexfn-sem-impl lex-it1]
  (log/info (str "string? child: " (string? child)))
  (log/info (str "seq? child: " (string? child)))
  (cond
   (string? child) (map (fn [each-child]
                          (let [debug (log/info (str "each-child:" each-child))]
                            (over3 parent each-child lexfn-sem-impl lex-it1)))
                        (lex-it1 child))

   (set? child) (map (fn [each-child]
                       (over3 parent each-child lexfn-sem-impl lex-it1))
                     (seq child))

   (seq? child) (map (fn [each-child]
                       (over3 parent each-child lexfn-sem-impl lex-it1))
                     child)

   (= (unify/get-in parent '(:head-filled)) true) ;; won't work in general: only works if complement is first (e.g. cc10)
   (moreover-comp parent child lexfn-sem-impl)

   :else
   (moreover-head parent child lexfn-sem-impl)))

(defn gen14-inner [phrase-with-head complements complement-filter-fn post-unify-fn recursion-level
                   lexfn-sem-impl [ & filtered-complements]]
  (let [debug (log/debug (str "gen14-inner begin: recursion level: " recursion-level))
        debug (log/debug (str "gen14-inner phrase-with-head: " (fo phrase-with-head)))
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
              (moreover-comp phrase-with-head comp lexfn-sem-impl)]
          (if (not (unify/fail? result))
            (do
              (log/debug (str "gen14-inner: unifies: recursion level: " recursion-level))
              (log/debug (str "gen14-inner: unifies head: " (fo phrase-with-head)))
              (log/debug (str "gen14-inner: unifies comp: " (fo comp)))
              ;; test: in italian, is complement first?
              (if (= \c (nth (unify/get-in phrase-with-head '(:comment)) 0))
                ;; yes, italian strings complement is first.
                (log/debug (str "gen14-inner:"
                               (unify/get-in phrase-with-head '(:comment)) " => "
                               (fo comp)
                               " + "
                               (fo (unify/get-in phrase-with-head '(:head))) " => TRUE"))
                ;; italian head first.
                (log/debug (str "gen14-inner:"
                               (unify/get-in phrase-with-head '(:comment)) " => "
                               (fo (unify/get-in phrase-with-head '(:head)))
                               " + "
                               (fo comp) " => TRUE")))
              (let [with-impl (if post-unify-fn (post-unify-fn result) result)]
                  (if (unify/fail? with-impl)

                    ;; adding comp to this phrase failed: continue with the rest of the complements.
                    (gen14-inner phrase-with-head rest-complements complement-filter-fn post-unify-fn
                                 recursion-level lexfn-sem-impl nil)


                    (lazy-seq
                     (cons
                      with-impl
                      (gen14-inner phrase-with-head rest-complements complement-filter-fn post-unify-fn
                                   recursion-level lexfn-sem-impl rest-complements))))))
            (do
              (log/debug (str "gen14-inner: fail: " result))
              (if (= \c (nth (unify/get-in phrase-with-head '(:comment)) 0))
                ;; comp first ('c' is first character of comment):
                (log/debug (str "gen14-inner :"
                                (unify/get-in phrase-with-head '(:comment)) " => "
                                (fo comp)
                                " + "
                                (fo (unify/get-in phrase-with-head '(:head))) " => FAIL"))
                ;; head first ('c' is not first character of comment):
                (log/debug (str "gen14-inner :"
                               (unify/get-in phrase-with-head '(:comment)) " => "
                               (fo (unify/get-in phrase-with-head '(:head)))
                               " + "
                               (fo comp) " => FAIL.")))

              (lazy-seq (gen14-inner phrase-with-head rest-complements complement-filter-fn post-unify-fn recursion-level 
                                      lexfn-sem-impl rest-complements)))))))))

(defn gen14 [phrase heads complements filter-against post-unify-fn recursion-level lexfn-sem-impl]
  (log/info (str "gen14: phrase: " (:comment phrase) "; heads type: " (type heads)))
  (log/debug (str "gen14: filter-against: " filter-against))
  (log/debug (str "gen14: fn? heads:" (fn? heads)))
  (log/debug (str "gen14: not empty? heads: " (and (not (fn? heads)) (not (empty? heads)))))
  (if (or (fn? heads) (not (empty? heads)))
    (do
      (log/debug (str "gen14: starting now: recursion-level: " recursion-level))
      (log/debug (str "gen14: type of heads: " (type heads)))
      (log/debug (str "gen14: filter-against: " filter-against))
      (log/debug (str "gen14: phrase: " (unify/get-in phrase '(:comment))))
      (log/debug (str "gen14: fo(first phrase): " (fo phrase)))
      (log/debug (str "gen14: type of comps: " (type complements)))
      (log/debug (str "gen14: emptyness of comps: " (and (not (fn? complements)) (empty? complements))))
      (let [recursion-level (+ 1 recursion-level)
            phrase (unifyc phrase
                                filter-against
                                {:synsem {:sem (lexfn-sem-impl
                                                (unifyc
                                                 (unify/get-in phrase '(:synsem :sem) :top)
                                                 (unify/get-in filter-against '(:synsem :sem) :top)))}})
            debug (log/debug "gen14: TYPE OF HEADS (before eval): " (type heads))
            heads (cond (fn? heads)
                        (let [filter-against
                              (unify/get-in phrase
                                            '(:head) :top)]
                          (log/debug (str "gen14: PHRASE IS:" phrase))
                          (log/debug (str "gen14: treating heads as a function and applying against filter:"  filter-against))
                          (apply heads (list filter-against)))
                        :else
                        heads)
            debug (log/debug "gen14: TYPE OF HEADS (after eval): " (type heads))
            head (first heads)]
        (let [check (if (nil? head) (log/warn "gen14: head candidate is null - heads was a function, which, when called, returned an empty set of candidates."))
              logging (log/debug (str "gen14: head candidate: " (fo head)))
              logging (log/debug (str "gen14: phrase: " (unify/get-in phrase '(:comment))))
              phrase-with-head (if (not (nil? head))
                                 (moreover-head phrase head lexfn-sem-impl)
                                 :fail)
              debug (log/debug (str "gen14: phrase-with-head: " (fo phrase-with-head)))
              is-fail? (unify/fail? phrase-with-head)
              debug (log/debug (str "gen14: head is-nil? " (nil? head)))
              debug (log/debug (str "gen14: phrase-with-head is-fail? " is-fail?))
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
                                post-unify-fn 0 lexfn-sem-impl nil))
                 (gen14 phrase
                        (rest heads)
                        complements
                        filter-against
                        post-unify-fn
                        recursion-level
                        lexfn-sem-impl)))
              (do
                (log/debug (str "gen14: head unification failed: trying rest of heads."))
                (gen14 phrase
                       (rest heads)
                       complements
                       filter-against
                       post-unify-fn
                       recursion-level
                       lexfn-sem-impl)))))))
    (do
      (log/debug (str "gen14: done.")))))

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

(defn myfoo [alternatives label filter-against lexfn-sem-impl]
  (log/info (str "myfoo: alternatives: " alternatives))
  (log/info (str "myfoo: label: " label))
  (log/info (str "myfoo: filter-against: " filter-against))
  (log/info (str "myfoo: lexfn-sem-impl: " lexfn-sem-impl))

  (if (and (not (empty? alternatives))
           (first alternatives))
    (let [debug (log/info (str "myfoo: alternatives is not empty."))
          candidate (first alternatives)
          label (if label label (if (map? label) (:label candidate)))
          debug (log/info (str "myfoo: candidate: " candidate " : " label))
          debug (log/info (str "symbol? candidate:" (symbol? candidate)))
          debug (log/info (str "map? candidate:" (map? candidate)))
          debug (log/info (str "not nil schema:" (not (nil? (:schema candidate)))))
          ]
      

      (cond (and (symbol? candidate)
                 (seq? (eval candidate)))
            (do
              (log/info (str "LAZY-SHUFFLE: " (lazy-shuffle (eval candidate))))
              (log/info (str "filter-against: " filter-against))
              (log/info (str "lexfn-sem-impl: " lexfn-sem-impl))
              (log/info (str "calling stuff.."))
              (myfoo (lazy-shuffle (eval candidate))
                     (str label " -> " candidate)
                     filter-against lexfn-sem-impl)
              (log/info (str "done calling stuff."))))

      (let [debug
            (log/info "HELLO.")
            result1
            (lazy-cat
             '(a b))

            result12
            (log/info "COND 12.")

            result13
            (try
              (log/info "COND 13.")
              (catch Exception e))


            result14
            (try
              (cond true
                    (log/info "COND14."))
              (catch Exception e
                (log/info (str "CAUGHT EXCEPTION: " e))))


            result15
            (try
              (lazy-cat
               (cond true
                     (log/info "COND1."))
               )
              (catch Exception e
                (log/info (str "CAUGHT EXCEPTION: " e))))

            result2
            (lazy-cat
             (cond (and false (symbol? candidate)
                        (seq? (eval candidate)))

                   (do
                     (log/info "gen-all: making recursive call with eval of candidate.")
                     (log/debug (str "gen-all: candidate: " candidate " evals to a seq."))
                     (myfoo
                      (lazy-shuffle
                       (eval candidate))
                      (str label " -> " candidate)
                      filter-against lexfn-sem-impl))
                   
                   true
                   (do (log/info "welp.")
                       nil)

                   (and (map? candidate)
                        (not (nil? (:schema candidate))))
                   (do (log/info "WTF.")
                       nil)))

            result3
            (log/info "post-lazycat")
;             (cond (and (map? candidate)
;                        (not (nil? (:schema candidate))))
;                   (log/info "WTF0")))
            result
            (lazy-cat
             (cond (and (symbol? candidate)
                        (seq? (eval candidate)))

                   (do
                     (log/info "GOT HERE..")
                     (log/debug (str "gen-all: candidate: " candidate " evals to a seq."))
                     (myfoo
                      (lazy-shuffle
                       (eval candidate))
                      (str label " -> " candidate)
                      filter-against lexfn-sem-impl))

                   (and (map? candidate)
                        (not (nil? (:schema candidate))))
                   (log/info "WTF.")
;                   (let [schema (:schema candidate)
;                         head (:head candidate)
;                         comp (:comp candidate)]
;                     (log/info (str "gen-all: candidate is a schema: " candidate))
;                     (log/info (str "gen-all: filter-against: " filter-against)))




                   true
                   nil))]
        (log/info (str "RESULT:" result))))
    (log/info (str "alternatives are empty."))))



(defn gen-all [alternatives label filter-against lexfn-sem-impl]
  (if (and (not (empty? alternatives))
           (first alternatives))
    (let [candidate (first alternatives)
          label (if label label (if (map? label) (:label candidate)))]
      (log/info (str "GOT HERE1: " (type candidate)))
      (log/info (str "GOT HERE2: " (symbol? candidate)))
      (if (symbol? candidate)
        (log/info (str "SYMBOL: " candidate)))
      (if (symbol? candidate)
        (log/info (str "EVAL: " (eval candidate))))
      (if (symbol? candidate)
        (log/info (str "EVAL2: " (eval candidate))))
      (cond (and (symbol? candidate)
                 (seq? (eval candidate)))
            (do
              (log/info (str "LAZY-SHUFFLE: " (lazy-shuffle (eval candidate))))
              (log/info (str "filter-against: " filter-against))
              (log/info (str "lexfn-sem-impl: " lexfn-sem-impl))
              (log/info (str "calling stuff.."))
              (myfoo (lazy-shuffle (eval candidate))
                     (str label " -> " candidate)
                     filter-against lexfn-sem-impl)
              (log/info (str "done calling stuff.")))
            true (log/info true "YY"))
      (lazy-cat
       (cond (and (symbol? candidate)
                  (seq? (eval candidate)))
             (do
               (log/info "GOT HERE..")
               (log/debug (str "gen-all: candidate: " candidate " evals to a seq."))
               (gen-all
                (lazy-shuffle
                 (eval candidate))
                (str label " -> " candidate)
                filter-against lexfn-sem-impl))

             (and (map? candidate)
                  (not (nil? (:schema candidate))))
             (let [schema (:schema candidate)
                   head (:head candidate)
                   comp (:comp candidate)]
               (log/debug (str "gen-all: candidate is a schema: " candidate))
               (log/debug (str "gen-all: filter-against: " filter-against))
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
               (gen14 (unify/copy (eval schema))
                      ;; head (1) (see below for complements)
                      (fn [inner-filter-against]
                        (log/debug (str "INNER-FILTER-AGAINST: " inner-filter-against))
                        (let [intermediate (unifyc filter-against
                                                        {:head inner-filter-against})
                              shuffled-heads (lazy-shuffle (eval head))]
                          (log/debug (str "INTERMEDIATE: " intermediate))
                          (log/debug (str "FIRST HEAD: " (first shuffled-heads)))
                          (gen-all shuffled-heads
                                   (if false ;; show or don't show schema (e.g. cc10)
                                     (str label ":" schema " -> {H:" head "}")
                                     (str label " -> {H:" head "}"))
                                   (unify/get-in intermediate
                                                 '(:head) :top)
                                   lexfn-sem-impl)))

                      ;; complement: filter-by will filter candidate complements according to each head
                      ;; generated in immediately above, in (1).
                      (fn [parent-with-head]
                        (gen-all (if (symbol? comp)
                                   (lazy-shuffle (eval comp)) (lazy-shuffle comp))
                                 (if false ;; show or don't show schema (e.g. cc10)
                                   (str label " : " schema " -> {C:" comp "}")
                                   (str label " -> {C: " comp "}"))
                                 (unify/get-in
                                  (unifyc filter-against
                                               parent-with-head) '(:comp) :top)
                                 lexfn-sem-impl))
                      filter-against

                      (:post-unify-fn candidate)

                      0

                      lexfn-sem-impl
))

             (and (map? candidate)
                  (not (nil? filter-against)))
             (let [result (unifyc filter-against candidate)]
               (if (not (unify/fail? result))
                 (do (log/info (str "gen-all: " (log-candidate-form candidate label) " -> " (fo candidate) ": ok."))
                     (list result))
                 (do (log/debug (str "gen-all: " (log-candidate-form candidate label) " -> " (fo candidate) ": failed."))
                     nil)))

             true (throw (Exception. (str "don't know what to do with this; type=" (type candidate)))))
       (if (not (empty? (rest alternatives)))
         (gen-all (rest alternatives) label filter-against lexfn-sem-impl))))))

(defn generate [alternatives lexfn-sem-impl]
  (gen-all alternatives "" :top lexfn-sem-impl))

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
