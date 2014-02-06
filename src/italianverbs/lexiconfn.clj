(ns italianverbs.lexiconfn
  (:refer-clojure :exclude [get-in merge resolve find])
  (:use [clojure.set])
  (:require
   [clojure.set :as set]
   [clojure.tools.logging :as log]
   [clojure.core :as core]
   [italianverbs.morphology :as morph]
   ;; We redefine unify here: TODO: just use unifyc where appropriate.
   [italianverbs.unify :as unify :exclude (unify)]))

(defn unify [ & args]
  "like unify/unify, but unify/copy each argument before unifying."
  (do
    (log/debug (str "(lexfn)unify args: " args))
    (log/debug (str "(lexfn)unify first arg: " (first args)))
    (apply unify/unifyc args)))

(defn encode-where-query [& where]
  "encode a query as a set of index queries."
  where)

(defn italian [lexeme]
  (get (nth lexeme 1) :lexicon))

(defn synsem [lexeme]
  (nth lexeme 1))

(defn english [lexeme]
  (get (nth lexeme 1) :english))

(def firstp
  {:person :1st})
(def secondp
  {:person :2nd})
(def thirdp
  {:person :3rd})
(def sing
  {:number :singular})
(def plural
  {:number :plural})
(def present
  {:cat :verb
   :infl :present})

;; TODO: move to morphology
(defn italian-pluralize [singular gender]
  (cond
   (= gender :masc)
   (replace #"([oe])$" "i" singular)
   (= gender :fem)
   (replace #"([a])$" "e" singular)))

;; TODO move to morphology
(defn english-pluralize [singular]
  (str (replace #"([sxz])$" "$1e" singular) "s"))

;; The following are defs and defns used specifically by lexicon.clj.
;; we put them here in lexiconfn to avoid cluttering lexicon file so that
;; the latter is easier to edit by humans.
;;
;;
;;
;; useful abbreviations (aliases for some commonly-used maps):
(def human {:human true})
(def animal {:animate true :human false})

(defn sem-impl [input]
  "expand input feature structures with semantic (really cultural) implicatures, e.g., if human, then not buyable or edible"
  (cond
   (= input :top) input
   true
   (let [activity (if (= (unify/get-in input '(:activity))
                         true)
                    {:animate false
                     :artifact false
                     :consumable false
                     :part-of-human-body false})
         animate (if (= (unify/get-in input '(:animate))
                        true)
                   {:activity false
                    :artifact false
                    :mass false
                    :furniture false
                    :physical-object true
                    :part-of-human-body false
                    :drinkable false
                    :speakable false
                    :place false}{})
         artifact (if (= (unify/get-in input '(:artifact))
                         true)
                    {:animate false
                     :activity false
                     :physical-object true}{})

         buyable (if (= (unify/get-in input '(:buyable))
                        true)
                   {:human false
                    :part-of-human-body false})

         city (if (= (unify/get-in input '(:city))
                     true)
                {:place true
                 :human false
                 :animate false
                 :legible false})

         clothing (if (= (unify/get-in input '(:clothing))
                         true)
                    {:animate false
                     :place false
                     :physical-object true}{})


         consumable (if (= (unify/get-in input '(:consumable)) true)
                      {:activity false
                       :buyable true
                       :furniture false
                       :legible false
                       :pet false
                       :physical-object true
                       :speakable false})

         consumable-false (if (= (unify/get-in input '(:consumable)) false)
                            {:drinkable false
                             :edible false} {})

         drinkable
         ;; drinkables are always mass nouns.
         (if (= (unify/get-in input '(:drinkable)) true)
           {:mass true})

         drinkable-xor-edible-1
         ;; things are either drinkable or edible, but not both (except for weird foods
         ;; like pudding or soup). (part 1: edible)
         (if (and (= (unify/get-in input '(:edible)) true)
                  (= (unify/get-in input '(:drinkable) :notfound) :notfound))
           {:drinkable false}{})

         drinkable-xor-edible-2
         ;; things are either drinkable or edible, but not both (except for weird foods
         ;; like pudding or soup). (part 2: drinkable)
         (if (and (= (unify/get-in input '(:drinkable)) true)
                  (= (unify/get-in input '(:edible) :notfound) :notfound))
           {:edible false})

         ;; qualities of foods and drinks.
         edible (if (or (= (unify/get-in input '(:edible)) true)
                        (= (unify/get-in input '(:drinkable)) true))
                  {:consumable true
                   :human false
                   :pet false
                   :place false
                   :speakable false
                   :legible false
                   :furniture false
                   :part-of-human-body false}{})

         furniture (if (= (unify/get-in input '(:furniture))
                          true)
                     {:artifact true
                      :animate false
                      :buyable true
                      :drinkable false
                      :legible false
                      :edible false
                      :place false
                      :speakable false})

         human (if (= (unify/get-in input '(:human))
                      true)
                 {:activity false
                  :buyable false
                  :physical-object true
                  :edible false
                  :animate true
                  :part-of-human-body false
                  :drinkable false
                  :speakable false
                  :place false}{})
         inanimate (if (= (unify/get-in input '(:animate))
                           false)
                     {:human false
                      :part-of-human-body false}{})

         ;; legible(x) => artifact(x),drinkable(x,false),edible(x,false),human(x,false)
         legible
         (if (= (unify/get-in input '(:legible)) true)
           {:artifact true
            :drinkable false
            :human false
            :furniture false
            :part-of-human-body false
            :edible false})

         material-false
         (if (= (unify/get-in input '(:material)) :false)
           {:edible false
            :animate false
            :drinkable false
            :buyable false ; money can't buy me love..
            :visible false})

         non-places (if (or
                         (= (unify/get-in input '(:legible)) true)
                         (= (unify/get-in input '(:part-of-human-body)) true)
                         (= (unify/get-in input '(:pred)) :fiore)
                         (= (unify/get-in input '(:pred)) :scala))
                   {:place false})

         ;; artifact(x,false) => legible(x,false)
         not-legible-if-not-artifact
         (if (= (unify/get-in input '(:artifact)) false)
           {:legible false})

         part-of-human-body
         (if (= (unify/get-in input '(:part-of-human-body)) true)
           {:speakable false
            :buyable false
            :animate false
            :edible false
            :drinkable false
            :legible false
            :artifact false})

         ;; we don't eat pets (unless things get so desperate that they aren't pets anymore)
         pets (if (= (unify/get-in input '(:pet))
                     true)
                {:edible false
                 :buyable true
                 :physical-object true
                 })

         place (if (= (unify/get-in input '(:place))
                      true)
                 {:activity false
                  :animate false
                  :speakable false
                  :physical-object true
                  :drinkable false
                  :edible false
                  :legible false}{})

         ]
     (let [merged
           (if (= input :fail) :fail
               ;; don't need the features of unify/merge (at least not yet), so use core/merge.
               (core/merge input animate artifact buyable city clothing consumable consumable-false drinkable
                           drinkable-xor-edible-1 drinkable-xor-edible-2
                           edible furniture human inanimate
                           legible material-false non-places
                           not-legible-if-not-artifact part-of-human-body pets place
                      ))]
       (log/debug (str "sem-impl so far: " merged))
       (if (not (= merged input)) ;; TODO: make this check more efficient: count how many rules were hit
         ;; rather than equality-check to see if merged has changed.
         (sem-impl merged) ;; we've added some new information: more implications possible from that.
         merged))))) ;; no more implications: return

(def modal
  "modal verbs take a VP[inf] as their 2nd arg. the subject of the modal verb is the same as the subject of the VP[inf]"
  (let [subj-sem (ref :top)
        vp-inf-sem (ref {:subj subj-sem})
        subj-subcat (ref {:cat :noun
                          :sem subj-sem})]
     {:synsem {:sem {:subj subj-sem
                     :obj vp-inf-sem}
               :subcat {:1 subj-subcat
                        :2 {:sem vp-inf-sem
                            :cat :verb
                            :infl :infinitive
                            :subcat {:1 subj-subcat
                                     :2 '()}}}}
      :english {:modal true}}))

;; A generalization of intransitive and transitive:
;; they both have a subject, thus "subjective".
(def verb-subjective
  (let [subj-sem (ref :top)
        subject-agreement (ref {:case :nom})
        infl (ref :top)
        essere-type (ref :top)]
    {:italian {:agr subject-agreement :infl infl :essere essere-type}
     :english {:agr subject-agreement :infl infl}
     :synsem {:essere essere-type
              :infl infl
              :cat :verb
              :sem {:subj subj-sem}
              :subcat {:1 {:sem subj-sem
                           :cat :noun
                           :agr subject-agreement}}}}))

;; intransitive: has subject but no object.
(def intransitive
  (unify verb-subjective
         {:synsem {:subcat {:2 '()}}}))

;; transitive: has both subject and object.
(def transitive
  (unify verb-subjective
         (let [obj-sem (ref :top)
               infl (ref :top)]
           {:english {:infl infl}
            :italian {:infl infl}
            :synsem {:sem {:obj obj-sem}
                     :infl infl
                     :subcat {:2 {:sem obj-sem
                                  :subcat '()
                                  :cat :noun
                                  :agr {:case :acc}}}}})))

(def transitive-but-with-adjective-instead-of-noun
  (unify verb-subjective
         (let [obj-sem (ref :top)
               infl (ref :top)]
           {:english {:infl infl}
            :italian {:infl infl}
            :synsem {:sem {:obj obj-sem}
                     :cat :verb
                     :infl infl
                     :subcat {:2 {:sem obj-sem
                                  :subcat '()
                                  :cat :adjective}
                              :3 '()}}})))

(def transitive-but-with-intensifier-instead-of-noun
  (unify verb-subjective
         (let [obj-sem (ref :top)
               infl (ref :top)]
           {:english {:infl infl}
            :italian {:infl infl}
            :synsem {:sem {:obj obj-sem}
                     :infl infl
                     :subcat {:2 {:sem obj-sem
                                  :subcat '()
                                  :cat :intensifier}}}})))

(def transitive-but-with-prepositional-phrase-instead-of-noun
  (unify verb-subjective
         (let [obj-sem (ref :top)
               infl (ref :top)]
           {:english {:infl infl}
            :italian {:infl infl}
            :synsem {:sem {:obj obj-sem}
                     :infl infl
                     :subcat {:2 {:sem obj-sem
                                  :subcat '()
                                  :cat :prep}
                              :3 '()}}})))

;; whether a verb has essere or avere as its
;; auxiliary to form its passato-prossimo form:
;; Must be encoded in both the :italian (for morphological agreement)
;; and the :synsem (for subcategorization by the appropriate aux verb).
(def verb-aux-type
  (let [essere-binary-categorization (ref :top)
        aux (ref true)
        sem (ref {:tense :past})
        subject (ref :top)]
    {:italian {:aux aux
               :essere essere-binary-categorization}
     :synsem {:aux aux
              :sem sem
              :essere essere-binary-categorization
              :subcat {:1 subject
                       :2 {:cat :verb
                           :subcat {:1 subject
                                    :2 '()}
                           :sem sem
                           :infl :past}}}}))

(def verb-aux-type-2
  (let [essere-binary-categorization (ref :top)
        aux (ref true)
        sem (ref {:tense :past})
        subject (ref :top)
        object (ref :top)]
    {:italian {:aux aux
               :essere essere-binary-categorization}
     :synsem {:aux aux
              :sem sem
              :essere essere-binary-categorization
              :subcat {:1 subject
                       :2 {:cat :verb
                           :subcat {:1 subject
                                    :2 object}
                           :sem sem
                           :infl :past}}}}))

(def subject (ref {:cat :noun}))
(def comp-sem (ref {:activity false
                    :discrete false}))

(def noun
  (let [gender (ref :top)
        ;; common nouns are underspecified for number: number selection (:sing or :plur) is deferred until later.
        ;; (except for mass nouns which are only singular)
        number (ref :top)
        ;; common nouns are neither nominative or accusative. setting their case to :top allows them to (match) with
        ;; verbs' case specifications like {:case {:not :acc}} or {:case {:not :nom}}.
        case (ref :top)
        person (ref :top)
        agreement
        (let [number (ref :top)
              gender (ref :top)
              person (ref :top)
              agr (ref {:number number
                        :gender gender
                        :case :top
                        :person person})
              cat (ref :top)]
          {:synsem {:cat cat
                    :subcat {:1 {:number number
                                 :person person
                                 :gender gender}}
                    :agr agr}
           :italian {:cat cat
                     :agr agr}
           :english {:cat cat
                     :agr agr}})
        common
        {:synsem {:cat :noun
                  :agr {:person :3rd}
                  :subcat {:1 {:cat :det}
                           :2 '()}}}

        masculine {:synsem {:agr {:gender :masc}}}
        feminine {:synsem {:agr {:gender :fem}}}

        mass
        (let [mass (ref true)]
          {:synsem {:subcat {:1 {:cat :det
                                 :mass mass
                                 :number :sing}}
                    :sem {:mass mass}}})

        countable
        (let [mass (ref false)]
          {:synsem {:subcat {:1 {:cat :det
                                 :mass mass}}
                    :sem {:mass mass}}})

        drinkable
        (unify mass
               common
               {:synsem {:sem {:number :sing
                               :drinkable true}}})]
    {:agreement agreement
     :common common
     :countable countable
     :drinkable drinkable
     :feminine feminine
     :masculine masculine}))

(def proper-noun
  {:synsem {:cat :noun
            :pronoun false
            :propernoun true
            :agr {:person :3rd}
            :subcat '()}})

(def adjective
  (let [adjective (ref :adjective)
        gender (ref :top)
        number (ref :top)]
    {:synsem {:cat adjective
              :agr {:gender gender
                    :number number}
              }
     :italian {:cat adjective
               :agr {:number number
                     :gender gender}}
     :english {:cat adjective}}))

;; noun convenience variables:
(def agreement-noun (:agreement noun))
(def common-noun (:common noun))
(def countable-noun (:countable noun))
(def drinkable-noun (:drinkable noun))
(def feminine-noun (:feminine noun))
(def masculine-noun (:masculine noun))

(def pronoun-acc (ref :acc))
(def pronoun-noun (ref :noun))
(def verb {:transitive transitive})
(def disjunctive-case-of-pronoun (ref :disj))
(def cat-of-pronoun (ref :noun))

(def subcat0 {:synsem {:subcat '()}})

(defn implied [map]
  "things to be added to lexical entries based on what's implied about them in order to canonicalize them."
  ;; for example, if a lexical entry is a noun with no :number value, or
  ;; the :number value equal to :top, then set it to :singular, because
  ;; a noun is canonically singular.
  ;; TODO: remove this first test: probably doesn't match anything
  ;; - should be (unify/get-in map '(:synsem :cat)), not (:cat map).
  (let [map
        (if (or (= (unify/get-in map '(:synsem :cat)) :det)
                (= (unify/get-in map '(:synsem :cat)) :adverb))
          (unify
           subcat0
           map)
          map)

        map
        (if (and (= (unify/get-in map '(:synsem :cat)) :adjective)
                 (not (= (unify/get-in map '(:synsem :sem :comparative)) true)))
          (unify
           subcat0
           map)
          map)

        map
        (if (= (unify/get-in map '(:synsem :cat)) :sent-modifier)
          (unify
           {:synsem {:subcat {:1 {:cat :verb
                                  :subcat '()}
                              :2 '()}}}
           map)
          map)

        ;; in italian, prepositions are always initial (might not need this)
        map
        (if (= (unify/get-in map '(:synsem :cat)) :prep)
          map map)
;          (let [italian (unify/get-in map '(:italian))]
;            (if (string? italian)
;              (merge map
;                     {:italian {:italian italian
;                                :initial true}})
;              (unify map
;                     {:italian italian}
;                     {:italian {:initial true}})))
;          map)
        ]
    map))

(def sentential-adverb
  (let [sentential-sem (ref :top)]
    {:synsem {:cat :sent-modifier
              :sem {:subj sentential-sem}
              :subcat {:1 {:sem sentential-sem
                           :subcat '()}}}}))

(def andare-common
   {:italian {:infinitive "andare"
              :essere true
              :irregular {:present {:1sing "vado"
                                    :2sing "vai"
                                    :3sing "va"
                                    :1plur "andiamo"
                                    :2plur "andate"
                                    :3plur "vanno"}
                          :futuro {:1sing "andrò"
                                   :2sing "andrai"
                                   :3sing "andrà"
                                   :1plur "andremo"
                                   :2plur "andrete"
                                   :3plur "andranno"}}}
    :english {:infinitive "to go"
              :irregular {:past "went"
                          :past-participle "gone"}}
    :synsem {:essere true
             :sem {:subj {:animate true}
                   :activity false ;; because "I was going when (something happened) .." sounds weird.
                   :pred :andare
                   :discrete false
                   :motion false}}})

(def avere-common
  {:synsem {:essere false
            :cat :verb}
   :italian {:infinitive "avere"
             :irregular {:passato "avuto"
                         :present {:1sing "ho"
                                   :2sing "hai"
                                   :3sing "ha"
                                   :1plur "abbiamo"
                                   :2plur "avete"
                                   :3plur "hanno"}}}
   :english {:infinitive "to have"
             :irregular {:past "had"
                         :present {:1sing "have"
                                   :2sing "have"
                                   :3sing "has"
                                   :1plur "have"
                                   :2plur "have"
                                   :3plur "have"}}}})

(def essere-common
  {:synsem {:essere true}
   :italian {:infinitive "essere"
             :essere true
             :irregular {:present {:1sing "sono"
                                   :2sing "sei"
                                   :3sing "è"
                                   :1plur "siamo"
                                   :2plur "siete"
                                   :3plur "sono"}
                         :passato "stato"
                         :imperfetto {:1sing "ero"
                                      :2sing "eri"
                                      :3sing "era"
                                      :1plur "eravamo"
                                      :2plur "eravate"
                                      :3plur "erano"}
                         :futuro {:1sing "sarò"
                                  :2sing "sarai"
                                  :3sing "sarà"
                                  :1plur "saremo"
                                  :2plur "sarete"
                                  :3plur "saranno"}}}
   :english {:infinitive "to be"
             :irregular {:present {:1sing "am"
                                   :2sing "are"
                                   :3sing "is"
                                   :1plur "are"
                                    :2plur "are"
                                   :3plur "are"}
                         :past {:participle "been"
                                :1sing "was"
                                :2sing "were"
                                :3sing "was"
                                :1plur "were"
                                :2plur "were"
                                :3plur "were"}}}})

(def fare-common
  ;; factor out common stuff from all senses of "fare".
  {:synsem {:essere false}
   :italian {:infinitive "fare"
             :irregular {:passato "fatto"
                         :present {:1sing "facio"
                                   :2sing "fai"
                                   :3sing "fa"
                                   :1plur "facciamo"
                                   :2plur "fate"
                                   :3plur "fanno"}
                         :imperfetto {:1sing "facevo"
                                      :2sing "facevi"
                                      :3sing "faceva"
                                      :1plur "facevamo"
                                      :2plur "facevate"
                                      :3plur "facevano"}
                         :futuro {:1sing "farò"
                                  :2sing "farai"
                                  :3sing "farà"
                                  :1plur "faremo"
                                  :2plur "farete"
                                  :3plur "faranno"}}}})

(def venire-common
  {:italian {:infinitive "venire"
             :irregular {:passato "venuto"
                         :futuro  {:1sing "verrò"
                                   :2sing "verrai"
                                   :3sing "verrà"
                                   :1plur "verremo"
                                   :2plur "verrete"
                                   :3plur "verranno"}
                         :present {:1sing "vengo"
                                   :2sing "vieni"
                                   :3sing "viene"
                                   :1plur "veniamo"
                                   :2plur "venete"
                                   :3plur "vengono"}}}
   :english {:infinitive "to come"
             :irregular {:past "came"}}})

