(ns italianverbs.test.tour
  (:refer-clojure :exclude [get-in])
  (:require
   [babel.italiano :as it]
   [dag-unify.core :refer (fail? get-in strip-refs unify)]
   [clojure.data.json :as json]
   [clojure.test :refer :all]
   [italianverbs.tour :refer :all]
   [korma.core :as db]))

;; TODO: need a shim to return fixtures (simulated database results)
(deftest tour-generate
  (is (not (= "" "foo"))))

(def killer-spec 
  {:comp {:phrasal false} 
   :head {:phrasal :top} 
   :synsem {:sem {:tense :past 
                  :aspect :perfect 
                  :pred :talk
                  :subj {:mass false 
                         :furniture false 
                         :pred :loro 
                         :place false 
                         :drinkable false 
                         :human true 
                         :animate true 
                         :speakable false 
                         :activity false 
                         :physical-object true 
                         :buyable false 
                         :legible false 
                         :artifact false 
                         :gender :masc 
                         :edible false 
                         :part-of-human-body false} 
                  :obj :unspec} 
            :subcat []
            :cat :verb}})

(deftest enrich-test
  (let [took-time (time (it/generate (it/enrich killer-spec) it/small))]))
