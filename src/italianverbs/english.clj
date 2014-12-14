(ns italianverbs.english
  (:refer-clojure :exclude [get-in]))

(require '[clojure.tools.logging :as log])
(require '[italianverbs.cache :refer (build-lex-sch-cache create-index over spec-to-phrases)])
(require '[italianverbs.generate :as generate])
(require '[italianverbs.grammar.english :as gram])
(require '[italianverbs.lexicon.english :as lex])
(require '[italianverbs.lexiconfn :refer (compile-lex unify)])
(require '[italianverbs.morphology.english :as morph])
(require '[italianverbs.parse :as parse])
(require '[italianverbs.ug :refer :all])
(require '[italianverbs.unify :refer (get-in)])

(def get-string morph/get-string)
(def grammar gram/grammar)
(def lexicon-source lex/lexicon-source)

(log/info "compiling: source-lexicon: " (.size (keys lex/lexicon-source)))
(def lexicon (compile-lex lex/lexicon-source morph/exception-generator morph/phonize morph/english-specific-rules))
(log/info "finished: compiled lexicon: " (.size (keys lexicon)))

(defn lookup [token]
  "return the subset of lexemes that match this token from the lexicon."
  (morph/analyze token #(get lexicon %)))

(def begin (System/currentTimeMillis))
(log/info "building grammatical and lexical index..")
(def index nil)
;; TODO: trying to print index takes forever and blows up emacs buffer:
;; figure out how to change printable version to show only keys and first value or something.
(def index (create-index grammar (flatten (vals lexicon)) head-principle))

(def end (System/currentTimeMillis))
(log/info "Built grammatical and lexical index in " (- end begin) " msec.")

(defn parse [string]
  (parse/parse string lexicon lookup grammar))

(defn sentence [ & [spec]]
  (let [spec (if spec spec :top)]
    (generate/sentence spec grammar index (flatten (vals lexicon)))))

(defn generate [ & [spec {use-grammar :grammar
                          use-index :index
                          use-lexicon :lexicon}]]
  (let [spec (if spec spec :top)
        use-grammar (if use-grammar use-grammar grammar)
        use-index (if use-index use-index index)
        use-lexicon (if use-lexicon use-lexicon lexicon)]
    (log/info (str "using grammar of size: " (.size use-grammar)))
    (log/info (str "using lexicon of size: " (.size (flatten (vals use-lexicon)))))
    (if (seq? spec)
      (map generate spec)
      (generate/generate spec use-grammar
                         (flatten (vals use-lexicon)) 
                         use-index))))

(defn generate-all [ & [spec {use-grammar :grammar}]]
  (let [spec (if spec spec :top)
        use-grammar (if use-grammar use-grammar grammar)]
    (log/info (str "using grammar of size: " (.size use-grammar)))
    (if (seq? spec)
      (map generate-all spec)
      (generate/generate-all spec use-grammar
                             (flatten (vals lexicon)) 
                             index))))

(def small
  (let [grammar
        (filter #(= (:rule %) "s-present")
                grammar)

        lexicon
        (into {}
              (for [[k v] lexicon]
                (let [filtered-v
                      (filter #(or (= (get-in % [:synsem :sem :pred]) :antonio)
                                   (= (get-in % [:synsem :sem :pred]) :bere)
                                   (= (get-in % [:synsem :sem :pred]) :dormire))
                              v)]
                  (if (not (empty? filtered-v))
                    [k filtered-v]))))]
    {:grammar grammar
     :lexicon lexicon
     :index (create-index grammar (flatten (vals lexicon)) head-principle)}))


