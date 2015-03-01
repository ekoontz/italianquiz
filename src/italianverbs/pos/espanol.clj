(ns italianverbs.pos.espanol)

(require '[italianverbs.pos :as pos])
(require '[italianverbs.unify :refer (unifyc)])


(def noun-agreement
  (let [agr (ref :top)]
    {:espanol {:agr agr}
     :synsem {:agr agr}}))

(def feminine-noun (unifyc
                    noun-agreement (:feminine pos/noun)))

(def masculine-noun (unifyc
                     noun-agreement (:masculine pos/noun)))

(def adjective
  (unifyc pos/adjective
          (let [agr (ref :top)
                cat (ref :top)]
            {:espanol {:agr agr
                        :cat cat}
             :synsem {:agr agr
                      :cat cat}})))

;; A generalization of intransitive and transitive:
;; they both have a subject, thus "subjective".
(def verb-subjective
  (unifyc pos/verb-subjective
          (let [infl (ref :top)
                agr (ref :top)
                essere-type (ref :top)]
            {:espanol {:agr agr
                        :essere essere-type
                        :infl infl}
             :synsem {:infl infl
                      :essere essere-type
                      :subcat {:1 {:agr agr}}}})))

(def transitive
  (unifyc verb-subjective
          pos/transitive
          {:synsem {:essere false}}))

(def intransitive-unspecified-obj
  (unifyc verb-subjective
          pos/intransitive-unspecified-obj))

(def intransitive
  (unifyc verb-subjective
          pos/intransitive))
