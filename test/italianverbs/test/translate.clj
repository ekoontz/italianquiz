(ns italianverbs.test.translate
  (:refer-clojure :exclude [get-in]))
(require '[clojure.tools.logging :as log])
(require '[clojure.set :refer :all])
(require '[clojure.test :refer :all])
(require '[italianverbs.engine :refer (get-meaning)])
(require '[italianverbs.english :as en])
(require '[italianverbs.morphology.english :as enm])
(require '[italianverbs.italiano :as it])
(require '[italianverbs.morphology.italiano :as itm])
(require '[italianverbs.translate :refer :all])
(require '[dag-unify.core :refer [get-in strip-refs]])

(deftest translate-a-cat
  (let [un-gatto (translate-all "un gatto")]
    (is (not (empty? (select #(= "a cat" (enm/fo %))
                             (set un-gatto)))))))

(deftest translate-she-reads
  (is (= "she reads" (enm/fo (translate "lei legge")))))

;; TODO: move this test to italianverbs.test.italiano
(deftest test-roundtrip-italian
  (let [retval (itm/fo (it/generate (get-meaning (parse "io dormo"))))]
    (is (or (= "dormo" retval)
            (= "io dormo" retval)))))






