(ns italianverbs.test.benchmark
  (:refer-clojure :exclude [get-in resolve merge])
  (:require
   [clojure.core :as core]
   [clojure.core.async :as async :exclude [partition-by]]
   [clojure.math.numeric-tower :as math]

   [clojure.set :refer (union)]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]
   [italianverbs.benchmark :refer :all]
   [italianverbs.cache :refer (build-lex-sch-cache spec-to-phrases get-comp-phrases-of)]
   [italianverbs.forest :as forest]
   [italianverbs.over :refer (overc overh)]
   [italianverbs.ug :refer (head-principle)]
   [dag-unify.core :as unify]
   [dag-unify.core :refer (fail? get-in lazy-shuffle unifyc)]))

;; test is busted.
;(deftest simple-sentence
;  (let [sentences (sentence-subject-verb 10)]
;    (= true true)))


