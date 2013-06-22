(ns italianverbs.grammar
  (:require
   [italianverbs.unify :as fs]
   [italianverbs.morphology :as morph]
   [italianverbs.lexiconfn :as lexfn]
   [clojure.string :as string]))

(defn unify [ & args]
  (apply fs/unifyc args))

;;    [1]
;;   /   \
;;  /     \
;; H[1]    C
(def head-principle
  (let [head-cat (ref :top)
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
                     :sem head-sem}}}))

;;     subcat<>
;;     /      \
;;    /        \
;; H subcat<1>  C[1]
(def subcat-1-principle
  (let [comp-synsem (ref {:subcat '()})]
    {:synsem {:subcat '()}
     :head {:synsem {:subcat {:1 comp-synsem
                              :2 '()}}}
     :comp {:synsem comp-synsem}}))

;;     subcat<1>
;;     /      \
;;    /        \
;; H subcat<1,2>  C[2]
(def subcat-2-principle
  (let [comp-synsem (ref {:cat :top})
        parent-subcat (ref {:cat :top})]
    {:synsem {:subcat {:1 parent-subcat}}
     :head {:synsem {:subcat {:1 parent-subcat
                              :2 comp-synsem}}}
     :comp {:synsem comp-synsem}}))


;; a language's morphological inflection is
;; identical to its head's SYNSEM|INFL value.
(def verb-inflection-morphology
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

(def italian-head-first
  (let [head-italian (ref :top)
        comp-italian (ref :top)]
    {:head {:italian head-italian}
     :comp {:italian comp-italian}
     :italian {:a head-italian
               :b comp-italian}}))

(def italian-head-last
  (let [head-italian (ref :top)
        comp-italian (ref :top)]
    {:head {:italian head-italian}
     :comp {:italian comp-italian}
     :italian {:a comp-italian
               :b head-italian}}))

(def english-head-first
  (let [head-english (ref :top)
        comp-english (ref :top)]
    {:head {:english head-english}
     :comp {:english comp-english}
     :english {:a head-english
               :b comp-english}}))

(def english-head-last
  (let [head-english (ref :top)
        comp-english (ref :top)]
    {:head {:english head-english}
     :comp {:english comp-english}
     :english {:a comp-english
               :b head-english}}))

(def vp-rules

  (let [head (ref :top)
        comp (ref {:subcat '()})
        infl (ref :top)
        agr (ref :top)]

  (def vp ;; TODO replace other vps with just vp.
    (fs/unifyc head-principle
               subcat-2-principle
               verb-inflection-morphology
               {:comment "vp &#x2192; head comp"
                :comment-plaintext "vp -> head comp"}
               italian-head-first
               english-head-first
               {:comp comp
                :head head}
               {:head {:synsem {:cat :verb}}}
;;; TODO: commenting out the following for now; why is it here in the first place?
;               {:comp {:synsem {:pronoun {:not true}}}}
               {:extend {;:a {:head 'lexicon
                         ;    :comp 'np}
                         ;:b {:head 'lexicon
                         ;    :comp 'prep-phrase}
                         ;:c {:head 'lexicon
                         ;    :comp 'vp-infinitive-transitive}
                         ;:d {:head 'lexicon
                         ;    :comp 'lexicon}
                         :e {:head 'lexicon
                             :comp 'intensifier-phrase}
                         }}))

  (def vp-pron
    (fs/merge
     (unify
      head-principle
      subcat-2-principle
      italian-head-last
      english-head-first
      {:head {:synsem {:cat :verb
                       :infl :present}} ;; TODO: allow other than :present. (:present-only for now for testing).
       :comp {:synsem {:cat :noun
                       :pronoun true}}}
      {:comment-plaintext "vp[pron]"
       :comment "vp[pron]"
       :extend {:f {:head 'lexicon
                    :comp 'lexicon}
                ;; TODO add vp -> lexicon vp also.
                }})))

  (def vp-present
    ;; add to vp some additional expansions for vp-present:
    (fs/merge vp
              {:comment "vp[present] &#x2192; head comp"
               :comment-plaintext "vp[present] -> head comp"
;               :extend {:f {:head 'lexicon
;                            :comp 'vp-past}
               }))

  (def vp-past
    (fs/merge vp
              {:comment "vp[past] &#x2192; head comp"
               :comment-plaintext "vp[past] -> head comp"
               :infl :past}))

  (def vp-infinitive-transitive
    (fs/unifyc head-principle
               subcat-2-principle
               verb-inflection-morphology
               {:head {:synsem {:cat :verb
                                :infl :infinitive
                                :subcat {:2 {:cat :noun
                                             :subcat '()}}}}}
               {:comment "vp[inf] &#x2192; head comp"
                :comment-plaintext "vp[inf] -> head comp"}
               italian-head-first
               english-head-first
               {:extend {
                         :a {:head 'lexicon
                             :comp 'np}}}))))

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
                            :subcat {:1 subcatted
                                     :2 '()}}})

        rule-base
        (fs/unifyc head-principle subcat-1-principle
                   subject-verb-agreement
                   {:comp {:synsem {:subcat '()
                                    :cat :noun}}
                    :head {:synsem {:cat :verb}}}
                   {:extend {:a {:comp 'np
                                 :head 'vp}
                             :b {:comp 'lexicon
                                 :head 'vp}
                             :c {:comp 'lexicon
                                 :head 'vp-pron}
                             :d {:comp 'np
                                 :head 'vp-pron}

                             :e {:comp 'np
                                 :head 'lexicon}
                             :f {:comp 'lexicon
                                 :head 'lexicon}
                             }})]

    (def s-present
      ;; unlike the case for future and imperfetto,
      ;; override the existing :extends in the case of s-present.
      (fs/merge
       (fs/unifyc rule-base
                   italian-head-last
                   english-head-last
                  {:comment "sentence[present]"
                   :comment-plaintext "s[present] -> .."
                   :synsem {:infl :present}})
       {:extend {:g {:comp 'lexicon
                     :head 'vp-present}
                 :h {:comp 'np
                     :head 'vp-present}
                 }}))

    (def s-future
      (fs/unifyc rule-base
                 italian-head-last
                 english-head-last
                 {:comment "sentence[future]"
                  :comment-plaintext "s[future] -> .."
                  :synsem {:infl :futuro}}))

    (def s-imperfetto
      (fs/unifyc rule-base
                 {:comment "sentence[imperfetto]"
                  :synsem {:infl :imperfetto}}))))


(def adj-phrase
  (unify head-principle
         subcat-1-principle
         italian-head-first
         english-head-first
         {:comment "adj-phrase&nbsp;&#x2192;&nbsp;adj&nbsp;+&nbsp;prep-phrase"
          :comment-plaintext "adj-phrase -> adj prep-phrase"}

         ;; TODO: prep-phrase should be {:cat {:not {:nom}}} to avoid "richer than he" (should be "richer than him")

         (let [comparative (ref true)]
           {:synsem {:sem {:comparative comparative}}
            :comp {:synsem {:sem {:comparative comparative}}}
            :head {:synsem {:sem {:comparative comparative}}}})

         {:synsem {:cat :adjective}
          :extend {:a {:head 'lexicon
                       :comp 'prep-phrase}}}))

;; intensifier (e.g. "più") is the head, which subcategorizes
;; for an adjective.
;; the head is the adjective-phrase, not the intensifier,
;; while the intensifier is the complement.
(def intensifier-phrase
  (unify head-principle
         subcat-1-principle
         italian-head-first
         english-head-first ;; not sure about this e.g. "più ricca di Paolo (richer than Paolo)"

         ;; TODO: specify this in lexicon (subcat of head) rather than here in grammar.
         {:head {:synsem {:cat :intensifier}}}

         ;; but for now we use "more=rich" e.g. "più ricca di Paolo (more rich than Paolo)"
         {:comment "intensifier-phrase&nbsp;&#x2192;&nbsp;intensifier&nbsp;+&nbsp;adj-phrase"
          :comment-plaintext "intensifier-phrase -> intensifier adj-phrase"
          :extend {:a {:comp 'adj-phrase
                       :head 'lexicon}}}))

(def nbar
  (let [head-semantics (ref :top)
        adjectival-predicate (ref :top)
        agr (ref :top)
        subcat (ref :top)]
    (unify head-principle
           ;; for Nbar, italian and english have opposite constituent order:
           italian-head-first
           english-head-last
           (let [def (ref :top)]
             {:head {:synsem {:def def}}
              :synsem {:def def}})
           {:synsem {:sem head-semantics}
            :comp {:synsem {:sem {:mod head-semantics}}}}
           ;; the following will rule out pronouns, since they don't subcat for a determiner;
           ;; (in fact, they don't subcat for anything)
           {:synsem {:subcat {:1 {:cat :det}}}}

           {:synsem {:agr agr
                     :subcat subcat}
            :head {:synsem {:agr agr
                            :cat :noun
                            :subcat subcat}}
            :comp {:synsem {:cat :adjective}
                   :italian {:agr agr}
                   :english {:agr agr}}}

           {:synsem {:sem {:mod adjectival-predicate}}
            :comp {:synsem {:sem {:mod head-semantics
                                  :pred adjectival-predicate}}}}
           {:comment "n&#x0305; &#x2192; noun adj"
            :comment-plaintext "nbar -> noun adj"
            :extend {:a {:head 'lexicon
                         :comp 'lexicon}}})))

(def np-rules
  (let [head (ref :top)
        comp (ref :top)]
    (def np
      (let [head-english (ref :top)
            head-italian (ref :top)
            comp-english (ref :top)
            comp-italian (ref :top)]
        (fs/unifyc head-principle subcat-1-principle ;; NP -> Comp Head
                   (let [agr (ref :top)]
                     (fs/unifyc
                      (let [def (ref :top)]
                        {:head {:synsem {:def def}}
                         :synsem {:def def}
                         :comp {:synsem {:def def}}})
                      {:head {:italian head-italian
                              :english head-english
                              :synsem {:cat :noun
                                       :agr agr}}
                       :comp {:synsem {:cat :det}
                              :italian comp-italian
                              :english comp-english}
                       :synsem {:agr agr}
                       :comment "np &#x2192; det (noun or nbar)"
                       :comment-plaintext "np -> det (noun or nbar)"

                       ;; for NP, italian and english have same constituent order:
                       :italian {:a comp-italian
                                 :b head-italian}
                       :english {:a comp-english
                                 :b head-english}
                       :extend {
                                :a {:comp 'lexicon
                                    :head 'lexicon}
                                :b {:comp 'lexicon
                                    :head 'nbar}
                                }
                   })))))
    (list np)))

(def prep-phrase
  (let [comparative (ref :top)
        head (ref {:synsem {:cat :prep
                            :sem {:comparative comparative}}})
        comp (ref {:synsem {:cat :noun
                            :subcat '()}})]
    (fs/unifyc head-principle
               subcat-1-principle
               {
                :comment "pp &#x2192; prep (np or propernoun)"
                :comment-plaintext "pp -> prep (np or proper noun)"}
               {:head head
                :comp comp
                :synsem {:sem {:comparative comparative}}}
               italian-head-first
               english-head-first
               {:extend {:a {:head 'lexicon
                             :comp 'np}
                         :b {:head 'lexicon
                             :comp 'lexicon}}})))

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
