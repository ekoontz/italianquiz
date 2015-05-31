;; WARN: modifies (and optionally removes) data in database. Should only run on a test database instance.
;; TODO: add safeguards to prevent running these tests on non-test database instances.
(ns italianverbs.test.borges.writer
  (:refer-clojure :exclude [get-in resolve merge])
  (:require
   [clojure.core :as core]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]
   [italianverbs.borges.reader :refer :all :exclude [generate get-meaning]]
   [italianverbs.borges.writer :refer :all :exclude [routes]]
   [italianverbs.engine :refer :all :as engine]
   [italianverbs.english :as en :refer [en]]
   [italianverbs.espanol :as es :refer [es]]
   [italianverbs.italiano :as it :refer [it]]
   [italianverbs.lexiconfn :refer [infinitives]]
   [italianverbs.morphology :refer :all]
   [italianverbs.morphology.espanol :as esm]
   [italianverbs.unify :refer [get-in serialize strip-refs unify]]
   ))

(def spec {:synsem {:essere true}})

(def enrich-function (:enrich @it/small))

(def try (enrich-function {:synsem {:essere true}}))

(def matching-head-lexemes (it/matching-head-lexemes spec))

(def spanish-sentence
  (fo (engine/generate {:synsem {:infl :present 
                                 :sem {:aspect :progressive}}} @es/small)))

(deftest spanish-working
  (is (not (nil? spanish-sentence)))
  (is (not (= "" spanish-sentence))))

(deftest populate-test
  (let [do-populate
        (populate 1 en/small es/small {:synsem {:infl :present :sem {:aspect :progressive}}})]
    (is true))) ;; TODO: add test

(deftest spanish-subject-agreement
  (let [vosotros-comeis (let [example (engine/generate {:synsem {:sem {:subj {:gender :masc :pred :voi} :tense :present :pred :mangiare}}} @es/small)] 
                          {:sem (strip-refs (get-in example [:synsem :sem :subj :gender] :unspecified)) 
                           :surface (fo example)})]
    (is 
     (or 
      (= (:surface vosotros-comeis) "ustedes comen")
      (= (:surface vosotros-comeis) "vosotros comeis")))))


(deftest do-populate
  (let [do-populate (populate 1 en/small es/small {:synsem {:sem {:pred :speak}}})]
    (is (= 1 1)))) ;; stub TODO: fill out test

(defn prep [this-many & [:truncate :false]]
  (if (= truncate true)
    (truncate))
  (populate this-many en/small it/small :top)
  (populate this-many en/small es/small :top))

(deftest do-prep
  (prep 10)
  (is (= 1 1))) ;; stub TODO: fill out test

(deftest reflexive-vp
  (let [expression
        (en/generate {:synsem {:subcat {:1 :top} :sem {:obj {:pred :io} :tense :present :pred :wash}}} 
                     en/small-plus-vp-pronoun)
        ]
    (is (= (fo expression)
           "wash myself"))))

;; test does not work yet due to problem with English generation
(deftest populate-reflexives
  (let [do-populate 
        (populate 1 en/small-plus-vp-pronoun it/small-plus-vp-pronoun 
                  {:synsem {:subcat '()
                            :sem {:tense :present
                                  :pred :wash
                                  :obj {:pred :io}}}})
        ]
    (is (= 1 1)))) ;; stub TODO: fill out test

(deftest insert-into-lexicon
  (let [lexeme {:foo {:bar 42}}]
    ;; insert a single word with canonical form 'foobar' in the lexicon for the Foobar language,
    ;; whose code name name is "fo".
    ;; (Note: 'fo' is not a current language code per: http://www.loc.gov/standards/iso639-2/php/code_list.php)
    (log/debug (str "inserting into lexicon: " lexeme))
    (insert-lexeme "foobar" lexeme "fo")
    (let [lexemes (get-lexeme "foobar" "fo")]
      (log/debug (str "Got this many lexemes for 'foobar' from lexicon: " (.size lexemes)))
      (is (not (empty? lexemes))))))

