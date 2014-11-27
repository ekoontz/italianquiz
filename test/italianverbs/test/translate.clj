(ns italianverbs.test.translate)
(require '[italianverbs.morphology :refer (fo)])
(require '[italianverbs.grammar.italiano :as it])
(require '[italianverbs.translate :refer :all])
(require '[clojure.test :refer :all])

(deftest translate-a-cat
  (is (= "a cat" (translate "un gatto"))))

(deftest translate-she-reads
  (is (= "she reads" (translate "lei legge"))))

(deftest test-roundtrip-italian
  (let [retval (fo (it/generate (get-meaning (parse "io dormo"))))]
    (or
     (and
      (seq? retval)
      (= (.size retval))
      (is (= "io dormo" (first retval))))
     (and
      (string? retval)
      (is (= "io dormo" retval))))))

