(ns italianverbs.test.english
  (:refer-clojure :exclude [get-in lookup])
  (:require
   [clojure.core :as core]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]
   [italianverbs.english :refer :all :as en]
   [italianverbs.engine :as engine]
   [italianverbs.lexicon.english :as lex]
   [italianverbs.lexiconfn :as lexiconfn :refer (compile-lex)]
   [italianverbs.morphology :refer (fo)]
   [italianverbs.morphology.english :as morph]
   [italianverbs.over :refer :all]
   [italianverbs.parse :as parse]
   [italianverbs.pos :as pos :refer [cat-of-pronoun
                                     comparative determiner
                                     drinkable-noun
                                     non-comparative-adjective noun
                                     pronoun-acc sentential-adverb
                                     verb verb-aux]]
   [italianverbs.pos.english :refer :all]
   [dag-unify.core :as unify :refer [dissoc-paths get-in strip-refs]]))

(def test-lexicon {"buy" {:synsem {:cat :verb
                                   :sem {:pred :comprare
                                         :subj {:human true}
                                         :obj {:buyable true}}}}
                   "sleep" {:synsem {:cat :verb
                                     :sem {:subj {:animate true}
                                           :discrete false
                                           :pred :dormire}}
                            :english {:past "slept"}}})

;; TODO: specific calls to compile-lex -> intransitivize -> transitivize
;; are repeated in src/italianverbs/english.clj as well as here.
(def compiled-1 (compile-lex test-lexicon
                             morph/exception-generator
                             morph/phonize
                             morph/english-specific-rules))

(def compiled (-> compiled-1
                  ;; make an intransitive version of every verb which has an
                  ;; [:sem :obj] path.
                  intransitivize
                  ;; make a transitive version
                  transitivize))

(def buy (get compiled "buy"))
(def sleep (get compiled "sleep"))

(deftest buy-test
  "There should be two entries for 'buy': one has both :subj and :obj; the other has only :subj. Make sure both are present and specified in the source lexicon."
  (is (= (.size buy) 2))

  ;; both transitive and intransitive: check subject spec
  (is (= {:human true} (get-in (nth buy 0) [:synsem :sem :subj])))
  (is (= {:human true} (get-in (nth buy 1) [:synsem :sem :subj])))

  ;; transitive sense: check object spec.
  (is (or (= {:buyable true} (get-in (nth buy 0) [:synsem :sem :obj]))
          (= {:buyable true} (get-in (nth buy 1) [:synsem :sem :obj]))))

  ;; intransitive sense: check object spec.
  (is (or (= :unspec (get-in (nth buy 0) [:synsem :sem :obj]))
          (= :unspec (get-in (nth buy 1) [:synsem :sem :obj])))))

(deftest test-roundtrip-english
  (let [retval (generate (engine/get-meaning (parse "she sleeps")))]
    (is (seq? retval))
    (is (> (.size retval) 0))
    (is (string? (fo (first retval))))
    (is (= "she sleeps" (fo (first retval))))))

(deftest generate-with-spec
  (let [retval (generate {:synsem {:sem {:tense :past, 
                                         :obj :unspec, 
                                         :aspect :perfect, 
                                         :pred :tornare, 
                                         :subj {:pred :loro}}}})]
    (is (not (empty? retval)))))


(deftest antonio-speaks
  (let [antonio-speaks (fo (engine/generate {:synsem {:infl :present :sem {:subj {:pred :antonio} :pred :speak}}} small :enrich true))]
    (is (= "Antonio speaks" antonio-speaks))))

(deftest antonia-plays
  (let [antonia-plays (fo (engine/generate {:synsem {:infl :present :sem {:subj {:pred :antonia} :pred :suonare}}} small :enrich true))]
    (is (= "Antonia plays" antonia-plays))))

(deftest parse-test-1-en
  (is (= "a cat" (fo (first (parse "a cat"))))))

;; TODO: add more testing of generated output
(deftest imperfect-we
  (let [generated (engine/generate
                   {:synsem {:sem {:aspect :progressive 
                                   :tense :past 
                                   :subj {:pred :noi}  }}}
                   small :enrich true)]
    (is (not (nil? generated)))))

(deftest generate-via-root
  "test whether we can generate an expression using a 'root' {:root r} rather 
   than e.g. generating by a given pred p ({:synsem {:sem {:pred p}}})"
  (let [generated-present (engine/generate {:root {:english {:english "speak"}} 
                                            :synsem {:cat :verb
                                                     :sem {:tense :present}
                                                     :subcat '()}} small)
        generated-past (engine/generate {:root {:english {:english "speak"}} 
                                         :synsem {:cat :verb
                                                  :sem {:aspect :perfect
                                                        :tense :past}
                                                  :subcat '()}} small)]
    (is (map? generated-present))
    (is (= (get-in generated-present [:root :english :english]) "speak"))

    (is (map? generated-past))
    (is (= (get-in generated-past [:root :english :english]) "speak"))

    ;; These test are perhaps not good tests of generate-by-:root functionality because they 
    ;; will only pass if the Italian lexicon's "parlare" entries' :pred happen to be set to :speak.
    (is (= (get-in generated-present [:synsem :sem :pred]) :speak))
    (is (= (get-in generated-past [:synsem :sem :pred]) :speak))))

(deftest reflexive-present
  "generate a reflexive sentence"
  (let [i-wash-myself
        (en/generate {:synsem {:subcat '() 
                               :sem {:tense :present 
                                     :obj {:pred :io} 
                                     :pred :wash}}}
                     en/small-plus-vp-pronoun)
        formatted (fo i-wash-myself)]
    (is (or (= formatted "I (♂) wash myself")
            (= formatted  "I (♀) wash myself")))))

(deftest reflexive-past
  "generate a reflexive sentence"
  (let [i-wash-myself
        (en/generate {:synsem {:subcat '() 
                               :sem {:tense :past
                                     :aspect :perfect
                                     :obj {:pred :io} 
                                     :pred :wash}}}
                     en/small-plus-vp-pronoun)
        formatted (fo i-wash-myself)]
    (is (or (= formatted "I (♂) washed myself")
            (= formatted  "I (♀) washed myself")))))

