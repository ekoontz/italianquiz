(ns italianverbs.search
  (:use [hiccup core page-helpers]
        [clojure.set]
        [rdutest])
  (:require
   [clojure.contrib.string :as string]
   [clojure.contrib.repl-utils :as repl-utils]
   [italianverbs.fs :as fs]
   [italianverbs.html :as html]
   [italianverbs.lexiconfn :as lexfn]
   [italianverbs.lev :as lev]
   [italianverbs.grammar :as gram]
   [clojure.contrib.duck-streams :as duck]))

;;(duck/spit "verbs.html"
;;      (html/static-page
;;            (str (html/fs lexfn/verbs) (string/join " " (map (fn [fs] (html/fs fs)) (lexfn/query (lexfn/pathify lexfn/verbs))))
;;
;;(duck/spit "trans-verbs.html"
;;      (html/static-page
;;            (str (html/fs lexfn/trans-verbs) (string/join " " (map (fn [fs] (html/fs fs)) (lexfn/query (lexfn/pathify lexfn/trans-verbs))))


;; TODO: remove *exclude-keys*,(pathify-r) and (pathify) in favor of fs's versions.
(def *exclude-keys* (set #{:_id :ref :refmap}))

(defn pathify-r [fs & [prefix]]
"Transform a map into a map of paths/value pairs,
 where paths are lists of keywords, and values are atomic values.
 e.g.:
 {:foo {:bar 42, :baz 99}} =>  { { (:foo :bar) 42}, {(:foo :baz) 99} }
The idea is to map the key :foo to the (recursive) result of pathify on :foo's value."
  (mapcat (fn [kv]
            (let [key (first kv)
                  val (second kv)]
;              (println (str "K:" key))
              (if (not (contains? *exclude-keys* key))
                (if (or (= (type val) clojure.lang.PersistentArrayMap)
                        (= (type val) clojure.lang.PersistentHashMap))
                  (do
;                    (println (str "PAM"))
                    (pathify-r val (concat prefix (list key))))
                  (do
;                    (println (str "not PAM" (type val)))
                    (list {(concat prefix (list key))
                           val}))))))
          fs))

(defn pathify [fs]
  (pathify-r fs))

(defn pv-not-matches [lexical-entry path value]
  (let [path-value (fs/get-path lexical-entry path)]
    (if (not (or (= path-value value)
                 (and (not (= (keyword path-value) nil))
                      (= (keyword path-value) value))))
      (list lexical-entry))))

(defn pv-matches [lexical-entry path value]
  "might need a more complicated equality predicate later."
  (if (= (last path) :not)
    (pv-not-matches lexical-entry (butlast path) value)
    (let [path-value (fs/get-path lexical-entry path)]
      (if (or (= path-value value)
              (= (keyword path-value) value))
        (list lexical-entry)))))

;; http://stackoverflow.com/questions/2352020/debugging-in-clojure/2352280#2352280
(defmacro dbg[x] `(let [x# ~x] (println "dbg:" '~x "=" x#) x#))

;; TODO: use recur:
;; see http://clojure.org/functional_programming#Functional Programming--Recursive Looping
(defn query-r [path-value-pairs]
  "
      map(lexicon,<p1,v1>)  => S1 |
      map(lexicon,<p2,v2>)  => S2 |
       ...                        |=> intersection(s:S) => return value.
       ...                        |
      map(lexicon,<pn,vn>)  => Sn |
  "
  (if (> (.size path-value-pairs) 0)
    (let [path (first (keys (first path-value-pairs)))
          value (get (first path-value-pairs) path)
          result (set (mapcat
                       (fn [entry] (pv-matches entry path value))
                       (lexfn/fetch)))]
      (if (> (.size path-value-pairs) 1)
        (intersection result (query-r (rest path-value-pairs)))
        result))
    #{})) ;; base case : return an empty set.

(defn query [& constraints]
  (query-r (mapcat (fn [constraint]
                     (pathify constraint))
                   constraints)))

;; test data for (run-query)
;; (pathify transitive-verb) returns a list of path-value-pairs
;; which can be passed to run-query (above). run-query
;; does an intersection over the entire lexicon with each
;; path-value-pair as a filter.
(def tv {:cat "verb" :obj {:cat "noun"}})

(defn myfn [fs] (= (fs/get-path fs '(:obj :cat)) "noun"))

;; How to map over (fetch :lexicon) results:
;; 
;; (get all lexical items with path=>value: :obj/:cat => "noun")
;; 1. (defn myfn [fs] (= (get (get fs :obj) :cat) "noun"))
;; 
;; 2. (def results (mapcat (fn [fs] (if (myfn fs) (list fs))) (fetch :lexicon)))
;;
(defn search [& constraints]
  (seq (apply query constraints)))

(def grammatical-terminology-term
  {:transitive {:cat :verb
                :obj {:cat :noun}}
   :legible {:legible true}
   :lit-verbs {:cat :verb
              :obj {:legible true}}
   :place-preps {:cat :prep
                   :obj {:place true}}})


(defn searchq [search-exp attrs]
  "search with query. attrs is converted into filtering attribute-value pairs in the feature structures."
  (if search-exp
    (string/join " "
                 (concat
                  (map (fn [attr]
                         (let [constraints {(keyword attr) search-exp}
                               results (gram/choose-lexeme constraints)]
                           (if (and results
                                    (not (= (get results :cat) :error))) ;; currently 'no results found' is a {:cat :error}.
                             (html/fs (gram/choose-lexeme constraints)))))
                       (string/split (java.util.regex.Pattern/compile " ") (if attrs attrs "italian english")))
                  (mapcat (fn [search-term]
                            (let [grammatical-terminology-term (get grammatical-terminology-term (keyword search-term))]
                              (if grammatical-terminology-term
                                (map (fn [fs] (html/fs fs)) (query (pathify grammatical-terminology-term))))))
                          (string/split (java.util.regex.Pattern/compile " ") search-exp))))
    nil))

(defn search-ui [request]
  (let [search-query (get (get request :query-params) "search")]
    (html
     [:div#search-ui {:class "quiz-elem"}
      [:h2 "cerca"]
      [:div#searchbar
       [:input {:size "50" :id "search" :type "text"  :value search-query }]
       [:button {:onclick "search()"} "cerca"]]
      [:div#searchresults
       (if search-query
         (searchq search-query nil))]])))

;; example usage: (take 5 (lazy-query {:cat :verb}))
(defn lazy-query [search]
  (lazy-seq (query search)))

(defn non-empty-set [set]
  (> (.size set) 0))

(defn empty-set [set]
  (= (.size set) 0))


;; TODO: move some of these lexically-related tests to lexicon.clj (e.g. the 'fare' (to do) test).
(def tests
  (list
   (rdutest
    "Sanity check: test rdutest itself by assuming that '+' is correct."
    (+ 1 2) 
    #(= % 3)
    :sanity-check)
   
   (rdutest
    "At least one verb is in the lexicon."
    (take 1 (lazy-query {:cat :verb}))
    #(> (.size %) 0)
    :one-verb)
   
   (rdutest
    "At least one noun is in the lexicon."
    (take 1 (lazy-query {:cat :noun}))
    #(> (.size %) 0)
    :one-noun)
   
   (rdutest
    "At least one nominative noun is in the lexicon (and that the conjunction of more than one predicate works."
    (take 1 (lazy-query {:cat :noun :case :nom}))
    #(> (.size %) 0)
    :one-nom-noun)

   (rdutest
    "Features that should be ignored for lookup purposes are in fact ignored."
    (take 1 (lazy-query {:cat :noun :case :nom :ref '("foo" "bar") :refmap {:baz 42}}))
    #(> (.size %) 0)
    :ignore-non-lookup-features)

   
   (rdutest
    "The intersection of mutually-exclusive queries is the null set (since a noun can't be both nominative and non-nominative)."
    (intersection (query {:cat :noun :case :nom}) (query {:cat :noun :case {:not :nom}}))
    #(= (.size %) 0)
    :null-set)
   
   (rdutest
    "There's at least one verb that takes an edible object (a nested query works)."
    (take 1 (lazy-query {:cat :verb :obj {:edible true}}))
    #(> (.size %) 0)
    :verb-with-edible-object)
   
   (rdutest
    "Looking up an irregular verb inflection by its root works."
    (search {:root {:italian "fare"}})
    #(and (not (= % nil)) (> (.size %) 0))
    :lookup-roots)
   
   (rdutest
    "Look up a word by its root: find a verb whose root is 'fare (to make)' (e.g. 'facio (i make)')."
    (search {:root (first (search {:italian "fare" :cat :verb :infl :infinitive}))})
    #(and (not (= % nil)) (> (.size %) 0))
    :lookup-complex-root)
   
   (rdutest
    "Search should take multiple parameters and merge them together."
    (search {:cat :noun} {:gender :fem} {:number :singular})
    #(and (not (= % nil)) (> (.size %) 0))
    :search-multiple-maps)
   
   ;; "pathifying" a map means (by example)
   ;;
   ;; {:root {:gender :masc
   ;;         :human true
   ;;         :det {:cat :det
   ;;               :def true}
   ;;         :animate true
   ;;         :morph "morph-noun"
   ;;         :common true
   ;;         :cat :noun
   ;;         :italian "uomo"}
   ;;  :italian "uomini"
   ;;  :number :plural}
   ;;
   ;; => ({(:root :gender) :masc}
   ;;     {(:root :human) true}
   ;;     {(:root :det :cat) :det}
   ;;     {(:root :det :def) true}
   ;;     {(:root :animate) true}
   ;;     {(:root :morph) "morph-noun"}
   ;;     {(:root :common) true}
   ;;     {(:root :cat) :noun}
   ;;     {(:root :italian) "uomo"}
   ;;     {(:italian) "uomini"}
   ;;     {(:number) :plural})

   (rdutest
    "Pathify a large map."
    (pathify
     {:root {:gender :masc
             :human true
             :det {:cat :det
                   :def true}
             :animate true
             :morph "morph-noun"
             :common true
             :cat :noun
             :italian "uomo"}
      :italian "uomini"
      :number :plural})
    (fn [paths] (= (count paths) 11)) ;; TODO: write better test.
    :pathify-long-map-1)
   
   (rdutest
    "Pathify another map."
    (pathify {:root {:gender :masc :human true :det {:cat :det} :animate true :morph "morph-noun" :common true :cat :noun :italian "uomo" :person :3rd}})
    (fn [paths] (= (count paths) 9)) ;; TODO: write better test.
    :pathify-long-map-2)
   ))

;; FIXME: move to test.clj.
(def evaluate-testresults
  (map (fn [result] {:comment (:comment result) :result (:assert-result result)})  tests))


