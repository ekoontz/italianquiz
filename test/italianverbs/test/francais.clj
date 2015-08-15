(ns italianverbs.test.francais)
(require '[clojure.test :refer :all])
(require '[italianverbs.francais :as fr])
(require '[italianverbs.morphology.francais :refer [fo]])

(deftest future-tense
  (is (not (empty? (filter #(= "j'accompagnerai" %)
                           (map fo (fr/+ "je" "accompagner")))))))
