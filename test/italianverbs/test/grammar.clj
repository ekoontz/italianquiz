(ns italianverbs.test.grammar
  (:require
   [italianverbs.unify :as unify])
  (:use [clojure.test]
        [italianverbs.generate]
        [italianverbs.morphology]
        [italianverbs.grammar]
        [italianverbs.lexiconfn :only (unify)]))

(deftest io-dormo
  (let [result (first (take 1
                            (generate
                             (unify s-present {:synsem {;; TODO: :cat :verb is a workaround for overgeneration due
                                                        ;; to generation redesign.
                                                        :cat :verb
                                                        :sem {:pred :dormire
                                                              :subj {:pred :io}}}}))))]
    (is (not (unify/fail? result)))
    (is (= (unify/get-in (finalize result) '(:italian)) "io dormo"))
    (is (= (unify/get-in (finalize result) '(:english)) "I sleep"))))

(defn successful [result]
  (or (not (map? result))
      (is (not (unify/fail? result))))
  (or (not (seq? result))
      (is (not (nil? (seq result))))))

(def fare-bene (over vp-plus-adverb "fare" "bene"))
(deftest fare-bene-test
  (is (successful fare-bene)))

(def a-vendere-la-casa (over prep-plus-verb-inf "a"
                             (over vp
                                   "vendere"
                                   (over np "la" "casa"))))
(deftest a-vendere-la-casa-test
  (is (successful a-vendere-la-casa)))

(def fare-bene-a-vendere-la-casa
  (over vp fare-bene a-vendere-la-casa))
(deftest fare-bene-a-vendere-la-casa-test
  (is (successful fare-bene-a-vendere-la-casa)))

(def avere-fare-bene-a-vendere-la-casa
  ;; TODO: should not have to look for second or first member: (over) should handle it.
  (second (over vp-aux "avere" fare-bene-a-vendere-la-casa)))

(deftest avere-fare-bene-a-vendere-la-casa-test
  (is (successful avere-fare-bene-a-vendere-la-casa)))

(def tu-hai-fatto-bene-a-vendere-la-casa
  ;; TODO: (over) can't take a sequence as the 2nd argument, so can't do this (commented-out):
                                        ;  (let [result (over s-past "tu" avere-fare-bene-a-vendere-la-casa)]
  ;; have to do this instead:
  (let [result (over s-past "tu" avere-fare-bene-a-vendere-la-casa)]

    ;; symptoms of the same problem: (inflexibility of input of (over))
    (if (seq? result)
      (first result)
      result)))

(deftest tu-hai-fatto-bene-a-vendere-la-casa-test
  (is (successful tu-hai-fatto-bene-a-vendere-la-casa))
  (let [english (unify/get-in (finalize (unify/copy tu-hai-fatto-bene-a-vendere-la-casa))
                              '(:english))]
    ;; TODO: figure out why extra space is being generated after "you".
    (is (or (= english "you  (&#x2642;) did well to sell the house")
            (= english "you  (&#x2640;) did well to sell the house"))))
  (is (= "tu hai fatto bene a vendere la casa"
         (unify/get-in (finalize (unify/copy tu-hai-fatto-bene-a-vendere-la-casa))
                       '(:italian)))))

(deftest adj-agreement-with-subject
  "adjectives must agree with subjects - tests this behavior with intermediate 'meno ricco' between the subject and the adjective."
  (let [lei-e-piu-ricca-di-giorgio
        (over s-present "lei"
              (over vp "essere"
                    (over intensifier-phrase "più"
                          ;; error: (over adj-phrase (second (it "ricco")))
                          (over adj-phrase "ricco"
                                (over prep-phrase "di" "Giorgio")))))]
    (is (= (strip (get-italian (get-in (first lei-e-piu-ricca-di-giorgio) '(:italian))))
           "lei è più ricca di Giorgio"))))

(deftest fare-bene
  (let [result (first (take 1 (generate (unify s-past {:synsem {:sem {:pred :fare
                                                                      :mod {:pred :bene}}}}))))]
    (is (successful result))))

(deftest fare-bene-vendere-casa
  (let [result (first (take 1 (generate (unify s-past {:synsem {:sem {:pred :fare
                                                                      :obj {:pred :vendere
                                                                            :obj {:pred :casa}}}}}))))]
    (is (successful result))))

(deftest io-sono-venuto-per-dormire
  (is (successful (over s-past "io"
                         (over vp-aux "essere" (over vp-past "venire" (over prep-plus-verb-inf "per" "dormire")))))))
