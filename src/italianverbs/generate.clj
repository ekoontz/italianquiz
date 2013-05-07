(ns italianverbs.generate
  (:use [clojure.stacktrace]
        [italianverbs.lexiconfn])
  (:require
   [clojure.tools.logging :as log]
   [italianverbs.lev :as lev]
   [italianverbs.morphology :as morph]
   [italianverbs.grammar :as gram]
   [italianverbs.fs :as fs]
   [italianverbs.config :as config]
   [italianverbs.html :as html]
   [italianverbs.lexicon :as lex]
   [italianverbs.search :as search]
   [clojure.string :as string]))

(defn filter-by-match [spec lexicon]
  (mapcat (fn [lexeme] (if (not (fs/fail? lexeme)) (list lexeme)))
          (map (fn [lexeme] (fs/match (fs/copy spec) (fs/copy lexeme))) lex/lexicon)))

(defn printfs [fs & filename]
  "print a feature structure to a file. filename will be something easy to derive from the fs."
  (let [filename (if filename (first filename) "foo.html")]  ;; TODO: some conventional default if deriving from fs is too hard.
    (spit filename (html/static-page (html/tablize fs) filename))))

(defn random-symbol [& symbols]
  (let [symbols (apply list symbols)]
    (nth symbols (rand-int (.size symbols)))))

(defn lookup-exception [fs]
  (let [spec
        (dissoc
         (dissoc
          (dissoc
           fs
           :use-number)
          :fn)
         :morph)
        exception
        (search/random-lexeme {:root spec
                               :number :plural})]
    exception))

(defn morph-noun [fs]
  ;; choose number randomly from {:singular,:plural}.
  (let [number (if (:use-number fs)
                 (:use-number fs)
                 (random-symbol :singular :plural))]
    (if (= number :singular)
      ;;
      fs ;; nothing needed to be done: we are assuming fs comes from lexicon and that singular is the canonical lexical form.
      ;; else, plural
      ;; TODO: check for exceptions in both :english and :italian side.
      (fs/unify fs
            {:number :plural}
            {:comp (fs/unify
                    {:number :plural}
                    (:comp (:comp fs)))}
            (let [exception (lookup-exception fs)]
              (if exception exception
                  (if (= (:gender fs) :fem)
                    {:italian (morph/plural-fem (:italian fs))
                     :english (morph/plural-en (:english fs))}
                    ;; else, assume masculine.
                    ;; :checked-for is for debugging only.
                    {:checked-for {:root (dissoc (dissoc fs :_id) :use-number)}
                     :italian (morph/plural-masc (:italian fs))
                     :english (morph/plural-en (:english fs))})))))))

;; TODO: learn why string/join doesn't work for me:
;; (string/join '("foo" "bar") " ")
;; => " " (should be: "foo bar").

;; note: italianverbs.generate> (string/join " " (list "foo" "bar"))
;; ==> "foo bar" <-- works fine.
(defn join [coll separator]
  (apply str (interpose separator coll)))

(defn conjugate-pp [preposition np]
  {:english (join (list (:english preposition) (:english np)) " ")
   :italian (morph/conjugate-italian-prep preposition np)
   :head preposition
   :comp np})

(defn random-infinitivo []
  (choose-lexeme
   (fs/unify {:cat :verb
           :infl :infinitive}
          config/random-infinitivo)))

(defn espressioni []
  (choose-lexeme {:cat :espressioni}))


(defn random-futuro-semplice [& constraints]
  (let [
        ;; 1. choose a random verb in the passato-prossimo form.
        verb-future (choose-lexeme
                     (fs/unify
                      (if constraints (first constraints) {})
                      {:infl :futuro-semplice}
                      config/futuro-semplice))]
    verb-future))

;; cf. grammar/vp: this will replace that.
;(defn vp [ & verb noun]
;  (let [verb (if verb (first verb) (search/random-lexeme {:cat :verb :infl :infinitive}))
 ;       noun (if noun (first noun)
;                 (if (search/get-path verb '(:obj))
;                   (search/random-lexeme (search/get-path verb '(:obj)))))]
;    (if noun
;      ;; transitive:
;      (gram/left verb (gram/np noun))
;      ;; else intransitive:
;    verb)))

(defn subj [verb]
  "generate a lexical subject that's appropriate given a verb."
  (search/random-lexeme
   (fs/unify
    {:cat :noun}
    {:case {:not :acc}}
    (get verb :subj))))

(defn random-verb [] (search/random-lexeme {:cat :verb :infl :infinitive :obj {:not nil}}))

(defn sentence1 []
  (let [verb (search/random-lexeme {:cat :verb :infl :infinitive :obj {:not nil}})
        object (search/random-lexeme (get verb :obj))
        subject (search/random-lexeme (fs/unify {:case {:not :acc}} (get verb :subj)))]
    (list verb object subject)))

(defn generate-np [& constraints]
  (let [det-value (:comp (apply fs/unify constraints))]
    (let [article (if (or (= nil constraints) det-value )
                    (search/random-lexeme {:cat :det}))]
      article)))

;; cf. grammar/sentence: this will replace that.
(defn sentence []
  (let [verb (search/random-lexeme {:cat :verb :infl :infinitive :obj {:cat :noun}})
        object (search/random-lexeme (:obj verb))
        subject (search/random-lexeme (:subj verb))]
    {:subject subject :object object :verb verb}))

(defn n-sentences [n]
  (if (> n 0)
    (cons (sentence)
          (n-sentences (- n 1)))))

(defn mylookup [italian]
  (search/random-lexeme {:italian italian}))


(defn conjugate-np [noun & [determiner]]
  "conjugate a noun with a determiner (if the noun takes a determiner (:comp is not nil)) randomly chosen using the 'determiner' spec."
  (log/info (str "(conjugate-np: " noun "," determiner ")"))
  (let [plural-exception (if (or
                              (= (:number noun) :plural) (= (:number noun) "plural")
                              (and (= (type (:number noun)) clojure.lang.Ref)
                                   (or (= @(:number noun) :plural)
                                       (= @(:number noun) "plural"))))
                           (let [search (search/search noun)] ;; search lexicon for plural exception.
                             (if (and search (> (.size search) 0))
                               (nth search 0))))
        ;; TODO: make these long plural- and masc- checking conditionals much shorter.
        italian (if (or (= (:number noun) :plural)
                        (= (:number noun) "plural")
                        (and (= (type (:number noun)) clojure.lang.Ref)
                             (or (= @(:number noun) :plural)
                                 (= @(:number noun) "plural"))))
                  (if (and plural-exception (:italian plural-exception))
                    (:italian plural-exception)
                    (if (or (= (:gender noun) "masc")
                            (= (:gender noun) :masc)
                            (and (= (type (:gender noun)) clojure.lang.Ref)
                                 (or (= @(:gender noun) :masc)
                                     (= @(:gender noun) "masc"))))
                      (morph/plural-masc (:italian noun))
                      (morph/plural-fem (:italian noun))))
                  (:italian noun))
        english (if (or (= (:number noun) :plural)
                        (= (:number noun) "plural")
                        (and (= (type (:number noun)) clojure.lang.Ref)
                             (or (= @(:number noun) :plural)
                                 (= @(:number noun) "plural"))))
                  (if (and plural-exception (:english plural-exception))
                    (:english plural-exception)
                    (if (not (:pronoun noun)) ;; pronouns should not be pluralized: e.g. "we" doesn't become "wes".
                      (morph/plural-en (:english noun))
                      (:english noun)))
                  (:english noun))
        article-search (if (not (= (:comp noun) nil))
                         (search/search
                          (fs/unify (if determiner determiner {:cat :det})
                                    (:comp noun))))
        article (if (and (not (= article-search nil))
                         (not (= (.size article-search) 0)))
                  (nth article-search (rand-int (.size article-search))))]
    {:italian (string/trim (join (list (:italian article) italian) " "))
     :article-search (:comp noun)
     :english (string/trim (join (list (:english article) english) " "))
     :article article
     :number (:number noun)
     :gender (:gender noun)
     :person (:person noun)
     :noun noun}))

(defn conjugate-sent [verb-phrase subject]
  {
   :italian (join (list (:italian subject) (:italian verb-phrase)) " ")
   :english (join (list (:english subject) (:english verb-phrase)) " ")
   :verb-phrase verb-phrase
   :subject subject})

(defn random-object-for-verb [verb]
  (search/random-lexeme (:obj verb)))

(defn random-verb-for-svo [& svo-maps]
  (let [svo-maps (if svo-maps svo-maps (list {}))]
    (search/random-lexeme (fs/merge (apply :verb svo-maps)
                                    {:cat :verb :infl :infinitive
                                     :obj (fs/merge (apply :obj svo-maps))}))))

;; TODO: refactor with (random-present).
(defn random-past [& svo-maps]
  (let [svo-maps (if svo-maps svo-maps (list {}))
        root-verb (eval `(random-verb-for-svo ~@svo-maps))]
    (let [subject (conjugate-np (search/random-lexeme {:cat :noun} (:subj root-verb)
                                               {:number (random-symbol :singular :plural)}))
          object
          (if (:obj root-verb)
            (conjugate-np (search/random-lexeme {:cat :noun} (:obj root-verb))
                          {:number (random-symbol :singular :plural)}))]
      (let [svo {:subject subject
                 :aux (fs/unify (search/random-lexeme {:italian (:passato-aux root-verb) :infl :infinitive})
                            subject)
                 :object object
                 :vp (fs/unify root-verb {:infl :passato-prossimo})}]
         (conjugate-sent svo subject)))))

(defn rand-sv []
  (let [subjects (search/search {:cat :noun :case {:not :nom}})
        subject (nth subjects (rand-int (.size subjects)))
        root-verbs (search/search {:cat :verb :infl :infinitive})
        root-verb (nth root-verbs (rand-int (.size root-verbs)))
        objects (search/search {:cat :noun :case {:not :acc}})
        object (conjugate-np (nth objects (rand-int (.size objects)))
                             {:def :def})]
    (list subject root-verb object)))

(defn random-verb []
  (let [verbs (search/search {:obj {:not nil} :cat :verb :infl :infinitive})]
    (nth verbs (rand-int (.size verbs)))))

(defn check-objs []
  "check that every inf. verb has at least one satisfying object."
  (let [verbs (search/search {:obj {:not nil} :cat :verb :infl :infinitive})
        objs (map (fn [verb]
                    (let [obj-feat (get-in verb '(:obj))
                          objs (search/search obj-feat)]
                      {:italian (:italian verb)
                       :objs (if objs (.size objs) 0)}))
                  verbs)]
    objs))

(defn take-article [map]
  (fs/unify map
        {:take-article "taken"}))

(defn capitalize [s]
  "Capitalize first char and leave the rest of the characters alone (compare with string/capitalize which lower-cases all chars after first."
  (if (nil? s) ""
      (let [s (.toString s)]
        (if (< (count s) 2)
          (.toUpperCase s)
          (str (.toUpperCase (subs s 0 1))
               (subs s 1))))))

(defn formattare-1 [expr]
  (cond
   (fs/fail? expr)
   "<tt>fail</tt>"
   :else
   (let [english
         (capitalize
          (cond
           (string? (fs/get-in expr '(:english)))
           (fs/get-in expr '(:english))

           (string? (fs/get-in expr '(:english :english)))
           (fs/get-in expr '(:english :english))

           (string? (fs/get-in expr '(:english :infinitive)))
           (fs/get-in expr '(:english :infinitive))

           (and (string? (fs/get-in expr '(:english :a :english)))
                (string? (fs/get-in expr '(:english :b :english))))
           (str (fs/get-in expr '(:english :a :english))
                " "
                (fs/get-in expr '(:english :b :english)))


           (and (string? (fs/get-in expr '(:english :a :infinitive)))
                (string? (fs/get-in expr '(:english :b))))
           (str (fs/get-in expr '(:english :a :infinitive))
                " "
                (fs/get-in expr '(:english :b)))

           (and (string? (fs/get-in expr '(:english :a :infinitive)))
                (string? (fs/get-in expr '(:english :b :a :infinitive)))
                (string? (fs/get-in expr '(:english :b :b))))
           (str (fs/get-in expr '(:english :a :infinitive))
                " "
                (fs/get-in expr '(:english :b :a :infinitive))
                " "
                (fs/get-in expr '(:english :b :b)))

           (string? (fs/get-in expr '(:english :a :english)))
           (str (fs/get-in expr '(:english :a :english)) "...")

           (string? (fs/get-in expr '(:english :a :infinitive)))
           (str (fs/get-in expr '(:english :a :infinitive)) "...")

           true
           (fs/get-in expr '(:english))))

         italian
         (capitalize
          (cond
           (string? (fs/get-in expr '(:italian)))
           (fs/get-in expr '(:italian))

           (string? (fs/get-in expr '(:italian :italian)))
           (fs/get-in expr '(:italian :italian))

           (string? (fs/get-in expr '(:italian :infinitive)))
           (fs/get-in expr '(:italian :infinitive))

           (and
            (string? (fs/get-in expr '(:italian :a :italian)))
            (string? (fs/get-in expr '(:italian :b :italian))))
           (str
            (fs/get-in expr '(:italian :a :italian)) " "
            (fs/get-in expr '(:italian :b :italian)))

           (string? (fs/get-in expr '(:italian :a)))
           (str (fs/get-in expr '(:italian :a)) "...")

           (string? (fs/get-in expr '(:italian :a :italian)))
           (str (fs/get-in expr '(:italian :a :italian)) "...")

           (and (string? (fs/get-in expr '(:italian :a :infinitive)))
                (string? (fs/get-in expr '(:italian :b))))
           (str (fs/get-in expr '(:italian :a :infinitive))
                " "
                (fs/get-in expr '(:italian :b)))

           (string? (fs/get-in expr '(:italian :a :infinitive)))
           (str (fs/get-in expr '(:italian :a :infinitive)) "...")

           true
           (fs/get-in expr '(:italian)))
          )
         ]
     (string/trim
      (str italian " (" english ").")))))

;;; e.g.:
;;; (formattare (over (over s (over (over np lexicon) (lookup {:synsem {:human true}}))) (over (over vp lexicon) (over (over np lexicon) lexicon))))
;; TO DO: move this to morphology.clj since it has to do with surface representation.
(defn formattare [expressions]
  "format a bunch of expressions (feature-structures) showing just the italian (and english in parentheses)."
  (do
    (if (map? expressions)
      ;; wrap this single expression in a list and re-call.
      (list (formattare-1 expressions))
      (cond (nil? expressions) nil
            (fs/fail? expressions)
            ":fail"
            (= (.size expressions) 0) nil
            true
            (cons
             (formattare-1 (first expressions))
             (formattare (rest expressions)))))))

(defn fo [expressions]
  (formattare expressions))

(defn unify-and-merge [parent child1 child2]
  (let [unified
        (unify parent
               {:1 child1
                :2 child2}
               {:1 {:synsem {:sem (lex/sem-impl (fs/get-in child1 '(:synsem :sem)))}}
                :2 {:synsem {:sem (lex/sem-impl (fs/get-in child2 '(:synsem :sem)))}}})
        fail (fs/fail? unified)]
    (if (= fail true)
      :fail
      (merge unified
             {:italian (morph/get-italian
                        (fs/get-in unified '(:1 :italian))
                        (fs/get-in unified '(:2 :italian)))
              :english (morph/get-english
                        (fs/get-in unified '(:1 :english))
                        (fs/get-in unified '(:2 :english)))}))))

(defn unify-side [parent left-side right-sides]
  (if (empty? right-sides)
    nil
    (lazy-seq (cons (unify-and-merge left-side (first right-sides))
                    (unify-side left-side (rest right-sides))))))

(defn unify-sides [parent & [left-sides right-sides]]
  (let [left-sides
        (if (nil? left-sides)
          (lazy-seq lex/determiners)
          left-sides)
        right-sides
        (if (nil? right-sides)
          (lazy-seq (unify-sides gram/nbar)))]
    (lazy-cat (unify-side parent
                          (first left-sides)
                          right-sides)
              (unify-sides parent (rest left-sides)
                           right-sides))))


(defn unify-lr3 [left rights]
  (map (fn [right]
         {:1 left
          :2 right})
       rights))

(defmacro unify-lr2 [lefts rights]
  `(lazy-cat ~@(map (fn [left]
                      (unify-lr3 left rights))
                    lefts)))

(defn unify-lr [lefts rights]
  "lefts and rights are lazy sequences."
  (map (fn [left]
         (let [left
               (unify left
                      {:1 {:synsem {:sem (lex/sem-impl (fs/get-in left '(:1 :synsem :sem)))}}})]
           (remove #(= :fail %)
                   (map (fn [right]
                          (let [unified
                                (unify left right
                                       {:2 {:synsem {:sem (lex/sem-impl (fs/get-in right '(:2 :synsem :sem)))}}})
                                fail (fs/fail? unified)]
                            (if fail
                              :fail
                              (merge unified
                                     {:italian (morph/get-italian
                                                (fs/get-in unified '(:1 :italian))
                                                (fs/get-in unified '(:2 :italian)))
                                      :english (morph/get-english
                                                (fs/get-in unified '(:1 :english))
                                                (fs/get-in unified '(:2 :english)))}))))
                        rights))))
       lefts))

(defn unify-lr4 [parent left right]
  (let [unified
        (unify parent
               {:1 left}
               {:2 right}
               {:1 {:synsem {:sem (lex/sem-impl (fs/get-in left '(:synsem :sem)))}}
                :2 {:synsem {:sem (lex/sem-impl (fs/get-in right '(:synsem :sem)))}}})]
    (if (fs/fail? unified)
      :fail
      (merge unified
             {:italian (morph/get-italian
                        (fs/get-in unified '(:1 :italian))
                        (fs/get-in unified '(:2 :italian)))
              :english (morph/get-english
                        (fs/get-in unified '(:1 :english))
                        (fs/get-in unified '(:2 :english)))}))))

(defn unify-lr-hc [parent head comp]
  (let [with-head (unify parent
                         {:head head})
        with-comp (unify parent
                          {:head comp})
        unified
        (unify parent
               {:head head}
               {:comp comp}
               {:head {:synsem {:sem (lex/sem-impl (fs/get-in head '(:synsem :sem)))}}
                :copm {:synsem {:sem (lex/sem-impl (fs/get-in comp '(:synsem :sem)))}}})]
    (if (fs/fail? unified)
      :fail
      (merge unified
             {:italian (morph/get-italian
                        (fs/get-in unified '(:1 :italian))
                        (fs/get-in unified '(:2 :italian)))
              :english (morph/get-english
                        (fs/get-in unified '(:1 :english))
                        (fs/get-in unified '(:2 :english)))}))))

(defn gen2-with [parent candidates key]
  (remove #(= :fail %)
          (map (fn [candidate]
                 (let [unify-with-candidate (fs/unifyc parent {key candidate})]
                   (if (fs/fail? unify-with-candidate)
                     :fail
                     unify-with-candidate)))
               candidates)))

(defn gen2nbar [parent]
  (let [gen-left (gen2-with parent (lazy-seq (shuffle lex/human-nouns)) :1)
        gen-right (gen2-with parent (lazy-seq (shuffle lex/adjectives)) :2)]
    (unify-lr gen-left gen-right)))

(defn gen2np [parent left right]
  (let [gen-left (gen2-with parent (shuffle left) :1)
        logstuff (log/debug "start gen-right..")
        gen-right (gen2-with parent (gen2nbar right) :2)]
    (log/debug "start unify-lr in gen2np..")
    (log/debug "start apply concat in gen2np..")
    (unify-lr gen-left gen-right)))

(defn gen2sent [parent]
  (let [gen-left (gen2-with parent (gen2np gram/np lex/determiners gram/nbar) :1)
        gen-right (gen2-with parent (shuffle lex/intransitive-verbs) :2)]
    (unify-lr gen-left gen-right)))

;; TODO: use multiple dispatch.
(defn over2 [parent child1 child2]
  (if (vector? child1)
    (do
      (log/debug (str "over2 with child1 size: " (.size child1)))))
  (if (vector? child2)
    (do
      (log/debug (str "over2 with child2 size: " (.size child2)))))

  (cond

   (vector? parent)
   (over2 (lazy-seq parent) child1 child2)

   (vector? child1)
   (over2 parent (lazy-seq child1) child2)

   (vector? child2)
   (over2 parent child1 (lazy-seq child2))

   (= (type child1) java.lang.String)
   (over2 parent (lex/it child1) child2)

   (= (type child2) java.lang.String)
   (over2 parent child1 (lex/it child2))

   (nil? parent)
   nil

   (and (seq? parent)
        (= (.size parent) 0))
   nil

   (nil? child1)
   nil

   (nil? child2)
   nil

   (and (seq? child1)
        (= (.size child1) 0))
   nil

   (and (seq? child2)
        (= (.size child2) 0))
   nil

   (or (set? parent) (seq? parent))
   (lazy-cat (over2 (first parent) child1 child2)
             (over2 (rest parent) child1 child2))

   (or (set? child1) (seq? child1))
   (do
     (log/info (str (fs/get-in parent '(:comment)) ":child1: " (.size child1)))
     (lazy-cat (over2 parent (first child1) child2)
               (over2 parent (rest child1) child2)))

   (or (set? child2) (seq? child2))
   (do
;     (log/info (str "child2: " (.size child2)))
     (lazy-cat (over2 parent child1 (first child2))
               (over2 parent child1 (rest child2))))

   :else ; both parent and children are non-lists.
   ;; First, check to make sure complement matches head's (:synsem :sem) value, if it exists, otherwise, fail.
   (let [unified (unify-and-merge parent child1 child2)
         fail (fs/fail? unified)]
     (if (not fail)
       (list unified)))))

;; TODO: use multiple dispatch.
(defn over-parent-child [parent child]
  (log/debug (str "parent: " parent))
  (log/debug (str "child: " child))
  (cond

   (= (type child) java.lang.String)
   (over-parent-child parent (lex/it child))

   (nil? parent)
   nil

   (and (seq? parent)
        (= (.size parent) 0))
   nil

   (or (set? parent) (seq? parent))
   (lazy-cat
    (remove #(fs/fail? %)
            (over-parent-child (first parent) child))
    (over-parent-child (rest parent) child))

   (nil? child)
   nil

   (and (seq? child)
        (= (.size child) 0))
   nil

   (or (set? child) (seq? child))
   (let [retval (over-parent-child parent (first child))]
     (lazy-cat
      (remove #(fs/fail? %)
              (over-parent-child parent (first child)))
      (over-parent-child parent (rest child))))

   :else ; both parent and child are non-lists.
   ;; First, check to make sure complement matches head's (:synsem :sem) value, if it exists, otherwise, fail.
   (let [
         ;; "add-child-where": find where to attach child (:1 or :2), depending on value of current left child (:1)'s :italian.
         ;; if (:1 :italian) is nil, the parent has no child at :1 yet, so attach new child there at :1.
         ;; Otherwise, a :child exists at :1, so attach new child at :2.
         add-child-where (if (and
                              (not
                               (string?
                                (fs/get-in parent '(:1 :italian))))
                              (not
                               (string?
                                (fs/get-in parent '(:1 :italian :infinitive))))
                              ;; TODO: remove: :root is going away.
                              (not
                               (string?
                                (fs/get-in parent '(:1 :italian :root))))
                              (not
                               (string?
                                (fs/get-in parent '(:1 :italian :italian)))))
                           :1
                           :2)

         head-is-where (if (= (fs/get-in parent '(:head))
                              (fs/get-in parent '(:1)))
                         :1
                         :2)
         child-is-head (= head-is-where add-child-where)
         comp (if child-is-head
                (fs/get-in parent '(:comp))
                child)
         head (if child-is-head
                child
                (fs/get-in parent '(:head)))
         sem-filter (fs/get-in head '(:synsem :subcat :2 :sem)) ;; :1 VERSUS :2 : make this more explicit about what we are searching for.
         comp-sem (fs/get-in comp '(:synsem :sem))
         do-match
         (if (and (not (nil? sem-filter))
                  (not (nil? comp-sem)))
           (fs/match {:synsem (fs/copy sem-filter)}
                     (fs/copy {:synsem (fs/copy comp-sem)})))]
     ;; wrap the single result in a list so that it can be consumed by (over).
     (list
     (if (= do-match :fail)
       (do
         (log/debug (str "sem-filter: " sem-filter))
         (log/debug (str "comp-sem: " (fs/copy comp-sem)))
         (log/debug "failed match.")
         :fail)
       (let [unified (unify parent
                            {add-child-where
                             (unify
                              (let [sem (fs/get-in child '(:synsem :sem) :notfound)]
                                (if (not (= sem :notfound))
                                  {:synsem {:sem (lex/sem-impl sem)}}
                                  {}))
                             child)})]
         (if (fs/fail? unified)
           (log/debug "Failed attempt to add child to parent: " (fs/get-in parent '(:comment))
                     " at: " add-child-where))

          ;;
          (if (or true (not (fs/fail? unified))) ;; (or true - even if fail, still show it)
            (merge ;; use merge so that we overwrite the value for :italian.
             unified
             {:italian (morph/get-italian
                       (fs/get-in unified '(:1 :italian))
                       (fs/get-in unified '(:2 :italian)))
              :english (morph/get-english
                        (fs/get-in unified '(:1 :english))
                        (fs/get-in unified '(:2 :english)))})
            :fail)))))))

(defn over-parent [parent children]
  (cond
   (= (.size children) 0)
   nil
   true
   (lazy-seq
    (cons
     (over-parent-child parent (first children))
     (over-parent parent (rest children))))))

(defn over [& args]
  "usage: (over parent child) or (over parent child1 child2)"
  (let [parent (first args)
        child1 (second args)
        child2 (if (> (.size args) 2) (nth args 2))]
    (if (not (nil? child2))
      (over-parent-child (over-parent-child parent child1) child2)
      (over-parent-child parent child1))))

(defn random-extension [phrase]
  (if (= :notfound (:extend phrase :notfound))
;    (throw (Exception. (str "Can't generate using this rule: " phrase "  because it has no :extend feature.")))
    {:error "cannot generate using this rule."
     :rule phrase}
    (let [random-key (nth (keys (:extend phrase)) (rand-int (.size (keys (:extend phrase)))))]
      (get (:extend phrase) random-key))))

;; TODO: figure out how to encode namespaces within rules so we don't need
;; to have this difficult-to-maintain static mapping.
(defn eval-symbol [symbol]
  (cond
   (= symbol 'adjectives) lex/adjectives
   (= symbol 'nouns) lex/nouns
   (= symbol 'intransitive-verbs) lex/intransitive-verbs
   (= symbol 'transitive-verbs) lex/transitive-verbs
   (= symbol 'verbs-taking-pp) lex/verbs-taking-pp
   (= symbol 'modal-verbs) lex/modal-verbs
   (= symbol 'prepositions) lex/prepositions
   (= symbol 'determiners) lex/determiners
   (= symbol 'nominative-pronouns) lex/nominative-pronouns
   (= symbol 'accusative-pronouns) lex/accusative-pronouns
   (= symbol 'proper-nouns) lex/proper-nouns
   (= symbol 'common-nouns) lex/common-nouns
   (= symbol 'verbs) lex/verbs

   (= symbol 'nbar) gram/nbar
   (= symbol 'np) gram/np
   (= symbol 'prep-phrase) gram/prep-phrase
                                        ; doesn't exist yet:
                                        ;   (= symbol 'vp-infinitive-intransitive) gram/vp-infinitive-intransitive
   (= symbol 'vp-infinitive-transitive) gram/vp-infinitive-transitive

   (= symbol 'vp) gram/vp
   (= symbol 'vp-present) gram/vp-present
   (= symbol 'vp-past) gram/vp-past

   (= symbol 'aux-verbs) (list lex/aux-verbs)
   (= symbol 'essere-aux) (list lex/essere-aux)
   (= symbol 'avere-aux) (list lex/avere-aux)


   true (throw (Exception. (str "(italianverbs.generate/eval-symbol could not evaluate symbol: '" symbol "'")))))

(defn random-head-and-comp-from-phrase [phrase expansion]
  (let [head-sym (:head expansion)
        head (eval-symbol (:head expansion))
        comp (:comp expansion)] ;; leave comp as just a symbol for now: we will evaluate it later in random-comp-from-head.
    {:head head
     :head-sym head-sym ;; saving this for diagnostics
     :comp comp}))

;; filter by unifying each candidate against parent's :head value -
;; if configured to do so. If no filtering, we assume that all candidates
;; will unify successfully without checking, so we can avoid the time spent
;; in this filtering.
(def filter-head true)

(defn head-candidates [phrase expansion]
  (let [debug (fs/copy phrase)
        head-and-comp (random-head-and-comp-from-phrase phrase expansion)
        parent phrase]
;    (log/info (str "phrase: " (:comment phrase)))
;    (log/info (str "phrase synsem: " (fs/get-in phrase '(:synsem))))
;    (log/info (str "expansion: " expansion))
    (if (fs/fail? parent)
       (do
         (log/error "head-candidates: parent is fail: " parent)
         (log/error "phrase(debug): " debug)
         (log/error "expansion: " expansion)
         (throw (Exception. (str "head-candidates: parent is fail: " parent)))))
    (let [filter-candidates-against-phrase (filter (fn [unified-phrase-and-candidate]
                                                     (not (fs/fail? unified-phrase-and-candidate)))
                                                   (map (fn [head-candidate]
                                                          (fs/unifyc {:head head-candidate}
                                                                     phrase))
                                                        (:head head-and-comp)))
          debug (log/debug (str "filter-candidates-against-phrase: " (.size filter-candidates-against-phrase)))

          filter-against-complements (filter (fn [head-candidate]
                                               true)
                                             filter-candidates-against-phrase)

          debug (log/info (str "filter-against-complements: " (.size filter-against-complements)))

          ]


      (if (and
           (list? (:head head-and-comp))
           (= (.size filter-candidates-against-phrase) 0))
        (do
          (log/error "For phrase " (fs/get-in phrase '(:comment)) " with expansion " expansion ", no heads could match this phrase's head requirements: " (fs/get-in phrase '(:head)))
          (log/error (str "First head candidate unify: " (fs/unifyc {:head (first (:head head-and-comp))}
                                                                    phrase)))
          (throw (Exception. (str "No heads could match this phrase's head requirements: " (fs/get-in phrase '(:head)))))))
      (let [head (:head head-and-comp)
            head-filter (fs/unifyc (fs/get-in parent '(:head))
                                   (if (not (nil? (fs/get-in parent '(:head :synsem :sem))))
                                     {:head {:synsem {:sem (lex/sem-impl (fs/get-in parent '(:head :synsem :sem)))}}}
                                     :top))
            check-filter (if (fs/fail? head-filter)
                           (do
                             (log/error (str "head-filter is fail: " head-filter))
                             (log/error (str " parent's head:" (fs/get-in parent '(:head))))
                             (log/error (str " arg1" (fs/get-in parent '(:head :synsem :sem))))
                             (throw (Exception. (str "head-filter is fail: " head-filter)))))

            candidates
            (if (and filter-head (seq? head))
              ;; If head is a seq, then head is a list of possible candidates
              ;; for head of this phrase (e.g. all nouns for NP) ..
              (filter (fn [head-candidate]
                        (let [head-candidate
                              (if (not (nil? (fs/get-in head-candidate '(:synsem :sem))))
                                (fs/unifyc head-candidate
                                           {:synsem {:sem (lex/sem-impl (fs/get-in head-candidate '(:synsem :sem)))}})
                                head-candidate)]
                          (or false
                              (not (fs/fail? (fs/unifyc head-filter head-candidate))))))
                      head)
              ;; .. else, the head is not a seq, so we assume it must be a map. Unify it with parent's head constraints.
              (fs/unifyc head (fs/get-in parent '(:head))))]
        (if (nil? candidates)
            (throw (Exception. (str "Candidates is nil."))))
        (if (= (.size candidates) 0)
          (do
            (log/error (str "expansion: " expansion))
            (let [head-candidate (nth head 0)]
              (if (not (nil? (fs/get-in head-candidate '(:synsem :sem))))
                (let [unify-result
                    (fs/unifyc head-candidate
                               {:synsem {:sem (lex/sem-impl (fs/get-in head-candidate '(:synsem :sem)))}})]
                  )))

            (if (seq? head)
              (do
                (log/error (str "number of candidates:" (.size head)))
                (if (> (.size head) 0)
                  (do
                    :fail

                    ))))

            (log/error (str "head-filter: " (fs/get-in head-filter '(:synsem :sem))))
            :fail)
          (do
            (log/debug (str "successfully matched expansion: " expansion))
            (log/debug (str "expansion of " (fs/get-in phrase '(:comment))  " -> "   expansion " ratio:" (/ (.size candidates) (.size head))))
            (log/debug (str "successfully matched head-filter: " (fs/get-in head-filter '(:synsem :sem))))
            candidates))))))

(declare generate)

(defn random-head [parent expansion]
  (let [check (if (fs/fail? parent)
                (do
                  (log/error (str "parent is fail: " parent))
                  (throw (Exception. (str "random-head: parent is fail: " parent)))))

                                        ;  (println (str "RH parent: " parent))
                                        ;  (println (str "RH head-filter: " (fs/get-in parent '(:head))))
        head-filter (fs/get-in parent '(:head))
        head (head-candidates parent expansion)]
    (cond
     (map? head)
     ;; recursively expand.
     (generate head)
     (seq? head) ;; head is a list of candidate heads (lexemes): choose one at random.
     (do
       (log/info (str "choosing head from list of size: " (.size head)))
       (rand-nth head))
     true
     (throw (Exception. (str "- don't know how to get a head from: " head))))))

(defn check-parent-comp-vs-expansion [comp-expansion parent]
  "Check whether this parent will be able to successfully
generate a complement from this comp-expansion."
  (let [comp-spec (fs/get-in parent '(:comp))
        comps
        (if (symbol? comp-expansion)
          (eval-symbol comp-expansion)
          comp-expansion)]
    (cond
     (seq? comps)
     true ;; TODO: check that at least one succeeds rather than just returning true.
     (map? comps) ;; a feature structure.
     (let [unified-spec (fs/unifyc comps comp-spec)
           check (if (fs/fail? comps)
                   (throw (Exception.
                           (str "comps was fail: " comps))))
           check (if (fs/fail? comp-spec)
                   (throw (Exception.
                           (str "comp-spec was fail: " comp-spec))))]
       unified-spec))))

(defn expansion-to-candidates [comp-expansion comp-spec]
  "Generate a complement from the right side of a rule. A rule's right
side consists of a head and a comp. comp-expansion is the
complement. This may stand for (i.e. have as its value) either a
sequence of lexemes (if a symbol) or may be a map (i.e. a single
lexeme or phrase). e.g. in the rule np -> det nbar, the comp-expansion
is 'det'.  The comp-spec param can be used to place additional
constraints on the generation of the complement."
  (let [comps
        (if (symbol? comp-expansion)
          (eval-symbol comp-expansion)
          comp-expansion)]
    (log/debug (str "expansion-to-candidates: " comp-expansion))
    (cond
     (seq? comps)
     comps
     (map? comps) ;; a map such as np:
     (let [unified-spec (fs/unifyc comps comp-spec)
           check (if (fs/fail? comps)
                   (throw (Exception.
                           (str "comps was fail: " comps))))
           check (if (fs/fail? comp-spec)
                   (throw (Exception.
                           (str "comp-spec was fail: " comp-spec))))
           check (if (fs/fail? unified-spec)
                   (do
                     (log/error (str "unified-spec was fail: " unified-spec))
                     (log/error (str "comps: " comps))
                     (log/error (str "comp-spec: " comp-spec))
                     (throw (Exception.
                             (str "unified-spec was fail: " unified-spec)))))

           generated (generate unified-spec)] ;; recursively generate a phrase.
       (if (nil? generated)
         (throw (Exception.
                 (str "(generate) could not generate: returned nil with input: " unified-spec)))
         (list generated))) ;; wrap the generated phrase in a list (so we choose 'randomly' from amongst a singleton set).
     (nil? comp-expansion)
     (throw (Exception. (str "comp-expansion must not be null.")))
     true
     (do
       (log/error (str "input values: " comp-expansion " and " comp-spec " are not supported yet."))
       (throw (Exception. (str "TODO: recursively expand rules.")))))))

(defn random-comp-for-parent [parent comp-expansion]
  (let [check (if (fs/fail? parent)
                (do
                  (log/error (str "parent was fail: " parent))
                  (log/error (str "comp-expansion: " comp-expansion))
                  (throw (Exception. (str "parent was fail: " parent)))))
        check (let [comp-check (check-parent-comp-vs-expansion comp-expansion parent)]
                (if (fs/fail? comp-check)
                  (do
                    (log/error (str "This parent: " parent))
                     (log/error (str " cannot generate given comp-expansion: " comp-expansion))
                     (throw (Exception.
                             (str "Parent: '" (fs/get-in parent '(:comment)) "' with head italian=" (fs/get-in parent '(:head :italian)) "), whose head specifies a complement:" (fs/get-in parent '(:head :synsem :subcat)) " cannot generate from: " comp-expansion "."))))))

        ;; lookup lexical entries (if a symbol representing a set of lexemes) or generate a candidate (if a symbol representing a generative rule).
        candidates (expansion-to-candidates comp-expansion (fs/get-in parent '(:comp)))
        path-to-comp-sem (if (not (nil? (fs/get-in parent '(:comp :synsem :sem :mod))))
                           '(:comp :synsem :sem :mod)
                           '(:comp :synsem :sem))
        sem-impl-input (if (map? (fs/get-in parent path-to-comp-sem))
                         (dissoc (dissoc (fs/get-in parent path-to-comp-sem) :mod) :pred))
        sem-impl-result (if (not (nil? sem-impl-input))
                          (do
;                            (println (str "NON-NULL SEMANTICS: " (fs/get-in parent path-to-comp-sem)))
                            (lex/sem-impl sem-impl-input)))
        complement-filter (if (not (nil? sem-impl-result))
                            (if (= path-to-comp-sem '(:comp :synsem :sem :mod))
                              (unify {:synsem {:sem {:mod sem-impl-result}}}
                                     (fs/get-in parent '(:comp)))
                              (unify {:synsem {:sem sem-impl-result}}
                                     (fs/get-in parent '(:comp))))
                            (fs/get-in parent '(:comp)))
;        debug (println (str "CF: " complement-filter))
        check-fail (if (fs/fail? complement-filter)
                     (do
                       (log/error (str "Candidate filter is fail: " complement-filter))
                       (log/error (str "sem-impl-result: " sem-impl-result))
                       (log/error (str "alternative-a: " (= path-to-comp-sem '(:comp :synsem :sem :mod))))
                       (log/error (str "path-to-comp-sem: " path-to-comp-sem))
                       (log/error (str "parent's comp: " (fs/get-in parent '(:comp))))
                       (log/error (str "double-check: "
                                       (if (not (nil? sem-impl-result))
                                         (if (= path-to-comp-sem '(:comp :synsem :sem :mod))
                                           (unify {:synsem {:sem {:mod sem-impl-result}}}
                                                  (fs/get-in parent '(:comp)))
                                           (unify {:synsem {:sem sem-impl-result}}
                                                  (fs/get-in parent '(:comp))))
                                         (fs/get-in parent '(:comp)))))
                       (log/error (str "first unify arg : " {:synsem {:sem {:mod sem-impl-result}}}))
                       (log/error (str "parent's comp's:" (fs/get-in parent '(:comp))))
                      (throw (Exception. (str "Candidate filter is fail: " complement-filter)))))
        debug (log/debug (str "CF: " complement-filter))
        debug (log/debug (str "AGAINST #CANDIDATES:" (.size candidates)))
        filtered  (if (> (.size candidates) 0)
                    (filter (fn [x]
                              (not (fs/fail?
                                    (if (not (= complement-filter {}))
                                      (fs/unifyc complement-filter x)
                                      :top))))
                            candidates)
                    (do
                      (log/error (str "No candidates found for comp-expansion: " comp-expansion))
                      (throw (Exception. (str "No candidates found for comp-expansion: "
                                              comp-expansion ". Tried " (.size candidates) " candidates.")))))]
    ;; TODO: don't build this huge diagnostic map unless there's a reason to -
    ;; i.e. development/debugging/exceptions: commenting out the following for now.
    ;;
    {:comp (if (> (.size filtered) 0)
             (nth filtered (rand-int (.size filtered)))
             (do
               (log/error (str "No candidates for expansion: " comp-expansion " in rule:  " (fs/get-in parent '(:comment)) " matched complement filter: " (fs/get-in complement-filter '(:synsem :sem)) " - tried: " (.size candidates) " candidates."))
               (log/error (str " Parent: " parent))
               (log/error (str " Head: " (fs/get-in parent '(:head :italian))))
               (log/error (str " unfiltered candidate list:( " (.size candidates) "):" candidates))
               (throw
                (Exception.
                 (str "No candidates matched complement filter: "
                      (fs/get-in complement-filter '(:synsem :sem))
                      " from head: " (fs/get-in parent '(:head :italian))
                      " in parent: " (fs/get-in parent '(:comment))
                      " for generated complement expansion: " comp-expansion
                      ". Tried " (.size candidates) " candidate" (if (not (= (.size candidates) 1)) "s")    " before giving up.")))))


     :parent parent
     :expansion comp-expansion}))

(defn generate-with-parent [random-head-and-comp phrase expansion]
  (let [check
        (if (fs/fail? (:head random-head-and-comp))
          (do
            (log/error (str ":head part is :fail of: " random-head-and-comp))
            (throw (Exception. (str "generate-with-parent: head part is fail of: " random-head-and-comp)))))
        head (:head random-head-and-comp)
        random-head (random-head phrase expansion)]
    (let [retval
          (unify
           (fs/copy phrase)
           {:head (fs/copy random-head)})]
      (if (fs/fail? retval)
        (do
          (log/error "generate-with-parent failed: " (fs/get-in phrase '(:comment)) "->" expansion)
          (log/error "*1*")
          (log/error (str "phrase :2: " (fs/get-in phrase '(:head))))
          (log/error "*2*")
          (log/error (str "random-head:2: " random-head))
          (log/error "*3*")
          (log/error (str "english of parent: " (fs/get-in phrase '(:head :english))))
          (log/error (str "english of random-head: " (fs/get-in random-head '(:english))))
;          (log/error (str "RETVAL :2: " (fs/get-in retval '(:head))))
          (throw (Exception. (str "generate-with-parent failed with random-head: " random-head)))))
      (log/debug (str "GWP: " retval))
      retval)))

(declare generate-with-head-and-comp)

(defn generate [phrase]
  (let [check (if (fs/fail? phrase)
                (do
                  (log/error (str "generate: input phrase was fail: " phrase))
                  (throw (Exception. (str "generate: input phrase was fail: " phrase)))))
        chosen-extension (random-extension phrase)]

    (log/debug (str "generating " (:comment phrase) " with " chosen-extension))
    (let [random-head-and-comp (random-head-and-comp-from-phrase phrase chosen-extension)]
;      (log/info (str "generate: returned rhac was: " random-head-and-comp))
      (let [retval (generate-with-head-and-comp phrase random-head-and-comp chosen-extension)]
        (log/debug (str "generate: returning " (:comment phrase) " with " (:extend phrase)))
        retval))))

(defn check-for-fail [check-fs & [ message ] ]
  (if (fs/fail? check-fs)
    (do
      (log/error (str "Fail: " check-fs))
      (throw (Exception. (str "Fail: " check-fs))))))

(defn generate-with-head-and-comp [phrase head-and-comp chosen-extension]
  (let [check (if (fs/fail? head-and-comp)
                (do
                  (log/error (str "head-and-comp was fail: " head-and-comp))
                  (throw (Exception. (str "head-and-comp was fail: " head-and-comp)))))
        check (if (fs/fail? phrase)
                (do
                  (log/error (str "phrase was fail: " phrase))
                  (throw (Exception. (str "phrase was fail: " phrase)))))


        unified-parent (generate-with-parent head-and-comp phrase chosen-extension)]
    (check-for-fail unified-parent)
    (let [comp-expansion (:comp head-and-comp)]
      ;; now get complement given this head.
    (if (nil? comp-expansion)
      ;; no complement:  a phrase with only a single child constituent: just return the parent..
      (merge
       unified-parent
       {:extend chosen-extension}
       {:italian (morph/get-italian
                  (fs/get-in unified-parent '(:1 :italian))
                  ""
                  (fs/get-in unified-parent '(:1 :synsem :cat))
                  nil)
        :english (morph/get-english
                  (fs/get-in unified-parent '(:1 :english))
                  ""
                  (fs/get-in unified-parent '(:1 :synsem :cat))
                  nil)})

      ;; ..else there's a complement.
      (let [random-comp
            (random-comp-for-parent unified-parent comp-expansion)]
        (if (fs/fail? random-comp-for-parent)
          (do
            (log/error (str "RANDOM-COMP IS FAIL WITH unified parent: " unified-parent))
            :fail)
          (if (not (nil? (:error random-comp-for-parent)))
            ;; propagate the error up.
            (do
              (log/error (str "RANDOM-COMP HAS AN ERROR EMBEDDED INSIDE IT."))
              random-comp-for-parent)
          (let [unified-with-comp
                (fs/unifyc
                 unified-parent
                 {:comp
                  (fs/get-in random-comp '(:comp))})]
            (if (fs/fail? unified-with-comp)
              (do
                (log/error (str "UNIFIED-WITH-COMP IS FAIL WITH random-comp=" (fs/get-in random-comp '(:comp))))
                {:error "unified with-comp is fail with this random-comp."
                 :random-comp random-comp})
              (do
                (log/debug (str "italian 1:" (fs/get-in unified-with-comp '(:1 :italian))))
                (log/debug (str "italian 2:" (fs/get-in unified-with-comp '(:2 :italian))))
                (log/debug (str "1 cat:" (fs/get-in unified-with-comp '(:1 :synsem :cat))))
                (log/debug (str "2 cat:" (fs/get-in unified-with-comp '(:2 :synsem :cat))))
                (let [result
                      (merge
                       unified-with-comp
                       {:extend chosen-extension}
                       {:italian (morph/get-italian
                                  (fs/get-in unified-with-comp '(:1 :italian))
                                  (fs/get-in unified-with-comp '(:2 :italian)))
;;; :eng and :it are for debugging:
;;;                        :eng {:a (fs/get-in unified-with-comp '(:1 :english))
;;;                              :b (fs/get-in unified-with-comp '(:2 :english))}
;;;                        :ita {:a (fs/get-in unified-with-comp '(:1 :italian))
;;;                              :b (fs/get-in unified-with-comp '(:2 :italian))}
                        :english (morph/get-english
                                  (fs/get-in unified-with-comp '(:1 :english))
                                  (fs/get-in unified-with-comp '(:2 :english)))})]
                  (if (fs/fail? result)
                    (do
;              (log/error (str "random-comp: " random-comp))
;              (log/error (str "random-comp: (formatted) " (formattare random-comp)))
                      (log/error (str "fail? random-comp: " (fs/fail? random-comp)))
                      (log/error (str "italian 1:" (fs/get-in unified-with-comp '(:1 :italian))))
                      (log/error (str "italian 2:" (fs/get-in unified-with-comp '(:2 :italian))))
                      (log/error (str "1 cat:" (fs/get-in unified-with-comp '(:1 :synsem :cat))))
                      (log/error (str "2 cat:" (fs/get-in unified-with-comp '(:2 :synsem :cat))))
                      (log/error (str "GF: fail? unified-with-comp: " (fs/fail? unified-with-comp)))
;              (log/error (str "Generation failed: parent: " unified-parent))
;              (log/error (str "Generation failed: comp: " random-comp))
                      {:error "Generation failed in (generate-with-head-and-comp)"
                       :unified-parent :cannot-show-unified-parent-for-now
                       :comp :cannot-show-random-comp-for-now})
;               :comp random-comp})
                    (fs/copy-trunc result)))))))))))))

(defn random-sentence []
  (let [rules (list
               gram/s-present
               gram/s-future
               gram/s-imperfetto
               )]
    (generate (nth rules (rand-int (.size rules))))))

(defn random-sentences [n]
  (repeatedly n (fn [] (random-sentence))))

(defn random-nbar [n]
  (take n (over2 gram/nbar
                 (shuffle lex/nouns)
                 (shuffle lex/adjectives))))

(defn random-np [n]
  (take n (over2 gram/np
                 (shuffle lex/determiners)
                 (random-nbar n))))

(defn random-sent [n]
  (let [subj (take (* n 10) (shuffle (random-np (* n 20))))]
    (take n (over2 (shuffle (list gram/s-future gram/s-present gram/s-imperfetto))
                   subj
                   (shuffle lex/intransitive-verbs)))))

(defn speed-test [ & times]
  "TODO: show benchmark results and statistics (min,max,95%tile,stddev,etc)"
  (let [times (if (first times) (first times) 10)]
    (dotimes [n times] (time (random-sentence)))))

;;(if false
;;  (def skeleton (first (over2 gram/s-present (over2 gram/np :top (over2 gram/nbar :top :top))
;;                              (over2 gram/vp :top (over2 gram/np :top :top))))))

(declare head-by-comps)

(defn heads-by-comps [parent heads comps]
  (if (not (empty? heads))
    (if (fs/fail? (first heads))
      (heads-by-comps parent (rest heads) comps)
      (do
        (log/debug (str "head: " (fo (first heads))))
        (lazy-cat
         (let [probe (take 1 comps)
             logit (log/debug "right: " (fo probe))
               ]
         (head-by-comps parent (first heads) comps))
         (heads-by-comps parent (rest heads) comps))))))

(defn hc-expands [parent]
  (map (fn [expansion]
         (let [head (eval-symbol (:head expansion))
               comp (eval-symbol (:comp expansion))]
           {:head (if (seq? head) (shuffle
                                   (remove #(fs/fail? %)
                                           (map (fn [head-candidate] (unify head-candidate (fs/get-in parent '(:head))))
                                                head)))
                      (list (unify (fs/get-in parent '(:head)) head)))
            :comp (if (seq? comp) (shuffle
                                   (remove #(fs/fail? %)
                                           (map (fn [comp-candidate] (unify comp-candidate (fs/get-in parent '(:comp))))
                                                comp)))
                      (list (unify (fs/get-in parent '(:comp)) comp)))}))
       (shuffle (vals (fs/get-in parent '(:extend))))))

(defn generate-all-from-expands [parent expands]
  (if (not (empty? expands))
    (lazy-cat
     (heads-by-comps parent
           (:head (first expands))
           (:comp (first expands)))
     (generate-all-from-expands parent (rest expands)))))

(defn expand [parent]
  (if (= :not-exists (fs/get-in parent '(:comment-plaintext) :not-exists))
    (log/debug (str "terminal: no comment.")))
  (if (fs/get-in parent '(:comment-plaintext))
    (log/debug (str "expanding: " (fs/get-in parent '(:comment-plaintext)))))
  (cond (= :not-exists (fs/get-in parent '(:comment-plaintext) :not-exists))
        nil

        (and
         (or
          (nil? (fs/get-in parent '(:italian)))
          (and (map? (fs/get-in parent '(:italian :a)))
               (nil? (fs/get-in parent '(:italian :a :italian)))))
         (or
          (= (fs/get-in parent '(:comment-plaintext)) "np -> det (noun or nbar)")
          (= (fs/get-in parent '(:comment-plaintext)) "nbar -> noun adj")
          (= (fs/get-in parent '(:comment-plaintext)) "s -> ..")
          (= (fs/get-in parent '(:comment-plaintext)) "vp -> head comp")))
        (do
          (log/info (str "parent: " (fs/get-in parent '(:comment-plaintext)) " -> " (fo parent)))
          (generate-all-from-expands parent (hc-expands parent)))

        :else nil))

(defn head-by-comps [parent head comps]
  "Returns a lazy sequence of expressions generated by adjoining (head,comp_i) to parent, for all
   comp_i in comps."
  (if (not (empty? comps))
    (let [comp (first comps)
          head-expand (expand head)
          comp-expand (expand comp)]
      (cond (and (not (nil? head-expand))
                 (not (nil? comp-expand)))
            (lazy-cat
             (heads-by-comps parent head-expand comp-expand)
             (heads-by-comps parent head-expand (rest comps)))

            (not (nil? head-expand))
            (lazy-cat
             (heads-by-comps parent head-expand (list comp))
             (heads-by-comps parent head-expand (rest comps)))

            (not (nil? comp-expand))
            (lazy-cat
             (heads-by-comps parent (list head) comp-expand)
             (heads-by-comps parent (list head) (rest comps)))

            :else
            (remove #(fs/fail? %)
                    (lazy-seq
                     (cons (unify-lr-hc parent head comp)
                           (head-by-comps parent head (rest comps)))))))))


