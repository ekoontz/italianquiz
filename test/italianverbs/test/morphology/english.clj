(ns italianverbs.test.morphology.english)
(require '[italianverbs.morphology.english :refer :all])
(require '[clojure.test :refer :all])

(deftest inflect1
  (let [result (get-string {:infl :present
                            :english "to express"
                            :agr {:number :sing
                                  :person :3rd}})]
    (= result "expresses")))

(deftest inflect2
  (let [result (get-string {:infl :present
                            :english "to go"
                            :agr {:number :sing
                                  :person :3rd}})]
    (= result "goes")))

(deftest inflect3
  (let [result (get-string {:infl :present
                            :english "to play"
                            :agr {:number :sing
                                  :person :3rd}})]
    (= result "plays")))


