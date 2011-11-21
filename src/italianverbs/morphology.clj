;; RESTARTING OF RING REQUIRED FOR CHANGES TO THIS FILE. (maybe not actually)
(ns italianverbs.morphology
  (:use [hiccup core page-helpers]
        [italianverbs.rdutest])
  (:require
   [italianverbs.fs :as fs]
   [clojure.string :as string]
   [clojure.contrib.string :as stringc]
   [clojure.contrib.str-utils2 :as str-utils]))

(defn remove-to [english-verb-phrase]
  (let [english (get english-verb-phrase :english)]
    (let [regex #"^to[ ]+(.*)"]
      (let [string
            (str-utils/replace english regex (fn [[_ rest]] (str rest)))]
        (merge
         {:remove-to string}
         english-verb-phrase)))))

(defn add-s-to-first-word [english-verb-phrase]
  (let [english-verb-string (get english-verb-phrase :english)]
    (let [regex #"^[ ]*([^ ]+)[ ]*(.*)"
          with-s
          (str-utils/replace
           english-verb-string
           regex
           (fn [[_ first-word rest]]
             (str first-word (if (re-find #"o$" first-word) "e") "s" " " rest)))]
      (merge
       {:add-s with-s}
       english-verb-phrase))))

(defn conjugate-english-verb [verb-head subject & [conjugate-verb-as]]
  ;; conjugate verb based on subject and eventually verb's features (such as tense)
  (let [vp verb-head
        verb-head (if (get verb-head :head)
                    (get verb-head :head)
                    verb-head)
        english (get verb-head :english)
        remove-to (remove-to verb-head)]
    (cond
     (= (get (fs/get-head verb-head) :english) "must")
     (str
      (stringc/replace-re
       #"^must to"
       "must"
       (get verb-head :english))
      " "
      (get (get vp :comp) :english))

     (= (get (fs/get-head verb-head) :english) "to be able")
     (str
      (stringc/replace-re
       #"^to be able( to)?"
       "can"
       (get verb-head :english))
      " "
      (get (get vp :comp) :english))

;; move this to irregular param.
;     (= (get (fs/get-head verb-head) :english) "to be named")
;     (str
;      ;; FIXME: should use (conjugate-english-verb) here, not (overly-lowlevel) (fetch-one)
;      (get (lexfn/fetch-one {:root.english "to be" :infl :present
;                             :number (get subject :number)
;                             :person (get subject :person)}) :english) " "
;                             "named")
     
     (or (= (get (fs/get-head subject) :person) "1st")
         (= (get (fs/get-head subject) :person) :1st))
     (str
      (get remove-to :remove-to) 
      (get (get vp :comp) :english))

     (or (= (get (fs/get-head subject) :person) "2nd")
         (= (get (fs/get-head subject) :person) :2nd))
     (str
      (get remove-to :remove-to)
      " "
      (get (get vp :comp) :english))
      

     (and
      (or (= (get (fs/get-head subject) :person) "3rd")
          (= (get (fs/get-head subject) :person) :3rd))
      (or (= (get (fs/get-head subject) :number) "singular")
          (= (get (fs/get-head subject) :number) :singular))
      (or (= (get (fs/get-head verb-head) :infl) :present)
          (= (get conjugate-verb-as :infl) :present)))
     (str (get
           (add-s-to-first-word
            (merge
             remove-to
             {:english (get remove-to :remove-to)}))
           :add-s)
          " "
          (get (get vp :comp) :english))

     true ;; 3rd plural
     (str (get remove-to :remove-to)
          " "
          (get (get vp :comp) :english)))))

(defn final-char-of [string]
  (str-utils/get string (- (.length string) 1)))

(defn next-to-final-char-of [string]
  (if (> (.length string) 1)
    (str-utils/get string (- (.length string) 2))))

(defn if-isco [verb]
  (if (= (get verb :isco) true)
    "isc"
    ""))

(defn conjugate-italian-verb-regular [verb-head subject-head]
  (let [root-form (get verb-head :italian)
        regex #"^([^ ]*)([aei])re[ ]*$"]
    (cond

     (and (or (= (get subject-head :person) "1st")
              (= (get subject-head :person) :1st))
          (or (= (get subject-head :number) "singular")
              (= (get subject-head :number) :singular)))
     (str-utils/replace root-form regex
                        (fn [[_ stem vowel space]] (str stem
                                                        (if-isco verb-head)
                                                        "o"
                                                        space)))

     (and (or (= (get subject-head :person) "1st")
              (= (get subject-head :person) :1st))
          (or (= (get subject-head :number) "plural")
              (= (get subject-head :number) :plural)))
     (str-utils/replace root-form regex
                        (fn [[_ stem i space]] (str stem
                                                    (if-isco verb-head)
                                                    
                                                    (if (and
                                                         (not (= (next-to-final-char-of stem) (final-char-of "s")))
                                                         (= (final-char-of stem) (final-char-of "c")))
                                                      "h" "")

                                                    (if (not (= (final-char-of stem) (final-char-of "i")))
                                                      "i" "")


                                                    
                                                    "amo" space)))

     (and (or (= (get subject-head :person) "2nd")
              (= (get subject-head :person) :2nd))
          (or (= (get subject-head :number) :singular)
              (= (get subject-head :number) "singular")))
     (str-utils/replace root-form regex
                        (fn [[_ stem vowel space]] (str stem
                                                        (if-isco verb-head)

                                                        (if (and
                                                             (not (= (next-to-final-char-of stem) (final-char-of "s")))
                                                             (= (final-char-of stem) (final-char-of "c")))
                                                          "h" "")
                                                        
                                                        (if (not (= (final-char-of stem) (final-char-of "i"))) "i" "")
                                                        space)))

     (and (or (= (get subject-head :person) "2nd")
              (= (get subject-head :person) :2nd))
          (or (= (get subject-head :number) "plural")
              (= (get subject-head :number) :plural)))
     (str-utils/replace root-form regex
                        (fn [[_ stem vowel space]] (str stem
                                                        (if-isco verb-head)
                                                        vowel "te" space)))

     (and (or (= (get subject-head :person) "3rd")
              (= (get subject-head :person) :3rd))
          (or (= (get subject-head :number) "singular")
              (= (get subject-head :number) :singular)))
     ;; TODO: this works for -ire verbs like aprire->aprie but not
     ;; -ire verbs like finire->finisco.
     (str-utils/replace root-form regex
                        (fn [[_ stem vowel space]] (str stem
                                                        (if-isco verb-head)
                                                        (cond
                                                         (= vowel "a") "a"
                                                         true "e")
                                                        space)))

     (and (or (= (get subject-head :person) "3rd")
              (= (get subject-head :person) :3rd))
          (or (= (get subject-head :number) "plural")
              (= (get subject-head :number) :plural)))
     (str-utils/replace root-form regex
                        (fn [[_ stem vowel space]] (str stem
                                                        (if-isco verb-head)
                                                        (cond
                                                         (= vowel "a") "a"
                                                         true "o")
                                                        "no" space)))
     true
     (str "Error: :person (" (get subject-head :person) ") or :number (" (get subject-head :number)  ")  value was not matched. (verb-head=" (get verb-head :italian) "),(subject=" (get subject-head :italian) ")"))))

;; TODO: figure out how to interpolate variables into regexps.
(defn except-first-words [first-words words]
  (let [regex #"^[^ ]+[ ]?(.*)"]
    (str-utils/replace words regex (fn [[_ rest]] rest))))

(defn plural-masc [italian]
  (stringc/join " "
                (cons (stringc/replace-re #"[o]$" "i" (first (stringc/split #"\s+"
                                                                          italian)))
                      (rest (stringc/split #"\s+" italian)))))

(defn plural-fem [italian]
  (stringc/join " "
                (cons (stringc/replace-re #"[oa]$" "e" (first (stringc/split #"\s+"
                                                                          italian)))
                      (rest (stringc/split #"\s+" italian)))))

(defn single-fem [italian]
  (stringc/join " "
                (cons (stringc/replace-re #"[o]$" "a" (first (stringc/split #"\s+"
                                                                            italian)))
                      (rest (stringc/split #"\s+" italian)))))

(defn conjugate-passato-prossimo [verb-phrase subject]
  (cond

   (and (= (get verb-phrase :aux) "essere")
        (or (= (get subject :gender) :fem)
            (= (get subject :gender) "fem"))
        (or (= (get subject :number) :singular)
            (= (get subject :number) "singular")))
   (single-fem (get verb-phrase :italian))

   (and (= (get verb-phrase :aux) "essere")
        (or (= (get subject :gender) :masc)
            (= (get subject :gender) "masc")
            (not (get subject :gender)))
        (or (= (get subject :number) :plural)
            (= (get subject :number) "plural")))
   (plural-masc (get verb-phrase :italian))

   (and (= (get verb-phrase :aux) "essere")
        (or (= (get subject :gender) :fem)
            (= (get subject :gender) "fem"))
        (or (= (get subject :number) :plural)
            (= (get subject :number) "plural")))
   (plural-fem (get verb-phrase :italian))
   true (get verb-phrase :italian)))

(defn conjugate-italian-verb [verb-phrase subject]
  ;; conjugate verb based on subject and eventually verb's features (such as tense)
  ;; takes two feature structures and returns a string.
  (cond
   (or (= (get verb-phrase :infl) :passato-prossimo)
       (= (get verb-phrase :infl) "passato-prossimo"))
   (conjugate-passato-prossimo verb-phrase subject)
   true
   (let [italian (get verb-phrase :italian)
         italian-head (get (fs/get-head verb-phrase) :italian)
         ;; all we need is the head, which has the relevant grammatical information, not the whole subject
         subject (fs/get-head subject)] 
     (let [italian (if italian-head italian italian)]
       (let [except-first
             (except-first-words
              (fs/get-head verb-phrase)
              (get verb-phrase :italian))]
         (string/join (list
                       " "
                       (conjugate-italian-verb-regular
                        (fs/get-head verb-phrase) subject)
                       except-first)))))))

(defn conjugate-it [head]
  (cond (= (get head :cat) "noun")
	(cond (= (get head :gender) "masc")
	      (cond (= (get head :number) "plural")
		    (plural-masc (get head :italian))
		    true
		    (get head :italian))
	      (= (get head :gender) "fem")
	      (cond (= (get head :number) "plural")
		    (plural-fem (get head :italian))
		    true
		    (get head :italian))
	      true
	      "??(not masc or fem)")
	true
	(str "??(cat != noun)"
	     (get head :cat)
	     (= (get head :cat) "noun"))))

(defn conjugate-en [head arg]
  (str (get arg :english)
       " "
       (cond (= (get head :cat) "noun")
	     (cond (= (get head :number) "plural")
		   (str (get head :english) "s")
		   true
		   (get head :english))
	     true
	     (str "??(cat != noun)"
		  (get head :cat)
		  (= (get head :cat) "noun")))))

(defn italian-article [det noun]
  "do italian det/noun morphology e.g. [def :def] + studente => lo studente" 
  ;; TODO: return a feature structure holding the current return value in :italian.
  (let [det-italian (get det :italian)
        det-noun (get noun :italian)]
    (cond
     (and (re-find #"^[aeiou]" (get noun :italian))
          ;; TODO: figure out why we need to check for both string ("def") and symbol (:def)
          ;; probably has to do with mongo to clojure mapping.
          (or (= (get det :def) "def")
              (= (get det :def) :def))
          (or (= (get noun :number) "singular")
              (= (get noun :number) :singular))
          ;; for numbers, "l'una" but "le otto", only match "una" here.
          ;; not sure about other numbers that start with "una", if any.
          (or (not (= (get noun :numerical) true))
              (and (= (get noun :numerical) true)
                   (= (get noun :italian) "una"))))
     (str "l'" (get noun :italian))

     (and (re-find #"^st" (get noun :italian))
          (= (get det :def) "def")
          (= (get noun :number) "singular")
          (= (get noun :gender) "masc"))
     (str "lo " (get noun :italian))

     (and (re-find #"^(st|[aeiou])" (get noun :italian))
          (= (get det :def) "def")
          (= (get noun :number) "plural")
          (= (get noun :gender) "masc"))
     (str "gli " (get noun :italian))

     (and (re-find #"^[aeiou]" (get noun :italian))
          (= (get det :def) "indef")
          (= (get noun :number) "singular")
          (= (get noun :gender) "masc"))
     (str "un'" (get noun :italian))

     (and (re-find #"^st" (get noun :italian))
          (= (get det :def) "indef")
          (= (get noun :number) "singular")
          (= (get noun :gender) "masc"))
     (str "uno " (get noun :italian))

     (and (= (get det :def) "part")
          (= (get noun :gender) "fem"))
     (str "delle " (get noun :italian))

     (and (= (get det :def) "part")
          (= (get noun :gender) "masc")
          (re-find #"^(st|[aeiou])" (get noun :italian)))
     (str "degli " (get noun :italian))

     (and (= (get det :def) "part")
          (= (get noun :gender) "masc"))
     (str "dei " (get noun :italian))
     
     true (str det-italian " " det-noun))))


(defn replace-from-list [regexp-list target]
  "Apply the first regexp pair (from=>to) from regexp-list to target;
   if this regexp changes target, return changed string,
   otherwise, try next regexp."
  (if (> (count regexp-list) 0)
    (let [regexp-pair (first regexp-list)
          regexp-from (first regexp-pair)
          regexp-to (second regexp-pair)
          result (stringc/replace-re regexp-from regexp-to target)]
      (if (= result target)
        (replace-from-list (rest regexp-list) target)
        result))
    target))

(defn conjugate-italian-prep [prep np]
  (let [concat (str (get prep :italian)
                    " "
                    (get np :italian))]
    (replace-from-list
     (list
      (list #"\ba il " "al ")
      (list #"\ba lo " "allo ")
      (list #"\ba la " "alla ")
      (list #"\ba l'" "all'")
      (list #"\ba i " "ai ")
      (list #"\ba gli " "agli ")
      (list #"\ba le " "alle ")
      
      (list #"\bda il " "dal ")
      (list #"\bda lo " "dallo ")
      (list #"\bda la " "dalla ")
      (list #"\bda l'" "dall'")
      (list #"\bda i " "dai ")
      (list #"\bda gli " "dagli ")
      (list #"\bda le " "dalle ")

      (list #"\bde il " "del ")
      (list #"\bde lo " "dello ")
      (list #"\bde la " "della ")
      (list #"\bde l'" "dell'")
      (list #"\bde i " "dei ")
      (list #"\bde gli " "degli ")
      (list #"\bde le " "delle ")
      
      (list #"\bdi il " "del ")
      (list #"\bdi lo " "dello ")
      (list #"\bdi la " "della ")
      (list #"\bdi l'" "dell'")
      (list #"\bdi i " "dei ")
      (list #"\bdi gli " "degli ")
      (list #"\bdi le " "delle ")
      
      (list #"\bin il " "nel ")
      (list #"\bin lo " "nello ")
      (list #"\bin la " "nella ")
      (list #"\bin l'" "nell'")
      (list #"\bin i " "nei ")
      (list #"\bin gli " "negli ")
      (list #"\bin le " "nelle ")

      (list #"\bsu il " "sul ")
      (list #"\bsu lo " "sullo ")
      (list #"\bsu la " "sulla ")
      (list #"\bsu l'" "sull'")
      (list #"\bsu i " "sui ")
      (list #"\bsu gli " "sugli ")
      (list #"\bsu le " "sulle ")
      )
     concat)))

(defn stem-per-futuro [infinitive]
  "_infinitive_ should be a string (italian verb infinitive form)"
  (str-utils/replace infinitive #"^(.*)([aei])(re)$" (fn [[_ prefix vowel suffix]] (str prefix (if (= vowel "a") "e" vowel) "r"))))
  
(defn conjugate-future-italian [infinitive subject & [ stem ] ]
  (let [stem (if stem
               stem 
               (stem-per-futuro (get infinitive :italian)))]
    (cond
     (= (get subject :person)
        :1st)
     (cond (= (get subject :number)
              :singular)
           (str stem "ò")
           (= (get subject :number)
              :plural)
           (str stem "emo")
           true "??")
     (= (get subject :person)
        :2nd)
     (cond (= (get subject :number)
              :singular)
           (str stem "ai")
           (= (get subject :number)
              :plural)
           (str stem "ete")
           true "??")
     (= (get subject :person)
        :3rd)
     (cond (= (get subject :number)
              :singular)
           (str stem "à")
           (= (get subject :number)
              :plural)
           (str stem "anno")
           true "??")
     true
     "??")))

;(defn test []
;  (list
;   {:comment "stem verb for futuro semplice"
;    :test (stem-per-futuro "tornare")}))
(def tests
  {:stem-for-futuro
   (rdutest
    "Stem verb for futuro semplice"
    (stem-per-futuro "tornare")
    (fn [future-stem] (= future-stem "torner"))
    :stem-for-futuro)
   :io-facio
   (rdutest
    "Conjugate 'io' + 'prefire' => 'io  preferisco'"
    (conjugate-italian-verb {:infl "infinitive", :cat "verb", :isco true :italian "preferire", :english "to prefer"}
                            {:italian "io", :english "i", :person :1st, :number :singular})
    (fn [string]
      (= string " preferisco"))
    :io-facio)})




      
