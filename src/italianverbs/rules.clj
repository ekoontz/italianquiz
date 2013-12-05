(ns italianverbs.rules
  (:refer-clojure :exclude [get-in merge resolve])
  (:require [clojure.tools.logging :as log]
            [clojure.core :as core]
            [italianverbs.generate :refer :all]
            [italianverbs.grammar :refer :all]
            [italianverbs.lexicon :refer :all]
            [italianverbs.lexiconfn :refer :all]
            [italianverbs.morphology :refer :all]
            [italianverbs.ug :refer :all]
            [italianverbs.unify :refer :all :exclude [unify]]))

(log/info "started loading rules.")

;; possible expansions of sentence (for now, only declarative sentences):
(ns-unmap 'italianverbs.rules 'declarative-sentence)

;; TODO: translate the keyword ':present' into Italian.
(rewrite-as declarative-sentence
            {:schema 'cc10
             :constraints #{{:synsem {:infl :present
                                      :sem {:tense :present}}}
                            {:synsem {:infl :futuro
                                      :sem {:tense :futuro}}}
                            {:synsem {:infl :imperfetto
                                      :sem {:tense :past}}}}
             :label 'decl-sent
             :comp 'np
             :head 'vp})

(ns-unmap 'italianverbs.rules 'sentence-with-modifier)
(rewrite-as sentence-with-modifier {:schema 'hh10
                                    :label 'sentence-with-modifier-left
                                    :head 'sent-adverbs
                                    :comp 'declarative-sentence})

;; possible expansions of np (noun phrase):
;;
(ns-unmap 'italianverbs.rules 'np)
(rewrite-as np {:schema 'cc10
                :comp 'dets
                :head 'common-nouns})
(rewrite-as np {:schema 'cc10
                :comp 'dets
                :head 'nbar})
(rewrite-as np 'propernouns)
(rewrite-as np 'pronouns)

(ns-unmap 'italianverbs.rules 'nbar)
(rewrite-as nbar {:schema 'hc11
                  :comp 'adjectives
                  :head 'common-nouns})

;; possible expansions of vp (verb phrase):
;;
(ns-unmap 'italianverbs.rules 'vp)
(rewrite-as vp 'intransitive-verbs)
(rewrite-as vp 'transitive-vp)

;; possible expansions of transitive vp (verb phrase):
;;
;; undefine any previous values: TODO: should be a one-liner.
(ns-unmap 'italianverbs.rules 'transitive-vp)
(rewrite-as transitive-vp {:schema 'hh21
                           :comp 'np
                           :head 'transitive-verbs})

;; -- aliases --
(def ds declarative-sentence)

(ns-unmap 'italianverbs.rules 'sents)
(rewrite-as sents 'ds)
;(rewrite-as sents 'sentence-with-modifier)

;; -- useful functions
(defn rules []
  (take 1 (shuffle sents)))

(defn sentence [ & [ with ]]
  (first (take 1 (generate (shuffle sents) "sents" (if with with :top) sem-impl))))

(defn nounphrase [ & [ with ]]
  (first (take 1 (generate (shuffle np) "nps" (if with with :top) sem-impl))))

(log/info "done loading rules.")

