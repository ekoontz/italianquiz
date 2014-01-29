(ns italianverbs.test.forest
  (:refer-clojure :exclude [get-in merge resolve find parents])
  (:require
   [clojure.set :refer (union)]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]

   [italianverbs.forest :exclude (lightning-bolt)]
   [italianverbs.forest :as forest]
   [italianverbs.grammar :refer (grammar)]
   [italianverbs.lexicon :refer (lexicon it en)]
   [italianverbs.lexiconfn :exclude (unify)]
   [italianverbs.morphology :refer (fo fo-ps)]
   [italianverbs.over :refer :all]
   [italianverbs.ug :refer :all]
   [italianverbs.unify :refer (fail? get-in lazy-shuffle merge remove-top-values unify unifyc)]))

;; minip and minil are for testing.
(def minip (filter (fn [x]
                     (or
                      (= (:comment x) "vp-imperfetto")))
                   grammar))

(def minil (filter (fn [x]
                     (or
                      (= (get-in x '(:italian :italian)) "per")
                      (= (get-in x '(:italian :infinitive)) "volere")))
                   lexicon))

;; TODO: calling (.size) because (map) is lazy, and I want to realize
;; the sequence - must be a better way to loop over the grammar and realize the result.
(.size (map (fn [rule]
       (intern *ns* (symbol (:comment rule)) rule))
     grammar))

(def test-cache (forest/build-lex-sch-cache grammar lexicon grammar))

(defn lightning-bolt [spec]
  (forest/lightning-bolt spec lexicon grammar 0 test-cache))

(deftest sleeper-1 []
  (let [sleeper (get-in (first (lightning-bolt {:synsem {:sem {:pred :dormire}}})) '(:synsem :sem :subj))]
    (is (not (nil? sleeper)))))

(deftest i-sleep-1 []
  (let [i-sleep (first (lightning-bolt {:synsem {:subcat '()
                                                 :cat :verb
                                                 :sem {:subj {:pred :io}
                                                       :pred :dormire}}}))]

    (is (or (= (list "Io dormo (I sleep).") (fo i-sleep))
            (= (list "Io dormirò (I will sleep).") (fo i-sleep))
            (= (list "Io ho dormito (I slept).") (fo i-sleep))
            (= (list "Io dormivo (I was sleeping).") (fo i-sleep))))))


(deftest human-sleeper-1 []
  (let [human-sleeper (get-in (first (lightning-bolt {:synsem {:subcat '()
                                                               :sem {:subj {:human true}
                                                                     :pred :dormire}}}))
                               '(:synsem :sem :subj))]
    (is (= (get-in human-sleeper '(:human)) true))
    (is (= (get-in human-sleeper '(:animate)) true))

(deftest animal-sleeper-1 []
  (let [animal-sleeper (get-in (first (lightning-bolt {:synsem {:subcat '()
                                                                :sem {:subj {:human false}
                                                                      :pred :dormire}}}))
                               '(:synsem :sem :subj))]
    (is (= (get-in animal-sleeper '(:human)) false))))

(deftest edible-1
  (let [edible (get-in (first (lightning-bolt {:synsem {:subcat '()
                                                        :sem {:pred :mangiare}}})) '(:synsem :sem))]
    (is (not (nil? edible)))))))

(deftest animal-eater-1 []
  (let [eating-semantics (get-in (first (lightning-bolt {:synsem {:subcat '()
                                                                  :sem {:subj {:human false}
                                                                        :pred :mangiare}}}))
                                 '(:synsem :sem))]
    (is (= (get-in eating-semantics '(:subj :human)) false))))

