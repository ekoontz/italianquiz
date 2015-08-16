(ns italianverbs.lexicon.francais
  (:require
   [italianverbs.lexiconfn :refer (unify)]
   [italianverbs.pos.francais :refer :all]))

(def lexicon-source 
  {
   "abandoner" {:synsem {:cat :verb
                         :sem {:pred :abandon}}}

   "accepter" {:synsem {:cat :verb
                        :sem {:pred :accept}}}

   "accompagner" {:synsem {:cat :verb
                           :sem {:pred :accompany}}}

   "anoncier" {:synsem {:cat :verb
                        :sem {:pred :announce}}}

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

  "soutenir" {:synsem {:cat :verb
                       :sem {:pred :support}}}

  "terminer" {:synsem {:cat :verb
                       :sem {:pred :finish}}}
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

;;  CONJUGATES LIKE TENIR
  "apprendre" {:synsem {:cat :verb
                        :sem {:pred :imparare}}}
  
  "profiter (de)" {:synsem {:cat :verb
                            :sem {:pred :take-advantage-of}}}
  
  "assurer" [{:synsem {:cat :verb
                       :sem {:pred :assure}}}
             {:synsem {:cat :verb
                       :sem {:pred :insure}}}]

  "augmenter" {:synsem {:cat :verb
                        :sem {:pred :increase}}}

  "aider" {:synsem {:cat :verb
                    :sem {:pred :aiutare}}}
  
  "baisser" {:synsem {:cat :verb
                      :sem {:pred :lower}}}
  
  "changer" {:synsem {:cat :verb
                      :sem {:pred :cambiare}}}
  
  "commenter" {:synsem {:cat :verb
                        :sem {:pred :comment}}}
  
  "partager" {:synsem {:cat :verb
                       :sem {:pred :share}}}
  
  "acheter" {:synsem {:cat :verb
                      :sem {:pred :comprare}}}
  
  ;;CONJUGATES LIKE PRENDRE
  "comprendre" {:synsem {:cat :verb
                         :sem {:pred :understand}}}

  "conserver" [{:synsem {:cat :verb
                         :sem {:pred :conserve}}}
               {:synsem {:cat :verb
                         :sem {:pred :preserve}}}]
  
  "considérer" {:synsem {:cat :verb
                         :sem {:pred :consider}}}
  
  ;;SIMILAR TO PRENDRE
  "répondre" {:synsem {:cat :verb
                       :sem {:pred :answer}}}
  
  
  "corrir" {:synsem {:cat :verb
                     :sem {:pred :run}}}
  
  ;;  LIKE PRENDRE
  "correspondre" {:synsem {:cat :verb
                           :sem {:pred :correspond}}}
  
  "couper" {:synsem {:cat :verb
                     :sem {:pred :cut}}}
  
  "créer" {:synsem {:cat :verb
                    :sem {:pred :create}}}
  
  ;; TODO: clarify semantics of this in English 
  "cumplir" {:synsem {:cat :verb
                      :sem {:pred :turn-years}}}
                     
  "devoir" {:synsem {:cat :verb
                     :sem {:pred :have-to}}}
  ;; IRREGOLARE
  "decider" {:synsem {:cat :verb
                      :sem {:pred :decide}}}
  
  "laisser" {:synsem {:cat :verb
                      :sem {:pred :leave-behind}}}
  
  "developer" {:synsem {:cat :verb
                        :sem {:pred :develop}}}
  
  "désirer" {:synsem {:cat :verb
                      :sem {:pred :desire}}}
  
  "expulser" {:synsem {:cat :verb
                       :sem {:pred :throw-out}}}
  
  "enseigner" [{:synsem {:cat :verb
                         :sem {:pred :show}}}
               {:synsem {:cat :verb
                         :sem {:pred :teach}}}]

  "entrer" {:synsem {:cat :verb
                     :sem {:pred :enter}}}
  
  "echapper" {:synsem {:cat :verb
                       :sem {:pred :escape}}}

  "écouter" {:synsem {:cat :verb
                      :sem {:pred :listen-to}}}
  
  "attendre" [{:synsem {:cat :verb
                        :sem {:pred :wait-for}}}
              {:synsem {:cat :verb
                        :sem {:pred :hope}}}]
  
  "étudier" {:synsem {:cat :verb
                      :sem {:pred :study}}}
  
  "éviter" {:synsem {:cat :verb
                     :sem {:pred :avoid}}}
  
  "exister" {:synsem {:cat :verb
                      :sem {:pred :exist}}}
  
  "exprimer" {:synsem {:cat :verb
                       :sem {:pred :express}}}
  
  "manquer" {:synsem {:cat :verb
                      :sem {:pred :to-be-missing}}}
  
  "former" {:synsem {:cat :verb
                     :sem {:pred :form}}}
  
  "marcher" {:synsem {:cat :verb
                      :sem {:pred :work-nonhuman}}}
  
  "gagner" [{:synsem {:cat :verb
                      :sem {:pred :earn}}}
            {:synsem {:cat :verb
                      :sem {:pred :win}}}]

  "imaginer" {:synsem {:cat :verb
                       :sem {:pred :imagine??}}}
  "importer" {:synsem {:cat :verb
                       :sem {:pred :import??}}}
  "commencer" {:synsem {:cat :verb
                        :sem {:pred :begin??}}}
  "insister" {:synsem {:cat :verb
                       :sem {:pred :insist??}}}
  "essayer" {:synsem {:cat :verb
                      :sem {:pred :try??}}}
  "interessér" {:synsem {:cat :verb
                         :sem {:pred :interest??}}}
  "soulever" {:synsem {:cat :verb
                       :sem {:pred :lift??}}}
  "appeler" {:synsem {:cat :verb
                      :sem {:pred :call??}}}
  "apporter" {:synsem {:cat :verb
                       :sem {:pred :take/carry??}}}
  "gérer" {:synsem {:cat :verb
                    :sem {:pred :manage??}}}
  "envoyer" {:synsem {:cat :verb
                      :sem {:pred :send??}}}
  "touer" {:synsem {:cat :verb
                    :sem {:pred :??}}}
  "mettre" {:synsem {:cat :verb
                     :sem {:pred :??}}}
  "regarder" {:synsem {:cat :verb
                       :sem {:pred :look/watch??}}}
  
  ;;ELIMINATE NECESITAR BECAUSE AVOIR BESOIN DE IS A SPECIAL EXPRESSION
  "necesitar" {:synsem {:cat :verb
                        :sem {:pred :??}}}
  "remarquer" {:synsem {:cat :verb
                                       :sem {:pred :note??}}}
  "observer" {:synsem {:cat :verb
                       :sem {:pred :observe??}}}
  
  
  "oublier" {:synsem {:cat :verb
                      :sem {:pred :forget??}}}
  "participer" {:synsem {:cat :verb
                         :sem {:pred :participate??}}}
  "diviser" {:synsem {:cat :verb
                      :sem {:pred :divide??}}}
   
   })






