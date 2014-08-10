(ns italianverbs.test.forest
  (:refer-clojure :exclude [get-in resolve merge])
  (:require
   [clojure.core :as core]
   ;; have to exclude partition-by because of some namespace clash, not sure how else to fix
   [clojure.core.async :as async :exclude [partition-by]]
   [clojure.set :refer :all]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]
   [italianverbs.cache :refer (build-lex-sch-cache get-comp-phrases-of get-head-phrases-of get-lex
                                                   get-parent-phrases-for-spec
                                                   overc overh overh-with-cache overc-with-cache)]
   [italianverbs.forest :refer :all]
   [italianverbs.grammar.italiano :as it]
   [italianverbs.lexicon :refer :all]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.over :as over]
   [italianverbs.unify :as unify]
   [italianverbs.unify :refer (dissoc-paths get-in fail? lazy-shuffle remove-top-values-log show-spec unifyc)]))

(deftest generate-test
  (let [generated (first (take 1 (generate
                                  {:synsem {:cat :verb :subcat '() :aux false :sem {:subj {:animate true}}}}
                                  it/grammar lexicon it/cache)))]
    (is (not (fail? generated)))
    (is (not (nil? generated)))))
