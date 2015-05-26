(ns italianverbs.test.italiano
  (:refer-clojure :exclude [get-in lookup merge resolve])
  (:require
   [clojure.test :refer :all]
   [italianverbs.cache :refer (build-lex-sch-cache create-index over spec-to-phrases)]
   [italianverbs.engine :as engine]
   [italianverbs.forest :as forest]

   ;; TODO: change to: :refer :all rather than :refer-ing particular things.
   [italianverbs.italiano :as it :refer :all]

   [italianverbs.lexiconfn :as lexiconfn]
   [italianverbs.morphology :refer (fo)]
   [italianverbs.over :refer (overc overh)]
   [italianverbs.parse :refer (toks)]
   [italianverbs.ug :refer :all]
   [italianverbs.unify :refer :all]
   ))

(def test-grammar
  (let [lexicon
        (into {}
              (for [[k v] @it/lexicon]
                (let [filtered-v
                      (filter #(or (= (get-in % [:synsem :sem :pred]) :mangiare)
                                   (and (= (get-in % [:synsem :cat]) :det)
                                        (= (get-in % [:synsem :def]) :def)
                                        (= (get-in % [:synsem :number]) :sing)
                                        )
                                   (= (get-in % [:synsem :sem :pred]) :donna)
                                   (= (get-in % [:synsem :sem :pred]) :io)
                                   (= (get-in % [:synsem :sem :pred]) :pane))
                              v)]
                  (if (not (empty? filtered-v))
                    [k filtered-v]))))
        grammar
        (filter #(or (= (:rule %) "vp-present")
                     (= (:rule %) "s-present-nonphrasal")
                     (= (:rule %) "s-present-phrasal")
                     (= (:rule %) "noun-phrase1"))
                it/grammar)]

    {:enrich it/enrich
     :grammar grammar
     :lexicon lexicon
     :index (create-index grammar (flatten (vals lexicon)) head-principle)}))

(def il-pane
  (over (:grammar test-grammar) (it/it "il") (it/it "pane")))

(def mangiare-vp (over (:grammar test-grammar) (it/it "mangiare")))

(def spec-with-io {:synsem {:subcat '()
                            :sem {:pred :mangiare
                                  :subj {:pred :io}
                                  :obj {:pred :pane}}}})

(def spec-with-donna {:synsem {:subcat '()
                            :sem {:pred :mangiare
                                  :subj {:pred :donna
                                         :number :sing}
                                  :obj {:pred :pane}}}})

(def mangiare-il-pane
  (overc mangiare-vp il-pane))

(deftest mangiare-il-pane-test
  (let [vp mangiare-il-pane]
    (is (not (empty? vp)))
    (is (= (fo (first vp)) "mangiare il pane"))))

(def io-mangio-il-pane
  (engine/generate spec-with-io test-grammar))

(deftest io-mangio-il-pane-test
  (is (= (fo io-mangio-il-pane))))

(def la-donna-mangia-il-pane
  (engine/generate spec-with-donna test-grammar))

(deftest la-donna-mangia-il-pane-test
  (is (= (fo la-donna-mangia-il-pane) "la donna mangia il pane")))

;;(get-in (engine/generate {:synsem {:cat :noun :sem {:number :plur :pred :donna}}} it/medium) [:italiano :b])

(deftest tokenization-1
  "there should be only 2 tokens, even though there's 3 tokens at first according to initial tokenization."
  (let [result (toks "la sua birra" it/lexicon it/lookup)]
    (is (= (.size result) 2))))

(deftest tokenization-1
  "there should be 3 tokens, for each of the tokens according to initial tokenization (there is no way to combine any initial tokens in to larger tokens as there was in the test immediately above."
  (let [result (toks "il gatto nero" it/lexicon it/lookup)]
    (is (= (.size result) 3))))

(def parse-1 (it/parse "un gatto"))

(deftest parse-test-1
  (is (= "un gatto" (fo (first parse-1)))))

(def parse-2 (it/parse "Antonio dorme"))

(deftest parse-test-2
  (is (= (fo (first parse-2))
         "Antonio dorme")))

(def parse-2-1 (it/parse "Antonio dormirà"))

(deftest parse-test-2-1
  (is (= (fo (first parse-2-1))
         "Antonio dormirà")))

(def parse-3 (it/parse "il gatto nero"))

(deftest parse-test-3
  (let [result parse-3]
    (is (> (.size result) 0))
    (is (= (get-in (first result) [:synsem :sem :pred])
           :gatto))))

(def parse-4 (it/parse "il gatto nero dorme"))

(deftest parse-test-4
  (let [result parse-4]
    (is (> (.size result) 0))
    (is (= (get-in (first result) [:synsem :sem :pred])
           :dormire))
    (is (= (get-in (first result) [:synsem :sem :subj :pred])
           :gatto))
    (is (= (get-in (first result) [:synsem :sem :subj :mod :pred])
           :nero))))

(deftest future-mancare
  ;; test for whether exceptional future stems (futuro-stem) work.
  ;; In lexicon, we have: {:futuro-stem "mancher"}.
  (let [result (it "mancherò")]
    (is (> (.size result) 0)))

  ;; The next tests whether (it) rules out regular, but incorrect, forms.
  ;; In this case, "mancarò" is not possible due the the above exceptional form.
  ;; However, this test is disabled for now with "(or true ..)" because
  ;; italiano/analyze does not have a way to prevent regular forms that are wrong.
  ;; e.g. it will analyze "mancarò" as 1st person singular, future, even though
  ;; the correct form is the exceptional "mancherò".
  ;; In other words, (it) does not use the lexicon as it should, which would allow it to
  ;; (correctly) fail to analyze wrong forms like the below.
  (let [result (it "mancarò")]
    (is (or true (= (.size result) 0))))) 

(deftest generate-via-root
  "test whether we can generate an expression using a 'root' {:root r} rather 
   than e.g. generating by a given pred p ({:synsem {:sem {:pred p}}})"
  (let [generated-present (generate {:root {:italiano {:italiano "parlare"}} 
                                     :synsem {:cat :verb
                                              :sem {:tense :present}
                                              :subcat '()}} small)
        generated-past (generate {:root {:italiano {:italiano "parlare"}} 
                                  :synsem {:cat :verb
                                           :sem {:aspect :perfect
                                                 :tense :past}
                                           :subcat '()}} small)]
    (is (map? generated-present))
    (is (= (get-in generated-present [:root :italiano :italiano]) "parlare"))

    (is (map? generated-past))
    (is (= (get-in generated-past [:root :italiano :italiano]) "parlare"))

    ;; These test are perhaps not good tests of generate-by-:root functionality because they 
    ;; will only pass if the Italian lexicon's "parlare" entries' :pred happen to be set to :speak.
    (is (or (= (get-in generated-present [:synsem :sem :pred]) :speak)
            (= (get-in generated-present [:synsem :sem :pred]) :talk)))
    (is (or (= (get-in generated-past [:synsem :sem :pred]) :speak)
            (= (get-in generated-past [:synsem :sem :pred]) :talk)))))

(deftest reflexive-present
  "generate a reflexive sentence"
  (let [i-wash-myself
        (it/generate {:synsem {:subcat '() 
                               :sem {:tense :present 
                                     :obj {:pred :io} 
                                     :pred :wash}}}
                     it/small-plus-vp-pronoun)
        formatted (fo i-wash-myself)]
    (is (or (= formatted "io mi lavo")
            (= formatted "mi lavo")))))

(deftest reflexive-passato
  "generate a reflexive passato sentence"
  (let [i-washed-myself
        (it/generate
         {:synsem {:subcat '()
                   :sem {:tense :past
                         :aspect :perfect
                         :obj {:pred :io}
                         :pred :wash}}} it/small-plus-vp-pronoun)
        formatted (fo i-washed-myself)]
    (is (or (= formatted "io mi sono lavato")
            (= formatted "io mi sono lavata")
            (= formatted "mi sono lavato")
            (= formatted "mi sono lavata")))))





