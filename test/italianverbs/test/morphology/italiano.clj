(ns italianverbs.test.morphology.italiano
  (:refer-clojure :exclude [get get-in merge resolve str])
  (:use [clojure.test]))
(require '[clojure.string :as string])
(require '[clojure.string :refer (trim)])
(require '[italianverbs.italiano :as it])
(require '[italianverbs.morphology :refer (fo)])
(require '[italianverbs.morphology.italiano :refer :all])
(require '[dag-unify.core :refer :all])

(deftest lookup-inflection
  (let [result (it/it "dorme")]
    (is (> (.size result) 0))))

(deftest inflect-parlo
  (let [result (get-string {:agr {:person :1st
                                  :number :sing}
                            :cat :verb
                            :infl :present
                            :italiano "parlare"})]
    (is (= result "parlo"))))

(deftest inflect-isco
  (let [result (get-string {:agr {:person :1st
                                  :number :sing}
                            :boot-stem1 "capisc"
                            :cat :verb
                            :infl :present
                            :italiano "capire"})]
    (is (= result "capisco"))))

(deftest futuro-care
  "future tense with verbs that end in X-care should be future-conjucated with the stem being X-ch (not X-c"
  (let [result (get-string {:agr {:person :1st
                                  :number :sing}
                            :infl :futuro
                            :italiano "cercare"})]
    (is (= result "cercherò"))))
