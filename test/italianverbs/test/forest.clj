(ns italianverbs.test.forest
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:use [clojure.test])
  (:require
   [italianverbs.generate :as generate]
   [clojure.tools.logging :as log]
   [italianverbs.forest :refer :all]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.over :refer :all]
   [italianverbs.unify :refer :all]))

(deftest lightning1
  (let [bolts (filter (fn [x] (= "cc10" (get-in x '(:comment)))) (overh parents (overh parents lex)))]
    (is (not (empty? bolts)))))

(deftest lightning2
  (let [bolt1 (take 1 (lightningb :top parents))
        bolt2 (take 1 (lightningb))]
    (is (not (nil? bolt1)))
    (is (not (nil? bolt2)))))

(deftest lightning3
  (is (not (empty? (take 1 (lightningb {:synsem {:sem {:pred :dormire}}}))))))

(deftest lightning4
  (is (not (empty? (take 1 (lightningb {:synsem {:sem {:subj {:human true} :pred :dormire}}}))))))


