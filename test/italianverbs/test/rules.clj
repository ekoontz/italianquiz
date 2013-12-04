;; TODO: move tests that use gram/ and lexfn/ to their own namespaces, since
;; generate/ should not depend on those - the grammar and lexicon are independent of
;; generation.
(ns italianverbs.test.rules
  (:refer-clojure :exclude [get-in merge resolve find])
  (:use [clojure.test])
  (:require
   [italianverbs.generate :as generate]
   [clojure.tools.logging :as log]
   [italianverbs.lexiconfn :as lexfn]
   [italianverbs.unify :refer :all]))

(deftest test-constraints
  (let [constraints {:constraints #{{:synsem {:infl :futuro
                                              :sem {:tense :futuro}}}
                                    {:synsem {:infl :present
                                              :sem {:tense :present}}}}}]
    (is (not (nil? constraints)))))



