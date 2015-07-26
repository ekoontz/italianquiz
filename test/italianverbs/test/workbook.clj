(ns italianverbs.test.workbook
  (:refer-clojure :exclude [get-in merge resolve find])
  (:use [clojure.test]
        [italianverbs.lexiconfn]
        [italianverbs.morphology]
        [italianverbs.ug]
        [italianverbs.workbook]
        [dag-unify.core :exclude [unify]]
        ))

;; TODO: add tests that reflect user activity within workbook..

