(ns italianverbs.test.editor
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [italianverbs.borges.writer :refer [populate populate-from]]
   [italianverbs.engine :refer [generate]]
   [italianverbs.editor :refer :all]
   [italianverbs.engine :as engine]
   [italianverbs.english :as en]
   [italianverbs.espanol :as es]
   [italianverbs.italiano :as it]
   [italianverbs.morphology :refer (fo)]
   [italianverbs.korma :as db]
   [dag-unify.core :refer [get-in]]
   [italianverbs.verb :refer [generation-table predicates-from-lexicon]]
   [hiccup.core :refer (html)]
   [korma.core :as k]
))



