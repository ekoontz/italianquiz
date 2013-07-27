(ns italianverbs.test.generate
  (:use [clojure.test]
        [clojure.stacktrace]
        [italianverbs.generate]
        [italianverbs.grammar]
        [italianverbs.lexicon])
  (:require
   [clojure.tools.logging :as log]
   [italianverbs.lev :as lev]
   [italianverbs.lexiconfn :as lexfn]
   [italianverbs.morphology :as morph]
   [italianverbs.unify :as unify]
   [italianverbs.config :as config]
   [italianverbs.html :as html]
   [italianverbs.search :as search]
   [clojure.string :as string]))

(deftest il-libro
  (let [il-libro (finalize (first (over np "il" "libro")))]
    (is (not (unify/fail? il-libro)))
    (is (= "il libro"
           (get-in il-libro '(:italian))))
    (is (= "the book"
           (get-in il-libro '(:english))))))

(deftest il-cane
  (let [il-cane (finalize (first (over np "il" "cane")))]
    (is (not (unify/fail? il-cane)))
    (is (= "il cane"
           (get-in il-cane '(:italian))))
    (is (= "the dog"
           (get-in il-cane '(:english))))))

(deftest i-cani
  (let [i-cani (finalize (first (over np "i" "cane")))]
    (is (not (unify/fail? i-cani)))
    (is (= "i cani"
           (get-in i-cani '(:italian))))
    (is (= "the dogs"
           (get-in i-cani '(:english))))))

;(deftest il-cane-nero
;  (let [il-cane-nero (finalize (first (over np "il" (over nbar "cane" "nero")));)]
;    (is (not (unify/fail? il-cane-nero)))
;    (is (= "il cane nero"
;           (get-in il-cane-nero '(:italian))))
;    (is (= "the black dog"
;           (get-in il-cane-nero '(:english))))))

;(deftest i-cani-neri
;  (let [i-cani-neri (finalize (first (over np "i" (over nbar "cane" "nero"))))]
;    (is (not (unify/fail? i-cani-neri)))
;    (is (= "i cani neri"
;           (get-in i-cani-neri '(:italian))))
;    (is (= "the black dogs"
;           (get-in i-cani-neri '(:english))))))

(deftest all-children-done-old-style-1
  (is (nil? (add-child-where (first (over nbar "studente" "brutto"))))))

(deftest all-children-done-old-style-2
  (is (nil? (add-child-where (first (over np "i" (over nbar "studente" "brutto")))))))

(deftest gli-studenti-brutti
  (is (= "gli studenti brutti"
         (get-in (finalize (first (over np "i" (over nbar "studente" "brutto"))))
                 '(:italian)))))

(deftest io-sogno
  (let [io-sogno (finalize (first (over s-present "io" "sognare")))]
    (is (= "io sogno"
           (get-in io-sogno '(:italian))))
    (is (= "I dream"
           (get-in io-sogno '(:english))))))

;(deftest lei-ci-vede
;  (let [lei-ci-vede (finalize (first (over s-present "lei" (over vp-pron "ci" ";vedere"))))]
;    (is (= "lei ci vede"
;           (get-in lei-ci-vede '(:italian))))
;    (is (= "she sees us"
;           (get-in lei-ci-vede '(:english))))))

(deftest io-parlo-la-parola
  (let [parlare-la-parola (first (over vp "parlare" (over np "la" "parola")))
        io-parlo-la-parola (first
                            (over s-present "io"
                                  (over vp "parlare" (over np "la" "parola"))))]

    (is (nil? (add-child-where parlare-la-parola)))

    (is (nil? (add-child-where io-parlo-la-parola)))

    (is (= "io parlo la parola"
           (get-in (finalize io-parlo-la-parola) '(:italian))))
    (is (= "I speak the word"
           (get-in (finalize io-parlo-la-parola) '(:english))))
  ))

(deftest loro-hanno-il-pane
  (let [loro-hanno-il-pane (first (over s-present "loro"
                                                  (over vp "avere" (over np "il" "pane"))))
        hanno-il-pane (first (over vp "avere" (over np "il" "pane")))]
    (is (nil? (add-child-where hanno-il-pane)))
    (is (nil? (add-child-where loro-hanno-il-pane)))
    (is (= "loro hanno il pane"
           (get-in (finalize loro-hanno-il-pane) '(:italian))))
;    (is (= "they have the bread"
;           (get-in loro-hanno-il-pane '(:english)))
  ))

(deftest generate-nbar
  (let [nbar (take 1 (generate nbar))]
    (is (not (unify/fail? nbar)))))

(deftest generate-np
  (let [np (take 1 (generate np))]
    (is (not (unify/fail? np)))))

;(deftest generate-vp
;  (let [vp (take 1 (generate vp-present))]
;    (is (not (unify/fail? vp)))))

(deftest generate-s-present
  (let [sentence (take 1 (generate s-present))]
    (is (not (unify/fail? sentence)))))

(deftest add-child-where-1
  (let [cane (first (over nbar "cane"))
        cane-rosso (first (over nbar "cane" "rosso"))]
    (is (= (add-child-where cane) :comp))
    (is (nil? (add-child-where cane-rosso)))))


(deftest stack-overflow-error
    "merge has a problem: we hit StackOverflowError java.util.regex.Pattern$BmpCharProperty.match (Pattern.java:3366) when this test is run.
   Code works as expected if merge is replaced with unify. However, currently this test passes for some reason."
    (lexfn/unify
     (get-in (merge (let [head-cat (ref :top)
                                      head-is-pronoun (ref :top)
                                      head-sem (ref :top)
                                      head-infl (ref :top)]
                                  {:synsem {:cat head-cat
                                            :pronoun head-is-pronoun
                                            :sem head-sem
                                            :infl head-infl}
                                   :head {:synsem {:cat head-cat
                                                   :pronoun head-is-pronoun
                                                   :infl head-infl
                                                   :sem head-sem}}})
                                (let [essere (ref :top)
                                      infl (ref :top)
                                      cat (ref :verb)]
                                  {:italian {:a {:infl infl
                                                 :cat cat}}
                                   :english {:a {:infl infl
                                                 :cat cat}}
                                   :synsem {:infl infl
                                            :essere essere}
                                   :head {:italian {:infl infl
                                                    :cat cat}
                                          :english {:infl infl
                                                    :cat cat}
                                          :synsem {:cat cat
                                                   :essere essere
                                                   :infl infl}}}))
                   '(:head))
     (lexfn/unify
      {:italian {:foo 42}}
      (let [infl (ref :top)]
        {:italian {:infl infl}
         :english {:infl infl}
         :synsem {:infl infl}}))))

;; TODO: move this test to grammar.clj or lexicon.clj.
;(deftest adj-agreement-with-subject
;  "adjectives must agree with subjects - tests this behavior with intermediate ;'meno ricco' between the subject and the adjective."
;  (let [lei-e-piu-ricca-di-giorgio
;        (over s-present "lei"
;              (over vp "essere"
;                    (over intensifier-phrase "più"
;                          (over adj-phrase "ricco"
;                                (over prep-phrase "di" "Giorgio")))))]
;    (is (= (morph/strip (morph/get-italian (get-in (first lei-e-piu-ricca-di-giorgio) '(:italian))))
;           "lei è più ricca di Giorgio"))))
