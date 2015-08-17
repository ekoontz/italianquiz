(ns italianverbs.test.editor
  (:refer-clojure :exclude [get-in])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [dag-unify.core :refer [get-in]]
   [italianverbs.editor :refer :all]
   [hiccup.core :refer (html)]
   [korma.core :as k]
))



