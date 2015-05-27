(ns italianverbs.italiano
  (:refer-clojure :exclude [get-in]))

(require '[clojure.string :as string])
(require '[clojure.tools.logging :as log])
(require '[compojure.core :as compojure :refer [GET PUT POST DELETE ANY]])
(require '[hiccup.core :refer (html)])
(require '[italianverbs.auth :refer [is-admin]])
(require '[italianverbs.borges.writer :refer [populate truncate]])
(require '[italianverbs.cache :refer (build-lex-sch-cache create-index over spec-to-phrases)])
(require '[italianverbs.english :as en])
(require '[italianverbs.forest :as forest])
(require '[italianverbs.grammar.italiano :as gram])
(require '[italianverbs.html :as html])
(require '[italianverbs.lexicon.italiano :as lex])
(require '[italianverbs.lexiconfn :refer (compile-lex infinitives map-function-on-map-vals unify)])
(require '[italianverbs.morphology :refer (fo)])
(require '[italianverbs.morphology.italiano :as morph])
(require '[italianverbs.parse :as parse])
(require '[italianverbs.pos.italiano :refer [intransitivize transitivize]])
(require '[italianverbs.ug :refer :all])
(require '[italianverbs.unify :refer (fail? get-in strip-refs)])
(require '[italianverbs.unify :as unify])

(def get-string morph/get-string)
(def grammar gram/grammar)
(def lexicon-source lex/lexicon-source)

;; see TODOs in lexiconfn/compile-lex (should be more of a pipeline as opposed to a argument-position-sensitive function.
(def lexicon (future (-> (compile-lex lex/lexicon-source
                                      morph/exception-generator 
                                      morph/phonize morph/italian-specific-rules)

                         ;; make an intransitive version of every verb which has an
                         ;; [:sem :obj] path.
                         intransitivize
                         
                         ;; if verb does specify a [:sem :obj], then fill it in with subcat info.
                         transitivize
                         
                         ;; Cleanup functions can go here. Number them for ease of reading.
                         ;; 1. this filters out any verbs without an inflection: infinitive verbs should have inflection ':infinitive', 
                         ;; rather than not having any inflection.
                         (map-function-on-map-vals 
                          (fn [k vals]
                            (filter #(or (not (= :verb (get-in % [:synsem :cat])))
                                         (not (= :none (get-in % [:synsem :infl] :none))))
                                    vals))))))

(defn lookup [token]
  "return the subset of lexemes that match this token from the lexicon."
  (morph/analyze token #(get @lexicon %)))

(def it lookup) ;; abbreviation for the above

(defn parse [string]
  (parse/parse string lexicon lookup grammar))

(def index nil)
;; TODO: trying to print index takes forever and blows up emacs buffer:
;; figure out how to change printable version to (keys index).
(def index (future (create-index grammar (flatten (vals @lexicon)) head-principle)))

(defn sentence [ & [spec]]
  (let [spec (unify (if spec spec :top)
                    {:synsem {:subcat '()
                              :cat :verb}})]
    (forest/generate spec grammar (flatten (vals @lexicon)) index)))

(declare small)

(defn generate [ & [spec model]]
  (let [spec (if spec spec :top)
        model (if model model small)
        model (if (future? model) @model model)]
    (forest/generate spec
                     (:grammar model)
                     (:lexicon model)
                     (:index model))))

;; TODO: factor out to forest/.
(defn generate-all [ & [spec {use-grammar :grammar
                              use-index :index
                              use-lexicon :lexicon}]]
  (let [spec (if spec spec :top)
        use-grammar (if use-grammar use-grammar grammar)
        use-index (if use-index use-index index)
        use-lexicon (if use-lexicon use-lexicon lexicon)]
    (log/info (str "using grammar of size: " (.size use-grammar)))
    (log/info (str "using index of size: " (.size @use-index)))
    (if (seq? spec)
      (mapcat generate-all spec)
      (forest/generate-all spec use-grammar
                           (flatten (vals @use-lexicon))
                           use-index))))

;; TODO: move the following 2 to lexicon.clj:
(def lookup-in
  "find all members of the collection that matches with query successfully."
  (fn [query collection]
    (loop [coll collection matches nil]
      (if (not (empty? coll))
        (let [first-val (first coll)
              result (unify/match (unify/copy query) (unify/copy first-val))]
          (if (not (unify/fail? result))
            (recur (rest coll)
                   (cons first-val matches))
            (recur (rest coll)
                   matches)))
        matches))))

(defn choose-lexeme [spec]
  (first (unify/lazy-shuffle (lookup-in spec (vals lexicon)))))

(declare enrich)
(declare against-pred)
(declare against-comp)
(declare matching-head-lexemes)
(declare matching-comp-lexemes)

(def small
  (future
    (let [grammar
          (filter #(or (= (:rule %) "s-conditional-nonphrasal")
                       (= (:rule %) "s-present-nonphrasal")
                       (= (:rule %) "s-future-nonphrasal")
                       (= (:rule %) "s-imperfetto-nonphrasal")
                       (= (:rule %) "s-aux")
                       (= (:rule %) "vp-aux"))
                  grammar)
          lexicon
          (into {}
                (for [[k v] @lexicon]
                  (let [filtered-v
                        (filter #(or (= (get-in % [:synsem :cat]) :verb)
                                     (= (get-in % [:synsem :propernoun]) true)
                                     (= (get-in % [:synsem :pronoun]) true))
                                v)]
                    (if (not (empty? filtered-v))
                      [k filtered-v]))))]
      {:name "small"
       :language "it"
       :enrich enrich
       :grammar grammar
       :lexicon lexicon
       :index (create-index grammar (flatten (vals lexicon)) head-principle)})))

(def small-plus-vp-pronoun
  (future
    (let [grammar
          (filter #(or (= (:rule %) "s-conditional-phrasal")
                       (= (:rule %) "s-conditional-nonphrasal")
                       (= (:rule %) "s-present-phrasal")
                       (= (:rule %) "s-present-nonphrasal")
                       (= (:rule %) "s-future-phrasal")
                       (= (:rule %) "s-future-nonphrasal")
                       (= (:rule %) "s-imperfetto-phrasal")
                       (= (:rule %) "s-imperfetto-nonphrasal")
                       (= (:rule %) "s-aux")
                       (= (:rule %) "vp-aux")
                       (= (:rule %) "vp-aux-22")
                       (= (:rule %) "vp-pronoun-nonphrasal")
                       (= (:rule %) "vp-pronoun-phrasal"))
                  grammar)
          lexicon
          (into {}
                (for [[k v] @lexicon]
                  (let [filtered-v
                        (filter #(or (= (get-in % [:synsem :cat]) :verb)
                                     (= (get-in % [:synsem :propernoun]) true)
                                     (= (get-in % [:synsem :pronoun]) true))
                                v)]
                    (if (not (empty? filtered-v))
                      [k filtered-v]))))]
      {:name "small-plus-vp-pronoun"
       :language "it"
       :enrich enrich
       :grammar grammar
       :lexicon lexicon
       :index (create-index grammar (flatten (vals lexicon)) head-principle)})))

(def medium
  (future
    (let [lexicon
          (into {}
                (for [[k v] @lexicon]
                  (let [filtered-v v]
                    (if (not (empty? filtered-v))
                      [k filtered-v]))))]
      {:name "medium"
       :enrich enrich
       :grammar grammar
       :lexicon lexicon
       :index (create-index grammar (flatten (vals lexicon)) head-principle)
       })))

(defn enrich [spec]
  (let [against-pred (against-pred spec)]
    (if true against-pred
        (let [against-comp (map (fn [spec]
                            (against-comp spec))
                          (if (seq? against-pred)
                            (seq (set against-pred))
                            against-pred))]
          (if (seq? against-comp)
            (seq (set against-comp))
            against-comp)))))

(defn against-pred [spec]
  (let [pred (get-in spec [:synsem :sem :pred] :top)]
    (if (= :top pred)
      spec
      (mapcat (fn [lexeme]
                (let [result (unify spec
                                    {:synsem {:sem (strip-refs (get-in lexeme [:synsem :sem] :top))}}
                                    {:synsem {:essere (strip-refs (get-in lexeme [:synsem :essere] :top))}}
                                    )]
                  (if (not (fail? result))
                    (list result))))
              (matching-head-lexemes spec)))))

;; TODO: not currently used: needs to be called from within (enrich).
(defn against-comp [spec]
  (let [pred-of-comp (get-in spec [:synsem :sem :subj :pred] :top)]
    (if (= :top pred-of-comp)
      spec
      (mapcat (fn [lexeme]
                (let [result (unify spec
                                    {:comp {:synsem {:agr (strip-refs (get-in lexeme [:synsem :agr] :top))
                                                     :sem (strip-refs (get-in lexeme [:synsem :sem] :top))}}})]
                  (if (not (fail? result))
                    (list result))))
              (matching-comp-lexemes spec)))))

(defn matching-head-lexemes [spec]
  (let [pred-of-head (get-in spec [:synsem :sem :pred] :top)]
    (if (= pred-of-head :top)
      spec
      (mapcat (fn [lexemes]
                (mapcat (fn [lexeme]
                          (if (= pred-of-head
                                 (get-in lexeme [:synsem :sem :pred] :top))
                            (list lexeme)))
                        lexemes))
              (vals @lexicon)))))

(defn matching-comp-lexemes [spec]
  (let [pred-of-comp (get-in spec [:synsem :sem :subj :pred] :top)]
    (if (= pred-of-comp :top)
      spec
      (mapcat (fn [lexemes]
                (mapcat (fn [lexeme]
                          (if (= pred-of-comp
                                 (get-in lexeme [:synsem :sem :pred] :top))
                            (list lexeme)))
                        lexemes))
              (vals @lexicon)))))

(declare body)
(declare headers)

(def headers {"Content-Type" "text/html;charset=utf-8"})
(def language-name "Italiano")

(def routes
  (compojure/routes
   (GET "/" request
        (is-admin {:body (body language-name language-name request)
                   :status 200
                   :headers headers}))))

(defn body [title content request]
  (html/page
   title
   (html
    [:div.major
     [:h2 title]

     [:div.content
      content
      ]
     ])
   request))

;; TODO: move this function elsewhere; it has a dependency on english/small.
(defn fill-by-spec [spec count table source-model target-model-as-string]
  (let [target-model-as-string (if target-model-as-string
                                 target-model-as-string
                                 "small")]
    (populate count (eval (symbol (str "italianverbs." "english/" target-model-as-string)))
              source-model
              spec table)))

(defn fill-verb [verb count & [spec table model]] ;; spec is for additional constraints on generation.
  (let [spec (if spec spec :top)
        model (if model model small)
        tenses [{:synsem {:sem {:tense :conditional}}}
                {:synsem {:sem {:tense :futuro}}}
                {:synsem {:sem {:tense :past :aspect :progressive}}}
                {:synsem {:sem {:tense :past :aspect :perfect}}}
                {:synsem {:sem {:tense :present}}}]]
    (let [spec (unify {:root {:italiano {:italiano verb}}}
                       spec)]
      (log/debug (str "fill-verb spec: " spec))
      (pmap (fn [tense] (fill-by-spec (unify spec
                                             tense)
                                      count
                                      table
                                      model))
            tenses))))

(defn fill-per-verb [ & [count-per-verb]]
  (let [italian-verbs
        (sort (keys (infinitives @lexicon)))
        count-per-verb (if count-per-verb count-per-verb 10)]
    (map (fn [verb]
           (fill-verb verb count-per-verb))
         italian-verbs)))

;; HOWTO: fix and add expressions : (TODO: turn into test in test/italiano)
;; 1. (if necessary) truncate bad expressions in main table (use some kind of ILIKE statement)
;; 2. truncate expression_import: (truncate "expression_import")
;; 3. generate English -> Italian expressions in expression_import temporary table
;;    (fill-verbs-in-list ["mentire"] "expression_import" 5)
(def do-insert "
INSERT INTO expression (language,model,surface,structure,serialized)
     SELECT language,model,surface,structure,serialized
       FROM expression_import;
")

(defn fill-verbs-in-list [verbs table count-per-verb]
  (pmap #(fill-verb % count-per-verb :top "expression_import")
        verbs))

;; then in target DB (e.g. dev or production):
;; TRUNCATE expression_import;
(def insert-into
"
INSERT INTO expression (language,model,surface,structure,serialized)
   SELECT language,model,surface,structure,serialized
     FROM expression_import
")

;; (fill-by-spec {:root {:italiano {:italiano "lavare"}}} 1 "expression" small-plus-vp-pronoun "small-plus-vp-pronoun")
