(ns italianverbs.morphology.espanol
  (:refer-clojure :exclude [get-in merge resolve]))

(require '[clojure.core :as core])
(require '[clojure.string :as string])
(require '[clojure.string :refer (trim)])
(require '[clojure.tools.logging :as log])
(require '[italianverbs.stringutils :refer :all])
(require '[italianverbs.unify :refer (copy dissoc-paths fail? get-in merge ref? unifyc)])

(defn phrase-is-finished? [phrase]
  (cond
   (string? phrase) true
   (map? phrase)
   (or (phrase-is-finished? (get-in phrase '(:espanol)))
       (and (phrase-is-finished? (get-in phrase '(:a)))
            (phrase-is-finished? (get-in phrase '(:b)))))
   :else false))

(defn suffix-of [word]
  "compute the final character given a lexical entry and agreement info in :agr."
  (let [suffix (cond

                (and (= (get-in word '(:obj-agr :gender)) :fem)
                     (= (get-in word '(:obj-agr :number)) :sing))
                "a"

                (and (= (get-in word '(:obj-agr :gender)) :fem)
                     (= (get-in word '(:obj-agr :number)) :plur))
                "e"

                (= (get-in word '(:obj-agr :number)) :plur)
                "i"

                (and (= (get-in word '(:agr :gender)) :fem)
                     (= (get-in word '(:agr :number)) :sing)
                     (= (get-in word '(:essere)) true))
                "a"

                (and (= (get-in word '(:agr :gender)) :fem)
                     (= (get-in word '(:agr :number)) :plur)
                     (= (get-in word '(:essere)) true))
                "e"

                (and (= (get-in word '(:agr :number)) :plur)
                     (= (get-in word '(:essere)) true))
                "i"

                true
                "o"

                )]
    suffix))

(declare get-string)

(defn stem-per-futuro [infinitive drop-e]
  "_infinitive_ should be a string (italian verb infinitive form)"
  (cond
   (re-find #"giare$" infinitive)
   (string/replace infinitive #"giare$" "ger")

   (re-find #"ciare$" infinitive)
   (string/replace infinitive #"ciare$" "cer")

   (and
    (= true drop-e)
    (re-find #"are$" infinitive))
   (string/replace infinitive #"are$" "r")

   (re-find #"are$" infinitive)
   (string/replace infinitive #"are$" "er")

   (and
    (= true drop-e)
    (re-find #"ere$" infinitive))
   (string/replace infinitive #"ere$" "r")

   (re-find #"ere$" infinitive)
   (string/replace infinitive #"ere$" "er")

   (re-find #"ire$" infinitive)
   (string/replace infinitive #"ire$" "ir")

   true
   infinitive))

(defn stem-per-imperfetto [infinitive]
  "_infinitive_ should be a string (italian verb infinitive form)"
  (cond
   (re-find #"re$" infinitive)
   (string/replace infinitive #"re$" "")
   true
   infinitive))

;; TODO: this is an overly huge method that needs to be rewritten to be easier to understand and maintain.
(defn get-string-1 [word & [:usted usted :tú tu :vosotros vosotros :ustedes ustedes]]
  (if (seq? word)
    (map (string/join " " #(get-string-1 %))
         word)
  (let [person (get-in word '(:agr :person))
        number (get-in word '(:agr :number))
        info (log/debug "get-string-1: input word: " word)
        vosotros (if vosotros vosotros false)
        ustedes (if ustedes ustedes false)
        tú (if tú ´tu false)
        usted (if usted usted false)]

    (log/debug (str "word's a is a string? " (get-in word '(:a)) " => " (string? (get-in word '(:a)))))
    (log/debug (str "word's b is a map? " (get-in word '(:b)) " => " (map? (get-in word '(:b)))))

    (log/debug (str "word's a italian is a string? " (get-in word '(:a :espanol)) " => " (string? (get-in word '(:a :espanol)))))

    (cond

     (= word :top) ".."

     (ref? word)
     (get-string-1 @word)

     ;; TODO: this is a special case that should be handled below instead
     ;; of forcing every input to go through this check.
     (= word {:initial false})
     ".."
     (= word {:initial true})
     ".."

     (and (string? (get-in word '(:a)))
          (string? (get-in word '(:b))))
     (get-string (get-in word '(:a))
                 (get-in word '(:b)))

     (and (string? (get-in word '(:a)))
          (map? (get-in word '(:b))))
     (get-string (get-in word '(:a))
                 (get-in word '(:b)))

     (and (map? (get-in word '(:a)))
          (map? (get-in word '(:b))))
     (get-string
      (get-in word '(:a))
      (get-in word '(:b)))

     ;; TODO: this rule is pre-empting all of the following rules
     ;; that look in :a and :b. Either remove those following rules
     ;; if they are redundant and not needed, or move this general rule
     ;; below the following rules.
     (and (not (= :none (get-in word '(:a) :none)))
          (not (= :none (get-in word '(:b) :none))))
     (get-string (get-in word '(:a))
                 (get-in word '(:b)))

     (and
      (string? (get-in word '(:a :espanol)))
      (string? (get-in word '(:b :espanol)))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      )
     (str (string/trim (get-in word '(:a :espanol)))
          " "
          (string/trim (get-in word '(:b :espanol))))

     (and
      (string? (get-in word '(:a)))
      (string? (get-in word '(:b :espanol)))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      )
     (str (string/trim (get-in word '(:a)))
          " "
          (string/trim (get-in word '(:b :espanol))))

     (and
      (string? (get-in word '(:a :espanol)))
      (get-in word '(:a :espanol))
      (or (= :none (get-in word '(:b :agr :number) :none))
          (= :top (get-in word '(:b :agr :number) :none)))
      (= (get-in word '(:a :infl)) :top))
     (string/trim (str (get-in word '(:a :espanol))
                 " " (get-string-1 (get-in word '(:b)))))

     (= true (get-in word [:exception]))
     (get-in word [:espanol])

     ;; TODO: all of the rules that handle exceptions should be removed:
     ;; exceptions are dealt with at compile-time now, via italianverbs.lexicon.espanol/exception-generator

     ;; handle lexical exceptions (plural feminine adjectives):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:cat)) :adjective)
      (string? (get-in word '(:fem :plur))))
     (get-in word '(:fem :plur))

     ;; handle lexical exceptions (plural feminine adjectives):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:cat)) :adjective)
      (string? (get-in word '(:fem :plur))))
     (get-in word '(:fem :plur))

     ;; handle lexical exceptions (plural masculine adjectives):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:cat)) :adjective)
      (string? (get-in word '(:masc :plur))))
     (get-in word '(:masc :plur))

     (and
      (string? (get-in word [:espanol]))
      (or (= (get-in word [:agr :gender]) :masc)
          (= (get-in word [:agr :gender]) :top))
      (= (get-in word [:agr :number]) :plur)
      (= (get-in word [:cat]) :adjective))
     (string/replace (get-in word '[:espanol])
                     #"[eo]$" "i") ;; nero => neri

     (and
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)) :adjective))
     (string/replace (get-in word '(:espanol))
                     #"[eo]$" "e") ;; nero => nere

     ;; handle lexical exceptions (plural nouns):
     (and
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)) :noun)
      (string? (get-in word '(:plur))))
     (get-in word '(:plur))

     ;; regular masculine nouns
     (and
      (string? (get-in word [:espanol]))
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:agr :number)) :plur)
      (= :noun (get-in word '(:cat)))
      (get-in word '(:espanol)))
     (string/replace (get-in word '(:espanol))
                     #"[eo]$" "i") ;; dottore => dottori; medico => medici

     ;; regular feminine nouns
     (and
      (string? (get-in word [:espanol]))
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)))
      (get-in word '(:espanol)))
     (string/replace (get-in word '(:espanol))
                     #"[a]$" "e") ;; donna => donne

     ;; TODO: move this down to other adjectives.
     ;; this was moved up here to avoid
     ;; another rule from matching it.
     (and
      (string? (get-in word [:espanol]))
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat)) :adjective))
     (string/replace (get-in word '(:espanol))
                     #"[eo]$" "e") ;; nero => nere

     (and
      (string? (get-in word [:espanol]))
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :sing)
      (= (get-in word '(:cat)) :adjective))
     (string/replace (get-in word '(:espanol))
                     #"[eo]$" "a") ;; nero => nera

     (and (= :infinitive (get-in word '(:infl)))
          (string? (get-in word '(:espanol))))
     (get-in word '(:espanol))
     
     (and
      (get-in word '(:a))
      (get-in word '(:b))
      true) (str
             (trim (get-string-1 (get-in word '(:a)))) " "
             (trim (get-string-1 (get-in word '(:b)))))

     ;; TODO: do not use brackets: if there's an error about there being
     ;; not enough information, throw an exception explicitly.
     ;; return the irregular form in square brackets, indicating that there's
     ;; not enough information to conjugate the verb.
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:passato))
          (get-in word '(:essere) true)
          (or (= :notfound (get-in word '(:agr :number) :notfound))
              (= :top (get-in word '(:agr :number)))))
     ;; 'nei': not enough information.
     (do
       (log/warn (str "not enough agreement specified to conjugate: " (get-in word '(:passato)) " (irreg past)]"))
       (get-in word '(:passato)))

     ;; TODO: do not use brackets: if there's an error about there being
     ;; regular passato prossimo and essere-verb => NEI (not enough information): defer conjugation and keep as a map.
     (and (= :past (get-in word '(:infl)))
          (= (get-in word '(:essere)) true)
          (or (= :notfound (get-in word '(:agr :number) :notfound))
              (= :top (get-in word '(:agr :number)))))
     ;; 'nei': not enough information.
     (do
       (log/warn (str "not enough agreement specified to conjugate: " (get-in word '(:passato)) " (past)]"))
       (str (get-in word [:espanol]) " (past)"))

     ;; conjugate irregular passato: option 1) using :passato-stem
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:passato-stem)))
     (let [irregular-passato (get-in word '(:passato-stem))]
       (str irregular-passato (suffix-of word)))

     ;; conjugate irregular passato: option 2) using :passato
     (and (= :past (get-in word '(:infl)))
          (get-in word '(:passato)))
     (let [irregular-passato (get-in word '(:passato))
           butlast (nth (re-find #"(.*).$" irregular-passato) 1)]
       (str butlast (suffix-of word)))

     ;; conjugate regular passato
     (and (= :past (get-in word '(:infl)))
          (string? (get-in word '(:espanol))))
     (let [infinitive (get-in word '(:espanol))
           ar-type (try (re-find #"are$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive)))))
           er-type (re-find #"ere$" infinitive)
           ir-type (re-find #"ire$" infinitive)
           stem (string/replace infinitive #"[iae]re$" "")
           last-stem-char-is-i (re-find #"i$" stem)

           ;; for passato prossimo, the last char depends on gender and number, if an essere-verb.
           suffix (suffix-of word)

           vosotros false ;; this is dialect-dependent: only certain Spanish-speaking dialects use this.
           ustedes false  ;; this is dialect-dependent: only certain Spanish-speaking dialects use this.
           ]

       (cond
        (and (= person :1st) (= number :sing))
        (str stem "é")

        (and (= person :2nd) (= number :sing) ar-type)
        (str stem "aste")

        (and (= person :2nd) (= number :sing) (or ir-type er-type))
        (str stem "iste")
       
        (and (= person :3rd) (= number :sing) (or ir-type er-type))
        (str stem "ó")

        (and (= person :3rd) (= number :sing) ar-type)
        (str stem "ió")


        (and (= person :1st) (= number :plur) ar-type)
        (str stem "amos")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "imos")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "imos")
       
        ;; <second person plural past>

        (and (= person :2nd) (= number :plur) ar-type vosotros)
        (str stem "asteis")

        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "isteis")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "isteis")


        (and (= person :2nd) (= number :plur) ar-type ustedes)
        (str stem "aron")

        (and (= person :2nd) (= number :plur) er-type ustedes)
        (str stem "ieron")

        (and (= person :2nd) (= number :plur) ir-type ustedes)
        (str stem "ieron")

        ;; </second person plural past>

        ;; <third person plural past>
        (and (= person :3rd) (= number :plur)
             ar-type)
        (str stem "aron")
        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "ieron")

        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "ieron")

        ;; </third person plural past>

        :else
        (str "non so cosa fare"))))


     (and (= (get-in word '(:infl)) :present)
          (= person :1st) (= number :sing)
          (string? (get-in word '(:present :1sing))))
     (get-in word '(:present :1sing))

     (and (= (get-in word '(:infl)) :present)
          (= person :2nd) (= number :sing)
          (string? (get-in word '(:present :2sing))))
     (get-in word '(:present :2sing))

     (and (= (get-in word '(:infl)) :present)
          (= person :3rd) (= number :sing)
          (string? (get-in word '(:present :3sing))))
     (get-in word '(:present :3sing))

     (and (= (get-in word '(:infl)) :present)
          (= person :1st) (= number :plur)
          (string? (get-in word '(:present :1plur))))
     (get-in word '(:present :1plur))

     (and (= (get-in word '(:infl)) :present)
          (= person :2nd) (= number :plur)
          (string? (get-in word '(:present :2plur))))
     (get-in word '(:present :2plur))

     (and (= (get-in word '(:infl)) :present)
          (= person :3rd) (= number :plur)
          (string? (get-in word '(:present :3plur))))
     (get-in word '(:present :3plur))

     (and
      (= (get-in word '(:infl)) :present)
      (string? (get-in word '(:espanol))))
     (let [infinitive (get-in word '(:espanol))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]

       (cond
        (and (= person :1st) (= number :sing))
        (str stem "o")

        (and (= person :2nd) (= number :sing) ar-type (= false usted))
        (str stem "as")

        (and (= person :2nd) (= number :sing) ar-type usted)
        (str stem "a")

        (and (= person :2nd) (= number :sing) (or ir-type er-type) (= false usted))
        (str stem "es")

        (and (= person :2nd) (= number :sing) (or ir-type er-type) usted)
        (str stem "e")

        (and (= person :3rd) (= number :sing) ar-type)
        (str stem "a")
        (and (= person :3rd) (= number :sing) (or ir-type er-type))
        (str stem "e")

        (and (= person :1st) (= number :plur) ar-type)
        (str stem "amos")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "emos")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "imos")
       
        ;; <second person plural present>

        (and (= person :2nd) (= number :plur) ar-type vosotros)
        (str stem "ais")

        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "eis")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "ís")

        (and (= person :2nd) (= number :plur) ar-type ustedes)
        (str stem "an")

        (and (= person :2nd) (= number :plur) er-type ustedes)
        (str stem "en")

        (and (= person :2nd) (= number :plur) ir-type ustedes)
        (str stem "en")

        ;; </second person plural present>

        ;; <third person plural present>
        (and (= person :3rd) (= number :plur)
             ar-type)
        (str stem "an")
        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "en")
        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "en")

        ;; </third person plural present>

        :else
        "non so cosa fare"))

     (and
      (= (get-in word '(:infl)) :preterito)
      (string? (get-in word '(:espanol))))
     (let [infinitive (get-in word '(:espanol))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]

       (cond
        (and (= person :1nd) (= number :sing) ar-type)
        (str stem "é")

        (and (= person :1nd) (= number :sing) (or ir-type er-type))
        (str stem "í")

        (and (= person :2nd) (= number :sing) ar-type (= usted false))
        (str stem "aste")

        (and (= person :2nd) (= number :sing) (or ir-type er-type) (= usted false))
        (str stem "iste")

        (and (= person :2nd) (= number :sing) ar-type (= usted true))
        (str stem "ó")

        (and (= person :2nd) (= number :sing) (or ir-type er-type) (= usted true))
        (str stem "ió")
       
        (and (= person :3rd) (= number :sing) ar-type)
        (str stem "ó")

        (and (= person :3rd) (= number :sing) (or ir-type er-type))
        (str stem "ió")

        (and (= person :1st) (= number :plur) ar-type)
        (str stem "amos")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "emos")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "imos")      

        ;; <second person plural preterite>

        (and (= person :2nd) (= number :plur) ar-type vosotros)
        (str stem "asteis")

        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "isteis")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "isteis")


        (and (= person :2nd) (= number :plur) ar-type ustedes)
        (str stem "aron")

        (and (= person :2nd) (= number :plur) er-type ustedes)
        (str stem "ieron")

        (and (= person :2nd) (= number :plur) ir-type ustedes)
        (str stem "ieron")

        ;; </second person plural preterite>

        ;; <third person plural preterite>
        (and (= person :3rd) (= number :plur)
             ar-type)
        (str stem "aron")
        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "ieron")
        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "ieron")

        ;; </third person plural preterite>

        :else
        "non so cosa fare"))

     (and
      (= (get-in word '(:infl)) :imperfecto)
      (string? (get-in word '(:espanol))))
     (let [infinitive (get-in word '(:espanol))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           vosotros false ;; TODO: dialect dependent: make morphology dialect-aware.
           ustedes false;; TODO: dialect dependent: same comment applies.
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]

       (cond
        (and (= person :1nd) (= number :sing) ar-type)
        (str stem "aba")

        (and (= person :1nd) (= number :sing) (or ir-type er-type))
        (str stem "ía")

        (and (= person :2nd) (= number :sing) ar-type)
        (str stem "abas")

        (and (= person :2nd) (= number :sing) (or ir-type er-type))
        (str stem "ías")
       
        (and (= person :2nd) (= number :sing) ar-type (= usted true))
        (str stem "aba")

        (and (= person :2nd) (= number :sing) (or ir-type er-type) (= usted true))
        (str stem "ía")

        (and (= person :3rd) (= number :sing) ar-type)
        (str stem "aba")

        (and (= person :3rd) (= number :sing) (or ir-type er-type))
        (str stem "ía")

        (and (= person :1st) (= number :plur) ar-type)
        (str stem "abamos")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "íamos")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "íamos")
       
        ;; <second person plural imperfecto>

        (and (= person :2nd) (= number :plur) ar-type vosotros)
        (str stem "abais")

        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "íais")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "íais")

        (and (= person :2nd) (= number :plur) ar-type ustedes)
        (str stem "aban")

        (and (= person :2nd) (= number :plur) er-type ustedes)
        (str stem "ían")

        (and (= person :2nd) (= number :plur) ir-type ustedes)
        (str stem "ían")

        ;; </second person plural imperfecto>

        ;; <third person plural imperfecto>
        (and (= person :3rd) (= number :plur)
             ar-type)
        (str stem "aban")

        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "ían")

        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "ían")

        ;; </third person plural imperfecto>

        :else
        "non so cosa fare"))

     (and
      (= (get-in word '(:infl)) :future)
      (string? (get-in word '(:espanol))))
     (let [infinitive (get-in word '(:espanol))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]

       (cond
        (and (= person :1nd) (= number :sing) ar-type)
        (str stem "aré")
        (and (= person :1nd) (= number :sing) er-type)
        (str stem "eré")
        (and (= person :1nd) (= number :sing) ir-type)
        (str stem "iré")

        (and (= person :2nd) (= number :sing) ar-type)
        (str stem "aras")
        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "iras")
        (and (= person :2nd) (= number :sing) er-type)
        (str stem "eras")

        (and (= person :2nd) (= number :sing) ar-type (= usted true))
        (str stem "erá")
        (and (= person :2nd) (= number :sing) ir-type (= usted true))
        (str stem "irá")
        (and (= person :2nd) (= number :sing) er-type (= usted true))
        (str stem "erá")

        (and (= person :3rd) (= number :sing) ar-type)
        (str stem "erá")
        (and (= person :3rd) (= number :sing) ir-type)
        (str stem "irá")
        (and (= person :3rd) (= number :sing) er-type)
        (str stem "erá")

        (and (= person :1st) (= number :plur) ar-type)
        (str stem "aremos")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "eremos")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "iremos")
       
        ;; <second person plural future>

        (and (= person :2nd) (= number :plur) ar-type vosotros)
        (str stem "arais")

        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "erais")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "irais")

        (and (= person :2nd) (= number :plur) ar-type ustedes)
        (str stem "aran")

        (and (= person :2nd) (= number :plur) er-type ustedes)
        (str stem "eran")

        (and (= person :2nd) (= number :plur) ir-type ustedes)
        (str stem "iran")

        ;; </second person plural future>

        ;; <third person plural future>
        (and (= person :3rd) (= number :plur)
             ar-type)
        (str stem "aran")

        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "eran")

        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "iran")

        ;; </third person plural future>

        :else
        "non so cosa fare"))

     (and
      (= (get-in word '(:infl)) :conditional)
      (string? (get-in word '(:espanol))))
     (let [infinitive (get-in word '(:espanol))
           ar-type (try (re-find #"ar$" infinitive)
                         (catch Exception e
                           (throw (Exception. (str "Can't regex-find on non-string: " infinitive " from word: " word)))))
           er-type (re-find #"er$" infinitive)
           ir-type (re-find #"ir$" infinitive)
           stem (string/replace infinitive #"[iae]r$" "")
           last-stem-char-is-i (re-find #"ir$" infinitive)
           last-stem-char-is-e (re-find #"er$" infinitive)
           is-care-or-gare? (re-find #"[cg]ar$" infinitive)
           person (get-in word '(:agr :person))
           number (get-in word '(:agr :number))]

       (cond
        (and (= person :1nd) (= number :sing) ar-type)
        (str stem "aría")
        (and (= person :1nd) (= number :sing) er-type)
        (str stem "ería")
        (and (= person :1nd) (= number :sing) ir-type)
        (str stem "iría")

        (and (= person :2nd) (= number :sing) ar-type)
        (str stem "arías")
        (and (= person :2nd) (= number :sing) ir-type)
        (str stem "erías")
        (and (= person :2nd) (= number :sing) er-type)
        (str stem "irías")

        (and (= person :2nd) (= number :sing) ar-type (= usted true))
        (str stem "aría")
        (and (= person :2nd) (= number :sing) ir-type (= usted true))
        (str stem "ería")
        (and (= person :2nd) (= number :sing) er-type (= usted true))
        (str stem "iría")

        (and (= person :3rd) (= number :sing) ar-type)
        (str stem "aría")
        (and (= person :3rd) (= number :sing) ir-type)
        (str stem "ería")
        (and (= person :3rd) (= number :sing) er-type)
        (str stem "iría")

        (and (= person :1st) (= number :plur) ar-type)
        (str stem "aríamos")

        (and (= person :1st) (= number :plur) er-type)
        (str stem "eríamos")

        (and (= person :1st) (= number :plur) ir-type)
        (str stem "iríamos")
       
        ;; <second person plural conditional>

        (and (= person :2nd) (= number :plur) ar-type vosotros)
        (str stem "aríais")

        (and (= person :2nd) (= number :plur) er-type vosotros)
        (str stem "eríais")

        (and (= person :2nd) (= number :plur) ir-type vosotros)
        (str stem "iríais")

        (and (= person :2nd) (= number :plur) ar-type ustedes)
        (str stem "arían")

        (and (= person :2nd) (= number :plur) er-type ustedes)
        (str stem "erían")

        (and (= person :2nd) (= number :plur) ir-type ustedes)
        (str stem "irían")

        ;; </second person plural conditional>

        ;; <third person plural conditional>
        (and (= person :3rd) (= number :plur)
             ar-type)
        (str stem "arían")

        (and (= person :3rd) (= number :plur)
             er-type)
        (str stem "erían")

        (and (= person :3rd) (= number :plur)
             ir-type)
        (str stem "irían")

        ;; </third person plural conditional>

        :else
        "non so cosa fare"))

     (and
      (string? (get-in word '(:espanol)))
      (= :top (get-in word '(:agr :sing) :top)))
     (str (get-in word '(:espanol)))


     (= (get-in word '(:infl)) :top)
     (str (get-in word '(:espanol)) )

     (and
      (get-in word '(:a))
      (get-in word '(:b)))
     (get-string
      (get-in word '(:a))
      (get-in word '(:b)))

     (= (get-in word '(:a)) :top)
     (str
      ".." " " (get-string-1 (get-in word '(:b))))

     (and
      (= (get-in word '(:b)) :top)
      (string? (get-string-1 (get-in word '(:a)))))
     (str
      (get-string-1 (get-in word '(:a)))
      " " "..")

     (and
      (= (get-in word '(:b)) :top)
      (string? (get-in word '(:a :espanol))))
     (str
      (get-string-1 (get-in word '(:a :espanol)))
      " " "..")


     ;; TODO: remove support for deprecated :root.
     (and
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:agr :number)) :sing)
      (= (get-in word '(:cat)) :noun)
      (get-in word '(:root)))
     (get-in word '(:root))

     (and
      (= (get-in word '(:agr :gender)) :fem)
      (= (get-in word '(:agr :number)) :sing)
      (= (get-in word '(:cat)) :noun))
     (get-in word '(:espanol))

     ;; deprecated: remove support for :root.
     (and
      (= (get-in word '(:agr :gender)) :masc)
      (= (get-in word '(:agr :number)) :plur)
      (= (get-in word '(:cat) :noun))
      (get-in word '(:root)))
     (string/replace (get-in word '(:root))
                     #"[eo]$" "i") ;; dottore => dottori; medico => medici

   ;; deprecated: TODO: remove this
   (and
    (= (get-in word '(:agr :gender)) :fem)
    (= (get-in word '(:agr :number)) :plur)
    (= (get-in word '(:cat)) :noun)
    (get-in word '(:root)))
   (string/replace (get-in word '(:root))
                   #"[a]$" "e") ;; donna => donne


   ;; deprecated: TODO: remove support for :root.
   (and
    (= (get-in word '(:agr :gender)) :fem)
    (= (get-in word '(:agr :number)) :sing)
    (= (get-in word '(:cat)) :noun)
    (string? (get-in word '(:root))))
   (get-in word '(:root))

   (and
    (= (get-in word '(:agr :gender)) :masc)
    (= (get-in word '(:agr :number)) :sing)
    (= (get-in word '(:cat) :adjective)))
   (get-in word '(:espanol)) ;; nero

   (and
    (= (get-in word '(:agr :gender)) :masc)
    (= (get-in word '(:agr :number)) :plur)
    (= (get-in word '(:cat)) :adjective)
    ;; handle lexical exceptions.
    (string? (get-in word '(:masc :plur))))
   (get-in word '(:masc :plur))


   (and
    (= (get-in word '(:agr :gender)) :fem)
    (= (get-in word '(:agr :number)) :plur)
    (= (get-in word '(:cat)) :adjective)
    ;; handle lexical exceptions.
    (string? (get-in word '(:fem :plur))))
   (get-in word '(:fem :plur))

   (string? (get-in word '(:espanol)))
   (get-in word '(:espanol))

   (or
    (not (= :none (get-in word '(:a) :none)))
    (not (= :none (get-in word '(:b) :none))))
   (get-string (get-in word '(:a))
                (get-in word '(:b)))

   (and (map? word)
        (nil? (:espanol word)))
   ".."

   (or
    (= (get-in word '(:case)) {:not :acc})
    (= (get-in word '(:agr)) :top))
   ".."

   ;; TODO: throw exception rather than returning _word_, which is a map or something else unprintable.
   ;; in other words, if we've gotten this far, it's a bug.
   :else
   word)
  ))

(defn get-string [a & [ b ]]
  (cond (and (nil? b)
             (seq? a))
        (let [result (get-string-1 a)]
          (if (string? result)
            (trim result)
            result))
        
        true
        (let [a (if (nil? a) "" a)
              b (if (nil? b) "" b)
              a (get-string-1 a)
              b (get-string-1 b)
              info-a (log/debug (str "get-string: a: " a))
              info-b (if b (log/debug (str "get-string: b: " b)))

              it-b (log/debug "it-a is string? " (string? (get-in a '(:espanol))))
              it-b (log/debug "it-b is string? " (string? (get-in b '(:espanol))))

              cat-a (log/debug (str "cat a:" (get-in a '(:cat))))
              cat-b (log/debug (str "cat b:" (get-in b '(:cat))))

              ]
          (cond

           (and (= a "i")
                (string? (get-in b '(:espanol)))
                (re-find #"^[aeiou]" (get-in b '(:espanol))))
           (trim (str "gli " b))

           ;; TODO: cleanup & remove.
           (and false ;; going to throw out this logic: will use :initial and rule schemata instead.
                (= :verb (get-in a '(:cat)))
                (= :noun (get-in b '(:cat)))
                (= :acc (get-in b '(:case))))
           ;; flip order in this case:
           ;; i.e. "vedo ti" => "ti vedo".
           {:a (if (nil? b) :top b)
            :b (if (nil? a) :top a)}

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^il (mio|tio|suo|nostro|vostro|loro)\b" b))
           (str a " " (string/replace b #"^il " ""))

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^la (mia|tia|sua|nostra|vostra|loro)\b" b))
           (str a " " (string/replace b #"^la " ""))

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^i (miei|tuoi|suoi|nostri|vostri|loro)\b" b))
           (str a " " (string/replace b #"^i " ""))

           (and (string? a)
                (= a "di")
                (string? b)
                (re-find #"^le (mie|tue|sue|nostre|vostre|loro)\b" b))
           (str a " " (string/replace b #"^le " ""))

           (and (= a "di i")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "degli " b)

           (and (= a "di i")
                (string? b)
                (re-find #"^s[t]" b))
           (str "degli " b)

           (and (= a "di i")
                (string? b))
           (str "dei " b)

           (and (= (get-in a '(:espanol)) "di i")
                (string? b))
           (str "dei " b)

           (and (= (get-in a '(:espanol)) "di i")
                (string? (get-in b '(:espanol))))
           (str "dei " (get-string-1 (get-in b '(:espanol))))

           (and (= a "di il")
                (string? b))
           (get-string "del" b)  ;; allows this to feed next rule:

           (and (= a "del")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "dell'" b)

           (and (= a "di la")
                (string? b))
           (get-string "della" b) ;; allows this to feed next rule:

           (and (= a "della")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "dell'" b)

           (and (= a "di le")
                (string? b))
           (str "delle " b)

           (and (= a "i")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "gli " b)

           (and (= a "i")
                (string? (get-in b '(:espanol)))
                (re-find #"^[aeiou]" (get-in b '(:espanol))))
           (str "gli " b)

           (and (= a "i")
                (string? b)
                (re-find #"^s[t]" b))
           (str "gli " b)

           ;; 1),2),3) handle e.g. "io lo ho visto" => "io l'ho visto"
           ;; 1)
           (and (= a "mi")
                (string? b)
                (re-find #"^[aeiouh]" b))
           (str "m'" b)
           ;; 2)
           (and (= a "ti")
                (string? b)
                (re-find #"^[aeiouh]" b))
           (str "t'" b)
           ;; 3)
           (and (string? a)
                (re-find #"^l[ao]$" a)
                (string? b)
                (re-find #"^[aeiouh]" b))
           (str "l'" b)

           ;; 4) handle e.g. "aiutari + ti" => "aiutarti"
           (and (string? a)
                (or (re-find #"are$" a)
                    (re-find #"ere$" a)
                    (re-find #"ire$" a))
                (or (= b "ci")
                    (= b "mi")
                    (= b "la")
                    (= b "le")
                    (= b "li")
                    (= b "lo")
                    (= b "ti")
                    (= b "vi")))
           (str (string/replace a #"[e]$" "")
                b)
           
           (and (= a "un")
                (string? b)
                (re-find #"^s[t]" b))
           (str "uno " b)

           (and (= a "una")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "un'" b)

           (and (= a "il")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "l'" b)

           (and (= a "il")
                (string? b)
                (re-find #"^s[ct]" b))
           (str "lo " b)

           (and (= a "la")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "l'" b)

           (and (= a "quell[ao]")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "quell'" b)

           (and (= a "quelli")
                (string? b)
                (re-find #"^(st|sc|[aeiou])" b))
           (str "quegli " b)

           (and (= a "quest[aeio]")
                (string? b)
                (re-find #"^[aeiou]" b))
           (str "quest'" b)

           ;; prepositional phrases
           (and (= a "a")
                (string? b)
                (re-find #"^il " b))
           (str "al " (string/replace b #"^il " ""))

           (and (= a "a")
                (string? b)
                (re-find #"^i " b))
           (str "ai " (string/replace b #"^i " ""))

           (and (= a "a")
                (string? b)
                (re-find #"^le " b))
           (str "alle " (string/replace b #"^le " ""))

           (and (= a "a")
                (string? b)
                (re-find #"^la " b))
           (str "alla " (string/replace b #"^la " ""))
     
           (and (string? a) (string? b))
           (trim (str a " " b))
           
           (and (string? a) (string? (get-in b '(:espanol))))
           (trim (str a " " (get-in b '(:espanol))))
           
           (and (string? (get-in a '(:espanol)))
                (string? b))
           (trim (str (get-in a '(:espanol)) " " b))
           
           (and (string? a)
                (map? b))
           (trim (str a " .."))

           (and (string? b)
                (map? a))
           (trim (str " .." b))

           true
           {:a (if (nil? a) :top a)
            :b (if (nil? b) :top b)}))))

(declare fo-ps-it)

(defn fo-ps [expr]
  "show the phrase-structure of a phrase structure tree, e.g [hh21 'mangiare (to eat)' [cc10 'il (the)' 'pane(bread)']]"
  ;; [:first = {:head,:comp}] will not yet be found in expr, so this head-first? will always be false.
  (let [head-first? (= :head (get-in expr [:first]))]
    (cond

     (and
      (or (set? expr)
          (seq? expr)
          (vector? expr))
      (empty? expr))
     (str "")


     (and
      (or (set? expr)
          (seq? expr)
          (vector? expr))
      (not (empty? expr)))

     ;; expr is a sequence of some kind. Assume each element is a phrase structure tree and show each.
     (map (fn [each]
            (fo-ps each))
          expr)

     (and (map? expr)
          (:espanol expr))
     (fo-ps-it (:espanol expr))

     (and (map? expr)
          (:rule expr)
          (= (get-in expr '(:espanol :a))
             (get-in expr '(:comp :espanol))))
     ;; complement first
     (str "[" (:rule expr) " "
          (fo-ps (get-in expr '(:comp)))
          " "
          (fo-ps (get-in expr '(:head)))
          "]")

     (and (map? expr)
          (:rule expr))
     ;; head first ('else' case of above.)
     (str "[" (:rule expr) " "
          (fo-ps (get-in expr '(:head)))
          " "
          (fo-ps (get-in expr '(:comp)))
          "]")

     (and (map? expr)
          (:comment expr)
          (= (get-in expr '(:espanol :a))
             (get-in expr '(:comp :espanol))))
     ;; complement first
     (str "[" (:comment expr) " "
          (fo-ps (get-in expr '(:comp)))
          " "
          (fo-ps (get-in expr '(:head)))
          "]")

     (and (map? expr)
          (:comment expr))
     ;; head first ('else' case of above.)
     (str "[" (:comment expr) " "
          (fo-ps (get-in expr '(:head)))
          " "
          (fo-ps (get-in expr '(:comp)))
          "]")

     (and
      (map? expr)
      (:espanol expr))
     (get-string-1 (get-in expr '(:espanol)))

     true
     expr)))

(defn stem-per-passato-prossimo [infinitive]
  "_infinitive_ should be a string (italian verb infinitive form)"
  (string/replace infinitive #"^(.*)([aei])(re)$" (fn [[_ prefix vowel suffix]] (str prefix))))

(defn passato-prossimo [infinitive]
  (str (stem-per-passato-prossimo infinitive) "ato"))

;; allows reconstruction of the infinitive form from the inflected form
(def future-to-infinitive
  {
   ;; future
   #"ò$" 
   {:replace-with "e"
    :unify-with {:espanol {:infl :futuro
                            :agr {:number :sing
                                  :person :1st}}}}
   #"ai$" 
   {:replace-with "e"
    :unify-with {:espanol {:infl :futuro
                            :agr {:number :sing
                                  :person :2nd}}}}
   #"à$" 
   {:replace-with "e"
    :unify-with {:espanol {:infl :futuro
                            :agr {:number :sing
                                  :person :3rd}}}}
   #"emo$" 
   {:replace-with "e"
    :unify-with {:espanol {:infl :futuro
                            :agr {:number :plur
                                  :person :1st}}}}
   #"ete$" 
   {:replace-with "e"
    :unify-with {:espanol {:infl :futuro
                            :agr {:number :plur
                                  :person :2nd}}}}
   #"anno$" 
   {:replace-with "e"
    :unify-with {:espanol {:infl :futuro
                            :agr {:number :plur
                                  :person :3rd}}}}})

(def present-to-infinitive-ire
  {
   ;; present -ire
   #"o$"
   {:replace-with "ire"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :1st}}}}
   
   #"i$"
   {:replace-with "ire"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :2nd}}}}

   #"e$"
   {:replace-with "ire"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :3rd}}}}

   #"iamo$"
   {:replace-with "ire"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :1st}}}}

   #"ete$"
   {:replace-with "ire"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :2nd}}}}
   
   #"ono$"
   {:replace-with "ire"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :3rd}}}}})


(def present-to-infinitive-ere
  {;; present -ere
   #"o$"
   {:replace-with "ere"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :1st}}}}

   #"i$"
   {:replace-with "ere"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :2nd}}}}
   
   #"e$"
   {:replace-with "ere"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :3rd}}}}
   
   #"iamo$"
   {:replace-with "ere"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :1st}}}}

   #"ete$"
   {:replace-with "ere"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :2nd}}}}
   
   #"ano$"
   {:replace-with "ere"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :3rd}}}}})

(def present-to-infinitive-are
  {
   ;; present -are
   #"o$"
   {:replace-with "are"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :1st}}}}

   #"i$"
   {:replace-with "are"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :2nd}}}}

   #"e$"
   {:replace-with "are"
    :unify-with {:espanol {:infl :present
                            :agr {:number :sing
                                  :person :3rd}}}}
   
   #"iamo$"
   {:replace-with "are"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :1st}}}}

   #"ete$"
   {:replace-with "are"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :2nd}}}}

   #"ano$"
   {:replace-with "are"
    :unify-with {:espanol {:infl :present
                            :agr {:number :plur
                                  :person :3rd}}}}})

(def imperfect-to-infinitive-irreg1
  {
   ;; e.g.: "bevevo/bevevi/..etc" => "bere"
   #"vevo$"
   {:replace-with "re"
    :unify-with {:espanol {:infl :imperfetto
                            :agr {:number :sing
                                  :person :1st}}}}

   #"vevi$"
   {:replace-with "re"
    :unify-with {:espanol {:infl :imperfetto
                            :agr {:number :sing
                                  :person :2nd}}}}

   #"veva$"
   {:replace-with "re"
    :unify-with {:espanol {:infl :imperfetto
                            :agr {:number :sing
                                  :person :3rd}}}}
   })

(def past-to-infinitive
  {#"ato$"
   {:replace-with "are"
    :unify-with {:espanol {:infl :past}}}

   #"ito$"
   {:replace-with "ire"
    :unify-with {:espanol {:infl :past}}}

   #"uto$"
   {:replace-with "ere"
    :unify-with {:espanol {:infl :past}}}})

(def plural-to-singular-noun-fem-1
  {#"e$"
   {:replace-with "a"
    :unify-with {:synsem {:cat :noun
                          :agr {:gender :fem
                                :number :plur}}}}})

(def plural-to-singular-noun-masc-1
  {#"i$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :noun
                          :agr {:number :plur}}}}})

(def plural-to-singular-noun-masc-2 ;; e.g. "cani" -> "cane"
  {#"i$"
   {:replace-with "e"
    :unify-with {:synsem {:cat :noun
                          :agr {:number :plur}}}}})


(def plural-to-singular-adj-masc
  {#"i$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :adjective
                          :agr {:gender :masc
                                :number :plur}}}}})

(def plural-to-singular-adj-fem-sing
  {#"a$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :adjective
                          :agr {:gender :fem
                                :number :sing}}}}})

(def plural-to-singular-adj-fem-plur
  {#"e$"
   {:replace-with "o"
    :unify-with {:synsem {:cat :adjective
                          :agr {:gender :fem
                                :number :plur}}}}})

(def infinitive-to-infinitive
  {:identity
   {:unify-with {:synsem {:cat :verb
                          :infl :infinitive}}}})

(def lexical-noun-to-singular
  {:identity
   {:unify-with {:synsem {:cat :noun
                          :agr {:number :sing}}}}})

(defn analyze [surface-form lookup-fn]
  "return the map incorporating the lexical information about a surface form."
  (let [replace-pairs
        ;; Even though it's possible for more than one KV pair to have the same key:
        ;; e.g. plural-to-singular-noun-masc-1 and plural-to-singular-noun-masc-2 both have
        ;; #"i$", they are distinct as separate keys in this 'replace-pairs' hash, as they should be.
        (merge 
         future-to-infinitive
         imperfect-to-infinitive-irreg1
         infinitive-to-infinitive ;; simply turns :top into :infl
         lexical-noun-to-singular ;; turns :number :top to :number :sing
         past-to-infinitive
         present-to-infinitive-ire
         present-to-infinitive-ere
         present-to-infinitive-are
         plural-to-singular-noun-fem-1
         plural-to-singular-noun-masc-1
         plural-to-singular-noun-masc-2
         plural-to-singular-adj-masc
         plural-to-singular-adj-fem-plur
         plural-to-singular-adj-fem-sing
         )
        
        analyzed
        (remove fail?
                (mapcat
                 (fn [key]
                   (if (and (not (keyword? key)) (re-find key surface-form))
                        (let [replace-with (get replace-pairs key)
                              lexical-form (if (= key :identity)
                                             surface-form
                                             (string/replace surface-form key
                                                             (:replace-with replace-with)))
                              looked-up (lookup-fn lexical-form)]
                          (map #(unifyc 
                                 %
                                 (:unify-with replace-with))
                               looked-up))))
                 (keys replace-pairs)))

        ;; Analyzed-via-identity is used to handle infinitive verbs: converts them from unspecified inflection to
        ;; {:infl :infinitive}
        ;; Might also be used in the future to convert nouns from unspecified number to singular number.
        analyzed-via-identity
        (remove fail?
                (mapcat
                 (fn [key]
                   (if (and (keyword? key) (= key :identity))
                        (let [lexical-form surface-form
                              looked-up (lookup-fn lexical-form)]
                          (map #(unifyc 
                                 %
                                 (:unify-with (get replace-pairs key)))
                               looked-up))))
                 (keys replace-pairs)))]


    (concat
     analyzed

     ;; also lookup the surface form itself, which
     ;; might be either the canonical form of a word, or an irregular conjugation of a word.
     (if (not (empty? analyzed-via-identity))
       analyzed-via-identity
       (lookup-fn surface-form)))))

(defn exception-generator [lexicon]
  (let [lexeme-kv (first lexicon)
        lexemes (second lexeme-kv)]
    (if lexeme-kv
      (let [result (mapcat (fn [path-and-merge-fn]
                             (let [path (:path path-and-merge-fn)
                                   merge-fn (:merge-fn path-and-merge-fn)]
                               ;; a lexeme-kv is a pair of a key and value. The key is a string (the word's surface form)
                               ;; and the value is a list of lexemes for that string.
                               (log/debug (str (first lexeme-kv) "looking at path: " path))
                               (mapcat (fn [lexeme]
                                         ;; this is where a unify/dissoc that supported
                                         ;; non-maps like :top and :fail, would be useful:
                                         ;; would not need the (if (not (fail? lexeme)..)) check
                                         ;; to avoid a difficult-to-understand "java.lang.ClassCastException: clojure.lang.Keyword cannot be cast to clojure.lang.IPersistentMap" error.
                                         (let [lexeme (cond (= lexeme :fail)
                                                            :fail
                                                            (= lexeme :top)
                                                            :top
                                                            true
                                                            (dissoc (copy lexeme) :serialized))]
                                           (if (not (= :none (get-in lexeme path :none)))
                                             (list {(get-in lexeme path :none)
                                                    (merge
                                                     lexeme
                                                     (unifyc (apply merge-fn (list lexeme))
                                                             {:espanol {:exception true}}))}))))
                                       lexemes)))
                           [
                            ;; 1. past-tense exceptions
                            {:path [:espanol :passato]
                             :merge-fn
                             (fn [val]
                               {:espanol {:infl :past
                                           :espanol (get-in val [:espanol :passato] :nothing)}})}

                            ;; 2. present-tense exceptions
                            {:path [:espanol :present :1sing]
                             :merge-fn
                             (fn [val]
                               {:espanol {:infl :present
                                           :espanol (get-in val [:espanol :present :1sing] :nothing)
                                           :agr {:number :sing
                                                 :person :1st}}})}
                            {:path [:espanol :present :2sing]
                             :merge-fn
                             (fn [val]
                               {:espanol {:infl :present
                                           :espanol (get-in val [:espanol :present :2sing] :nothing)
                                           :agr {:number :sing
                                                 :person :2nd}}})}

                            {:path [:espanol :present :3sing]
                             :merge-fn
                             (fn [val]
                               {:espanol {:infl :present
                                           :espanol (get-in val [:espanol :present :3sing] :nothing)
                                           :agr {:number :sing
                                                 :person :3rd}}})}

                            {:path [:espanol :present :1plur]
                             :merge-fn
                             (fn [val]
                               {:espanol {:infl :present
                                           :espanol (get-in val [:espanol :present :1plur] :nothing)
                                           :agr {:number :plur
                                                 :person :1st}}})}

                            {:path [:espanol :present :2plur]
                             :merge-fn
                             (fn [val]
                               {:espanol {:infl :present
                                           :espanol (get-in val [:espanol :present :2plur] :nothing)
                                           :agr {:number :plur
                                                 :person :2nd}}})}

                            {:path [:espanol :present :3plur]
                             :merge-fn
                             (fn [val]
                               {:espanol {:infl :present
                                           :espanol (get-in val [:espanol :present :3plur] :nothing)
                                           :agr {:number :plur
                                                 :person :3rd}}})}

                            ;; adjectives
                            {:path [:espanol :masc :plur]
                             :merge-fn
                             (fn [val]
                               {:espanol {:agr {:gender :masc
                                                 :number :plur}}})}

                            {:path [:espanol :fem :plur]
                             :merge-fn
                             (fn [val]
                               {:espanol {:agr {:gender :fem
                                                 :number :plur}}})}
                            ])]
        (if (not (empty? result))
          (concat result (exception-generator (rest lexicon)))
          (exception-generator (rest lexicon)))))))

(defn phonize [a-map a-string]
  (let [common {:phrasal false}]
    (cond (or (vector? a-map) (seq? a-map))
          (map (fn [each-entry]
                 (phonize each-entry a-string))
               a-map)

          (and (map? a-map)
               (not (= :no-espanol (get-in a-map [:espanol] :no-espanol))))
          (unifyc {:espanol {:espanol a-string}}
                  common
                  a-map)

        true
        (unifyc a-map
                {:espanol a-string}
                common))))

(defn agreement [lexical-entry]
  (cond
   (= (get-in lexical-entry [:synsem :cat]) :verb)
   (let [cat (ref :top)
         infl (ref :top)]
     (unifyc lexical-entry
             {:espanol {:cat cat
                         :infl infl}
              :synsem {:cat cat
                       :infl infl}}))

   (= (get-in lexical-entry [:synsem :cat]) :noun)
   (let [agr (ref :top)
         cat (ref :top)]
     (unifyc lexical-entry
             {:espanol {:agr agr
                        :cat cat}
              :synsem {:agr agr
                       :cat cat}}))

   true
   lexical-entry))

(def espanol-specific-rules
  (list agreement))
