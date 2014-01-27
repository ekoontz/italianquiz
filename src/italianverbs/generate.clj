(ns italianverbs.generate
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:require
   [clojure.set :refer (union)]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]

   [italianverbs.forest :exclude (lightning-bolt) ]
   [italianverbs.forest :as forest]
   [italianverbs.grammar :refer (grammar)]
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
(def rule-cache (forest/build-lex-sch-cache grammar lexicon))

(defn generate [ & [head]]
  (let [head (if head head :top)]
    (first (take 1 (forest/lightning-bolt head lexicon (shuffle grammar) 0 rule-cache)))))

(defn sentence [ & [ with ]]
  (generate {:synsem {:cat :verb :subcat '()}}))

(defn nounphrase [ & [ with ]]
  (generate (first (take 1 (generate {:synsem {:cat :noun :subcat '()}})))))
