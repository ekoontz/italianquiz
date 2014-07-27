(ns italianverbs.generate
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:require
   [clojure.set :refer (union)]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]

   [italianverbs.cache :refer (build-lex-sch-cache) ]
   [italianverbs.forest :as forest]
   [italianverbs.grammar :refer :all]
   [italianverbs.html :as html]
   [italianverbs.lexicon :refer (lexicon it en)]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.over :refer :all]
   [italianverbs.ug :refer :all]
   [italianverbs.unify :refer (fail? get-in lazy-shuffle merge remove-top-values unify unifyc)]))

(defn printfs [fs & filename]
  "print a feature structure to a file. filename will be something easy to derive from the fs."
  (let [filename (if filename (first filename) "foo.html")]  ;; TODO: some conventional default if deriving from fs is too hard.
    (spit filename (html/static-page (html/tablize fs) filename))))

(defn plain [expr]
  "simply map expr in a map with one key :plain, whose value is expr.
   workbook/workbookq will format this accordingly."
  {:plain expr})

;; this rule-cache is defined outside any function so that all functions can share
;; a single cache.
(def rule-cache (conj (build-lex-sch-cache grammar lexicon grammar)
                      {:phrase-constraints head-principle})) ;; for now, only one constraint: ug/head-principle.

(defn generate [ & [head the-lexicon the-grammar cache]]
  (let [head (if head head :top)
        grammar (if the-grammar the-grammar grammar)
        lexicon (if the-lexicon the-lexicon lexicon)
        cache (if cache cache rule-cache)] ;; if no cache supplied, use package-level cache 'rule-cache'.
  (log/debug (str "generate with lexicon size: " 
                  (.size the-lexicon) " and grammar size: "
                  (.size the-grammar) "."))
  (first (take 1 (forest/gen2 grammar lexicon head cache)))))

(defn nounphrase [ & [ spec the-lexicon the-grammar cache ]]
  (let [spec (if spec spec :top)
        lexicon (if the-lexicon the-lexicon lexicon)
        grammar (if the-grammar the-grammar grammar)
        cache (if cache cache rule-cache)]
    (first (take 1 (forest/hlcl cache grammar lexicon (unify spec {:synsem {:cat :noun :subcat '()}}))))))

(defn sentence [ & [spec the-lexicon the-grammar cache]]
  (let [sentence-spec {:synsem {:subcat '()
                                :cat :verb
                                :subj {:animate true}}}
                       
        spec (if spec spec :top)
        lexicon (if the-lexicon the-lexicon lexicon)
        grammar (if the-grammar the-grammar grammar)
        cache (if cache cache rule-cache)]
    (generate (unifyc sentence-spec spec))))

;; This sentence generation prevents initialization errors that occur when trying to
;; generate sentences within the sandbox.
;; TODO: move to a sandbox-initialization-specific area.
(def get-stuff-initialized (sentence {:comp {:phrasal false}
                                      :head {:phrasal false}
                                      :synsem {:subcat '() :cat :verb
                                               :sem {:pred :parlare
                                                     :subj {:pred :lei}
                                                     :obj {:pred :parola}}}}
                                     lexicon grammar))
                                                                             
(log/info (str "done loading generate: " (fo get-stuff-initialized)))
