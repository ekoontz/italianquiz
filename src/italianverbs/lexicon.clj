(ns italianverbs.lexicon
  (:use [italianverbs.lexiconfn]
        [italianverbs.rdutest])
  (:require [italianverbs.fs :as fs]))

;; WARNING: clear blows away entire lexicon in backing store (mongodb).
(clear)

(let [verb {:cat :verb}
      human-subj {:subj {:human true}}
      third-sing {:subj {:number :singular :person :3rd}}
      parlare (add "parlare" "to speak"
                   (fs/merge-like-core
                    verb
                    human-subj
                    {
                     :obj {:speakable true}
                     }))
      merge (fs/merge-like-core
             parlare
             third-sing
             {:root parlare})
          parla (add "parla" "speaks"
                     (fs/merge-like-core
                      parlare
                      third-sing
                      {:root parlare}))]
  )

(def localtests ;; so as not to collide with lexiconfn/tests.
  {:parla
   (rdutest
    "A lexical entry for the word: 'parlare'."
    (lookup "parlare")
    (fn [parlare]
      (= (:italian parlare) "parlare"))
    :parla)})



