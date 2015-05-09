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


(deftest inflect3
  (let [result (get-string {:infl :present
                            :english "to play"
                            :agr {:number :sing
                                  :person :3rd}})]
    (= result "plays")))


(deftest inflect4
  (let [result (get-string {:infl :present
                            :english "to try"
                            :agr {:number :sing
                                  :person :3rd}})]
    (= result "tries")))


