(ns italianverbs.test.borges.writer
  (:refer-clojure :exclude [get get-in resolve merge])
  (:require
   [clojure.core :as core]
   [italianverbs.borges.writer]
   [italianverbs.engine :refer :all]
   [italianverbs.italiano :as it]
   [italianverbs.unify :refer (get get-in)]))

;; (populate 1 {:synsem {:infl :futuro :sem {:pred :chiedere :subj {:pred :lei}}}})
;; (populate 1 {:synsem {:infl :present :sem {:pred :chiedere :subj {:pred :lei}}}})
;;(do (truncate)  (populate 1 {:synsem {:sem {:pred :chiedere :subj {:pred :lei}}}}))

(def spec {:synsem {:essere true}})

(def enrich-function (:enrich @it/small))

(def try (enrich-function {:synsem {:essere true}}))

(def matching-head-lexemes (it/matching-head-lexemes spec))

(def first-val
  "get the first entry of the first word in the dictionary."
  (let [the-key (first (sort (keys @it/lexicon)))]
    (first (get @it/lexicon the-key))))