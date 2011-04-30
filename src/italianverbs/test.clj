 (ns italianverbs.test
    (:use 
     [hiccup core page-helpers]
     [somnium.congomongo])
    (:require
     [italianverbs.html :as html]
     [italianverbs.lexiconfn :as lexfn]
     [italianverbs.grammar :as gram]
     [clojure.string :as string]
     [italianverbs.quiz :as quiz]))


;; <test definitions>

;; each testN must specify a testN-fn, a testN-head, and testN-comp.
;; 2 choices for testN-fn.
;; gram/choose-lexeme : choose a random lexeme based on choose-head which is a feature structure.
;;                      choose-comp-lexeme is ignored.
;; gram/np : generate a np with head based on choose-head,
;;           det based on choose-comp-lexeme.


;; test1: singular NPs with determiners.
(def test1-fn gram/np)
(def test1-head {:cat :noun
                 :pronoun {:$ne true}
                 :number :singular})
(def test1-comp {:number :singular
                 :cat :det})

;; test2: plural NPs with determiners.
(def test2-fn gram/np)
(def test2-head {:cat :noun
                 :pronoun {:$ne true}
                 :number :plural})
(def test2-comp {:number :plural
                 :cat :det})



;; useful library functions: will move elsewhere after testing.
(defn show-answer [question] (get question :answer))
(defn wrap-div [string]
  (str "<div class='test'>" string "</div>"))

(defn lex-thead [lexeme]
  (str "<tr>"
       "<th>italian</th>"
       "<th>english</th>"
       "<th>person</th>"
       "<th>number</th>"
       "<th>cat</th>"
       "<th>infl</th>"
       "<th>writable</th>"
       "<th>fn</th>"
       "</tr>"))

(defn lex-row [lexeme]
  (str "<tr>"
       "<td>" (nth lexeme 0) "</td>"
       "<td>" (get (nth lexeme 1) :english) "</td>"
       "<td>" (get (nth lexeme 1) :person) "</td>"
       "<td>" (get (nth lexeme 1) :number) "</td>"
       "<td>" (get (nth lexeme 1) :cat) "</td>"
       "<td>" (get (nth lexeme 1) :infl) "</td>"
       "<td>" (get (nth lexeme 1) :writable) "</td>"
       "<td>" (get (nth lexeme 1) :fn) "</td>"
       "</tr>"))

(defn answer-row [question]
  (let [correctness (if (= (get question :guess) (get question :answer)) "correct" "incorrect")]
       (str "<tr><td>" 
	    (get question :answer) 
	    "</td><td>" 
	    (get question :guess) 
	    "</td><td class='" correctness "'>"
	    correctness
	    "</td></tr>")))

;; tests
(defn answertable []
  (str "<table>" (string/join " " (map answer-row (fetch :question))) "</table>"))

(defn correct []
  (str "correct guesses: " (count (mapcat quiz/each-correct (fetch :question)))
       " out of : " (count (fetch :question))))

;; fixme: change name to "compose-sv-sentence" or something.
(defn conjugate [pronoun infinitive]
    (gram/combine infinitive pronoun 'right))

(defn io-pranzo []
  (gram/combine (lexfn/lookup "pranzare")
           (lexfn/lookup "io" {:case {:$ne :acc}}) 'right))

(defn lui-scrivo-il-libro []
  (let [subject (lexfn/lookup "lui" {:case {:$ne :acc}})
        object (gram/combine
                (lexfn/lookup "libro")
                (lexfn/lookup "il") 'right gram/det-n)
        verb-phrase (gram/combine (lexfn/lookup "scrivere") object 'left gram/vo)]
    (gram/combine verb-phrase subject 'right)))

(def in-italia
  (let [prep (lexfn/lookup "in")
	noun (lexfn/lookup "Italia")]
    (gram/combine
     prep noun 'left)))

(def andare-in-italia
  (gram/combine (lexfn/lookup "andare")
           in-italia 'left))

(defn lui-vado-in-italia []
  (gram/combine
   (gram/combine
    (lexfn/lookup "andare") in-italia 'left)
   (lexfn/lookup "lui" {:case {:$ne :acc}}) 'right))

(defn io-mangio-il-pane []
  (let [subject (lexfn/lookup "io" {:case {:$ne :acc}})
        object (gram/combine
                (lexfn/lookup "pane")
                (lexfn/lookup "il") 'right)
        verb-phrase (gram/combine (lexfn/lookup "mangiare")
                             object 'left)]
    (gram/combine verb-phrase subject 'right)))

(defn lui-mangio-la-pasta-in-italia []
  (let [subject (lexfn/lookup "lui" {:case {:$ne :acc}})
        object (gram/combine
                (lexfn/lookup "pasta")
                (lexfn/lookup "la") 'right)
        verb-phrase (gram/combine (lexfn/lookup "mangiare")
                             object
                             'left)]
    (gram/combine 
     (gram/combine verb-phrase subject 'right)
     in-italia 'left)))

(defn io-scrivo-il-libro []
  (let [subject (lexfn/lookup "io")
	object (gram/combine
		(lexfn/lookup "libro")
		(lexfn/lookup "il"))
	verb-phrase (gram/combine (lexfn/lookup "scrivere")
			     object)]
    (gram/combine verb-phrase subject)))

(defn reload-button []
  (str "<form action='/test/' method='post'><input type='submit' value='Reload'/>  </form> "))

(defn bugs []
  nil)

(defn conjugation [verb] ;; verb should be the infinitive form of a verb.
  (str
   "<div class='conjugation'>"
   (html/tablize verb)
   "<table class='fs conjugation'>"
   "<tr>"
   "<th>io</th>"
   "<td>"
   (get (conjugate (lexfn/lookup "io") verb) :italian)
   "</td>"
   "</tr>"
   "<tr>"
       "<th>tu</th>"
       "<td>"
       (get (conjugate (lexfn/lookup "tu") verb) :italian)
       "</td>"
       "</tr>"
       "<tr>"
       "<th>lui/lei</th>"
       "<td>"
       (get (conjugate (lexfn/lookup "lui") verb) :italian)
       "</td>"
       "</tr>"
       "<tr>"
       "<th>noi</th>"
       "<td>"
       (get (conjugate (lexfn/lookup "noi") verb) :italian)
       "</td>"
       "</tr>"
       "<tr>"
       "<th>voi</th>"
       "<td>"
       (get (conjugate (lexfn/lookup "voi") verb) :italian)
       "</td>"
       "</tr>"
       "<tr>"
       "<th>loro</th>"
       "<td>"
       (get (conjugate (lexfn/lookup "loro") verb) :italian)
       "</td>"
       "</tr>"
       "</table>"
       "</div>"
       ))

(defn conjugations []
  (list 
   "<div class='section'> <h2>conjugations</h2></div>"
   (conjugation (lexfn/lookup "andare"))
   (conjugation (lexfn/lookup "volare"))
   (conjugation (lexfn/lookup "fare"))
   (conjugation (lexfn/lookup "venire"))
   (conjugation (lexfn/lookup "dire"))))

(defn random-sentences-1 [num generate-fn head comp]
  (if (> num 0)
    (cons

     (html/tablize (apply generate-fn (list head
                                            (gram/choose-lexeme comp nil))))
     (random-sentences-1 (- num 1) generate-fn head comp))))

(defn random-sentences [num generate-fn head comp]
  (list
   (random-sentences-1 num generate-fn head comp)))

(def tests
  (list
;   (reload-button) ; reload button does not work yet (results are still cached)

   ;(bugs)

   ;(conjugations)

   (random-sentences 1 test1-fn test1-head test1-comp)
   (random-sentences 1 test2-fn test2-head test2-comp)
   
;   "<div class='section'> <h2>fixed sentences</h2></div>"
 ;  (html/tablize (lui-vado-in-italia))
 ;  (html/tablize (io-mangio-il-pane))
 ;  (html/tablize (lui-mangio-la-pasta-in-italia))
 ;  (html/tablize (io-pranzo))
 ;  (html/tablize (lui-scrivo-il-libro))

;   (correct)
;   (answertable)
   ))

  