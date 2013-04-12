(ns italianverbs.grammar
  (:require
   [italianverbs.fs :as fs]
   [italianverbs.morphology :as morph]
   [italianverbs.lexiconfn :as lexfn]
   [clojure.string :as string]))

;;    [1]
;;   /   \
;;  /     \
;; H[1]    C
(def head-principle
  (let [head-cat (ref :top)
        head-sem (ref :top)
        head-infl (ref :top)]
    {:synsem {:cat head-cat
              :sem head-sem
              :infl head-infl}
     :head {:synsem {:cat head-cat
                     :infl head-infl
                     :sem head-sem}}}))

;;     subcat<>
;;     /      \
;;    /        \
;; H subcat<1>  C[1]
(def subcat-1-principle
  (let [comp-synsem (ref :top)]
    {:subcat '()
     :head {:synsem {:subcat {:1 comp-synsem}}}
     :comp {:synsem comp-synsem}}))

;;     subcat<1>
;;     /      \
;;    /        \
;; H subcat<1,2>  C[2]
(def subcat-2-principle
  (let [comp-synsem (ref :top)
        parent-subcat (ref :top)]
    {:synsem {:subcat {:1 parent-subcat}}
     :head {:synsem {:subcat {:1 parent-subcat
                              :2 comp-synsem}}}
     :comp {:synsem comp-synsem}}))


;; a language's morphological inflection is
;; identical to its head's SYNSEM|INFL value.
(def verb-inflection-morphology
  (let [infl (ref :top)
        cat (ref :verb)]
    {:italian {:a {:infl infl
                   :cat cat}}
     :english {:a {:infl infl
                   :cat cat}}
     :head {:italian {:infl infl
                      :cat cat}
            :english {:infl infl
                      :cat cat}
            :synsem {:cat cat
                     :infl infl}}}))

(def vp-rules
  (let [head (ref :top)
        comp (ref :top)
        infl (ref :top)
        agr (ref :top)]

    (def vp-past-np
      (let [infl (ref :past)
            essere (ref :top)]
        (fs/unifyc head-principle
                   subcat-2-principle
                   verb-inflection-morphology
                   {:comment "vp[past] &#x2192; head comp"}
                   {:synsem {:essere essere}
                    :head {:synsem {:essere essere}}}
                   {:head head
                    :comp comp
                    :1 head
                    :2 comp
                    :synsem {:infl :past}
                    :extend {:a {:head 'transitive-verbs
                                 :comp 'np}}})))
    (def vp-past-pp
      (let [infl (ref :past)
            essere (ref :top)]
        (fs/unifyc head-principle
                   subcat-2-principle
                   verb-inflection-morphology
                   {:comment "vp[past] &#x2192; head comp"}
                   {:synsem {:essere essere}
                    :head {:synsem {:essere essere}}}
                   {:head head
                    :comp comp
                    :1 head
                    :2 comp
                    :synsem {:infl :past}
                    :extend {:a {:head 'verbs-taking-pp
                                 :comp 'prep-phrase}
                             }})))

    (def vp-infinitive-transitive
      (fs/unifyc head-principle
                 subcat-2-principle
                 verb-inflection-morphology
                 {:head {:synsem {:cat :verb
                                  :infl :infinitive
                                  :subcat {:2 {:cat :noun}}}}}
                 {:comment "vp[inf] &#x2192; head comp"
                  :head head
                  :comp comp
                  :1 head
                  :2 comp
                  :extend {
                           :a {:head 'transitive-verbs
                               :comp 'np}}}))

    (def vp-present
      (let [infl (ref :present)]
        (fs/unifyc head-principle
                   subcat-2-principle
                   verb-inflection-morphology
                   {:comment "vp &#x2192; head comp"
                    :head head
                    :comp comp
                    :1 head
                    :2 comp
                    :extend {
                             :a {:head 'transitive-verbs
                                 :comp 'np}
                             :b {:head 'verbs-taking-pp
                                 :comp 'prep-phrase}
                             :c {:head 'essere-aux
                                 :comp 'intransitive-verbs}
                             :d {:head 'avere-aux
                                 :comp 'intransitive-verbs}
                             :e {:head 'avere-aux
                                 :comp 'vp-past-np}
                             :f {:head 'essere-aux
                                 :comp 'vp-past-pp}
                             :g {:head 'modal-verbs
                                 :comp 'vp-infinitive-transitive}
                             :h {:head 'modal-verbs
                                 :comp 'intransitive-verbs}
                             }})))
    
    (def vp-future
      (fs/unifyc head-principle
                 subcat-2-principle
                 verb-inflection-morphology
                 {:comment "vp[future] &#x2192; head comp"
                  :head head
                  :comp comp
                  :1 head
                  :2 comp
                  :extend {
                           :a {:head 'transitive-verbs
                               :comp 'np}
                           :b {:head 'verbs-taking-pp
                               :comp 'prep-phrase}}}))))

(def subject-verb-agreement
  (let [infl (ref :top)
        agr (ref {:case :nom})]
    {:comp {:synsem {:agr agr}}
     :head {:synsem {:subcat {:1 {:agr agr}}
                     :infl infl}
            :italian {:agr agr
                      :infl infl}
            :english {:agr agr
                      :infl infl}}}))

(def sentence-rules
  (let [subj-sem (ref :top)
        subcatted (ref {:cat :noun
                        :subcat '()
                        :sem subj-sem})
        infl (ref :top)
        comp (ref {:synsem subcatted})
        agr (ref :top)
        head (ref {:synsem {:cat :verb
                            :sem {:subj subj-sem}
                            :subcat {:1 subcatted}}})]

    ;; present
    (def s-present
      (fs/unifyc head-principle subcat-1-principle
                 subject-verb-agreement
                 {:synsem {:infl :present}}
                 {:comment "sentence (present) (4 subrules)"
                  :head head
                  :comp comp
                  :1 comp
                  :2 head
                  :extend {
                           :a {:comp 'np
                               :head 'vp-present}
                           :b {:comp 'pronouns
                               :head 'vp-present}
                           :d {:comp 'np
                               :head 'intransitive-verbs}
                           :e {:comp 'pronouns
                               :head 'intransitive-verbs}
                           }}))
    
     ;; future
    (def s-future
      (fs/unifyc head-principle subcat-1-principle
                 subject-verb-agreement
                 {:synsem {:infl :futuro}}
                 {:comment "sentence (future) (4 subrules)"
                  :head head
                  :comp comp
                  :1 comp
                  :2 head
                  :extend {

                           :a {:comp 'np
                               :head 'vp-future}
                           :b {:comp 'pronouns
                               :head 'vp-future}
                           :d {:comp 'np
                               :head 'intransitive-verbs}
                           :e {:comp 'pronouns
                               :head 'intransitive-verbs}
                           }}))
  
    (list s-present s-future)))

(def nbar
  (let [head (ref :top)
        comp (ref :top)
        subcat (ref :top)
        agr (ref :top)
        head-semantics (ref :top)
        adjectival-predicate (ref :top)]
    (fs/unifyc
     head-principle
     {:head head
      :comp comp
      :1 head
      :2 comp}
     (let [def (ref :top)]
       {:head {:synsem {:def def}}
        :synsem {:def def}})
     {:synsem {:sem head-semantics}
      :comp {:synsem {:sem {:mod head-semantics}}}}
     {:synsem {:sem {:mod adjectival-predicate}}
      :comp {:synsem {:sem {:mod head-semantics
                            :pred adjectival-predicate}}}}
     {:synsem {:agr agr
               :subcat subcat}
      :head {:synsem {:agr agr
                      :subcat subcat}}
      :comp {:italian {:agr agr}
             :english {:agr agr}}
      :comment "n&#x0305; &#x2192; adj noun"
      :extend {:a {:head 'nouns
                   :comp 'adjectives}}})))

(def np-rules 
  (let [head (ref :top)
        comp (ref :top)]
    (def np1
      (fs/unifyc head-principle subcat-1-principle ;; NP -> Comp Head
                 (let [agr (ref :top)]
                   (fs/unifyc
                    (let [def (ref :top)]
                      {:head {:synsem {:def def}}
                       :synsem {:def def}
                       :comp {:synsem {:def def}}})
                    {:head {:synsem {:cat :noun
                                     :agr agr}}
                     :synsem {:agr agr}}
                    {:comment "np &#x2192; det (noun or nbar)"
                     :synsem {:agr agr}
                     :head head
                     :comp comp
                     :1 comp
                     :2 head
                     :extend {
                              :a {:comp 'determiners
                                  :head 'nouns}
                              :b {:comp 'determiners
                                  :head 'nbar}}
                     }
                    ))))
    (list np1)))

(def prep-phrase
  (let [head (ref {:synsem {:cat :prep}})
        comp (ref :top)]
    (fs/unifyc head-principle
               subcat-1-principle
               {
                :head head
                :comp comp
                :1 head
                :2 comp
                :extend {:a {:head 'prepositions
                             :comp 'np}
                         :b {:head 'prepositions
                             :comp 'proper-nouns}}})))

(def rules (concat np-rules vp-rules sentence-rules))

(def np (nth np-rules 0))

;; TODO: move to lexicon (maybe).
(defn italian-number [number]
  (cond
   (= number 1) "una"
   (= number 2) "due"
   (= number 3) "tre"
   (= number 4) "quattro"
   (= number 5) "cinque"
   (= number 6) "sei"
   (= number 7) "sette"
   (= number 8) "otto"
   (= number 9) "nove"
   (= number 10) "dieci"
   (= number 11) "undici"
   (= number 12) "dodici"
   (= number 13) "tredici"
   (= number 14) "quattordici"
   (= number 15) "quindici"
   (= number 16) "sedici"
   (= number 17) "diciassette"
   (= number 18) "diciotto"
   (= number 19) "diciannove"
   
   ;; ...
   (= number 20) "venti"
   (< number 30) (str (italian-number 20) (italian-number (- number 20)))
   (= number 30) "trenta"
   (< number 40) (str (italian-number 30) (italian-number (- number 30)))
   true "??"))

(defn italian-time [hour minute ampm]
  (let [print-hour
        (if (<= minute 30)
          (italian-number hour)
          (italian-number
           (if (= hour 12)
             1
             (+ hour 1))))]
    (str
     (cond
      (and (= print-hour 12)
           (= ampm "am"))
      "mezzogiorno"
      (and (= print-hour 12)
           (= ampm "pm"))
      "mezzonotte"
      true (morph/italian-article {:italian "le" :def :def} {:number :singular :italian print-hour :numerical true}))
     (cond
      (= minute 0) ""
      (= minute 15) " e un quarto"
      (= minute 30) " e mezzo"
      (= minute 45) " meno un quarto"
      (<= minute 30)
      (str " e " (italian-number minute))
      true (str " meno "(italian-number (- 60 minute)))))))

(defn english-time [hour minute ampm]
  (string/trim (str hour ":" (if (< minute 10) (str "0" minute) minute) " " (if (= hour 12) (if (= ampm "am") " after midnight" " after noon") ""))))
