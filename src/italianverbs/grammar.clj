(ns italianverbs.grammar
  (:refer-clojure :exclude [get-in])

  (:require [clojure.tools.logging :as log]
            [italianverbs.forest :as forest]
            [italianverbs.lexicon :refer (it)]
            [italianverbs.lexiconfn :refer :all]
            [italianverbs.pos :refer :all]
            [italianverbs.ug :refer :all]
            [italianverbs.unify :refer (get-in unifyc)]
            [clojure.string :as string]))

(log/info "begin italian-english specific lexical categories..")

;; TODO: s/unifyc/unify/

(def grammar (list (unifyc hh21
                           {:rule "adjective-phrase"
                            :synsem {:cat :adjective}})

                   (unifyc hh21
                           (let [head-synsem {:cat :intensifier
                                              :modified true}]
                             {:rule "intensifier-phrase"
                              :synsem head-synsem}))

                   (unifyc hc11-comp-subcat-1
                           (let [head-synsem {:cat :noun
                                              :modified true}]
                             {:comp {:phrasal false ;; rathole prevention
                                     :synsem {:cat :adjective
                                              :mod head-synsem}}
                              :head {:synsem {:modified false}}
                              :rule "nbar"
                              :synsem head-synsem}))

                   (unifyc cc10
                           {:rule "noun-phrase"
                            :synsem {:cat :noun}
                            :comp {:phrasal false}}) ;; rathole prevention

                   (unifyc hh10
                           {:rule "prepositional-phrase"
                            :synsem {:cat :prep}})

                   (unifyc cc10
                           {:head {:synsem {:aux true}}
                            :rule "s-aux"
                            :synsem {:infl :present
                                     :cat :verb
                                     :sem {:tense :past}}})

                   (unifyc cc10
                          {:rule "s-future"
                           :synsem {:aux false
                                    :infl :futuro
                                    :cat :verb
                                    :sem {:tense :future}}})


                   (unifyc cc10
                           {:rule "s-imperfetto"
                            :synsem {:aux false
                                     :infl :imperfetto
                                     :cat :verb
                                     :sem {:tense :past}}})

                   (unifyc cc10
                           {:rule "s-present"
                            :synsem {:aux false
                                     :infl :present
                                     :cat :verb
                                     :sem {:tense :present}}})

                   (unifyc hh21
                           {:rule "vp-infinitive"
                            :synsem {:aux false
                                     :infl :infinitive
                                     :cat :verb}})

                   (unifyc hh21
                           {:rule "vp-aux"
                            :synsem {:aux true
                                     :infl :present
                                     :sem {:tense :past}
                                     :cat :verb}})

                   ;; this rule is kind of complicated and made more so by
                   ;; dependence on auxilary sense of "avere" which supplies the
                   ;; obj-agr agreement between the object and the main (non-auxilary) verb.
                   (unifyc hh22
                           (let [obj-agr (ref :top)]
                             {:rule "vp-aux-22"
                              :synsem {:aux true
                                       :infl :present
                                       :sem {:tense :past}
                                       :subcat {:2 {:agr obj-agr}}
                                       :cat :verb}
                              :italian {:b {:obj-agr obj-agr}}}))

                   (unifyc hh21
                           {:rule "vp-future"
                            :synsem {:aux false
                                     :infl :futuro
                                     :cat :verb}})

                   (unifyc hh21
                          {:rule "vp-imperfetto"
                           :synsem {:aux false
                                    :infl :imperfetto
                                    :cat :verb}})

                   (unifyc hh21
                           {:rule "vp-past"
                            :synsem {:aux false
                                     :infl :past
                                     :cat :verb}})

                   (unifyc hh21
                           {:rule "vp-present"
                            :synsem {:aux false
                                     :infl :present
                                     :sem {:tense :present}
                                     :cat :verb}})

                   (unifyc ch21
                           {:comp {:synsem {:cat :noun
                                            :pronoun true}}
                            :rule "vp-pronoun"
                            :synsem {:cat :verb
                                     :infl {:not :past}}})))


(defn aux-is-head-feature [phrase]
  (cond (= :verb (get-in phrase '(:synsem :cat)))
        (unifyc phrase
                (let [ref (ref :top)]
                  {:synsem {:aux ref}
                   :head {:synsem {:aux ref}}}))
        true phrase))

(def grammar
  (map (fn [phrase]
         (aux-is-head-feature phrase))
       grammar))

;; This allows us to refer to individual grammar rules within grammar
;; by symbols like "vp-present" (e.g. (over vp-present lexicon)).
;; TODO: calling (.size) because (map) is lazy, and I want to realize
;; the sequence - must be a better way to loop over the grammar and realize the result.
(.size (map (fn [rule]
       (intern *ns* (symbol (:rule rule)) rule))
     grammar))

