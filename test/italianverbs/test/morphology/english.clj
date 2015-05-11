(ns italianverbs.test.morphology.english)
(require '[italianverbs.morphology.english :refer :all])
(require '[clojure.test :refer :all])

;; These tests focus on 3rd sing present because it's so complicated!
;; jump  -> jumps
;; play  -> plays
;; carry -> carries
;; try   -> tries

(deftest inflect1
  (let [result (get-string {:infl :present
                            :english "express"
                            :agr {:number :sing
                                  :person :3rd}})]
    (is (= result "expresses"))))

(deftest inflect2
  (let [result (get-string {:infl :present
                            :english "go"
                            :agr {:number :sing
                                  :person :3rd}})]
    (is (= result "goes"))))

(deftest inflect3
  (let [result (get-string {:infl :present
                            :english "play"
                            :agr {:number :sing
                                  :person :3rd}})]
    (is (= result "plays"))))


(deftest inflect3
  (let [result (get-string {:infl :present
                            :english "play"
                            :agr {:number :sing
                                  :person :3rd}})]
    (is (= result "plays"))))


(deftest inflect4
  (let [result (get-string {:infl :present
                            :english "try"
                            :agr {:number :sing
                                  :person :3rd}})]
    (is (= result "tries"))))

(deftest inflect5
  (let [result (get-string {:agr {:person :1st
                                  :number :sing}
                            :cat :verb
                            :infl :imperfetto
                            :english "lie"})]
    (is (= result "was lying"))))
