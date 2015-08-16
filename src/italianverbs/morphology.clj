;; TODO: remove all dependencies on particular languages (english,espanol,etc)
(ns italianverbs.morphology
  (:refer-clojure :exclude [get get-in merge resolve]))

(require '[clojure.core :as core])
(require '[clojure.string :as string])
(require '[clojure.tools.logging :as log])
(require '[italianverbs.morphology.english :as english])
(require '[italianverbs.morphology.espanol :as espanol])
(require '[italianverbs.morphology.italiano :as italiano])
(require '[italianverbs.stringutils :refer :all])
(require '[dag-unify.core :refer :all])

(defn phrase-is-finished? [phrase]
  (cond
   (string? phrase) true
   (map? phrase)
   (or (phrase-is-finished? (get-in phrase '(:italiano)))
       (string? (get-in phrase '(:infinitive)))
       (and (phrase-is-finished? (get-in phrase '(:a)))
            (phrase-is-finished? (get-in phrase '(:b)))))
   :else false))

(defn normalize-whitespace [input]
  (do
    (log/warn (str "fix this stubbed out function."))
    input))

(defn get-italian-1 [input]
  (do
    (log/warn (str "fix this stubbed out function."))
    input))

(defn get-english-1 [input]
  (do
    (log/warn (str "fix this stubbed out function."))
    input))

(defn remove-parens [str]
  (string/replace str #"\(.*\)" ""))
