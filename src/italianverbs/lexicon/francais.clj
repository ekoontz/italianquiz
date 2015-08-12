(ns italianverbs.lexicon.francais
  (:require
   [italianverbs.lexiconfn :refer (unify)]
   [italianverbs.pos.francais :refer :all]))

(def lexicon-source 
  {
   "elle"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :3rd
                   :number :sing
                   :gender :fem}
              :sem {:human true
                    :pred :lei}
             :subcat '()}}
   "elles"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :3rd
                   :number :plur
                   :gender :fem}
             :sem {:human true
                   :gender :fem
                   :pred :loro}
             :subcat '()}}
   "il"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :3rd
                   :number :sing
                   :gender :masc}
              :sem {:human true
                    :pred :lui}
             :subcat '()}}
   "ils"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :3rd
                   :number :plur
                   :gender :masc}
             :sem {:human true
                   :gender :masc
                   :pred :loro}
             :subcat '()}}
   "je"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :1st
                   :number :sing}
              :sem {:human true
                    :pred :io}
             :subcat '()}}
   "manger"
   {:synsem {:cat :verb
             :sem {:pred :mangiare}}}
   "nous"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :1st
                   :number :plur}
              :sem {:human true
                    :pred :noi}
             :subcat '()}}
   "parler"
   [{:synsem {:cat :verb
              :sem {:pred :speak}}}
    {:synsem {:cat :verb
              :sem {:pred :talk}}}]
   "tu"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :2nd
                   :number :sing}
              :sem {:human true
                    :pred :tu}
             :subcat '()}}
   "vous"
   {:synsem {:cat :noun
             :pronoun true
             :case :nom
             :agr {:person :2nd
                   :number :plur}
              :sem {:human true
                    :pred :voi}
             :subcat '()}}

   })






