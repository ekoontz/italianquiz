;; TODO: remove all dependencies on particular languages (english,espanol,etc)
(ns italianverbs.morphology
  (:refer-clojure :exclude [get get-in merge resolve]))

(require '[babel.english.morphology :as english])
(require '[babel.italiano.morphology :as italiano])
(require '[clojure.core :as core])
(require '[clojure.string :as string])
(require '[clojure.tools.logging :as log])
(require '[dag-unify.core :refer :all])
(require '[italianverbs.morphology.espanol :as espanol])
(require '[babel.stringutils :refer :all])

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
