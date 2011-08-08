(ns italianverbs.lev
  (:require
   [clojure.contrib.string :as string]
   [italianverbs.grammar :as gram]))

(defn create-matrix [matrix j y horiz-char-list vert-char-list]
  "adds one row at a time to matrix for all rows up to y.
   assumes that matrix already has one row created with
   (create-initial-row)"
  ;; TODO: for consistency, do similar to add-one-row does:
  ;;  (check for existence of (get matrix [0,j])).
  (let [add-one-row
        (fn ! [matrix x y horiz-char-list vert-char-list]
          "adds values for one whole row ([x:[0,end], y]) to the matrix."
          ;; TODO: use recur:
          ;; see http://clojure.org/functional_programming#Functional Programming--Recursive Looping
          (let [get-min (fn [matrix x y char-x char-y]
                          (let [diag
                                (if (= char-x char-y) 0 2)]
                            (min (if (> x 0)
                                   (+ 1 (get matrix (list (- x 1) y)))
                                   Float/POSITIVE_INFINITY)
                                 (if (> y 0)
                                   (+ 1 (get matrix (list x (- y 1))))
                           Float/POSITIVE_INFINITY)
                                 (if (and (> x 0) (> y 0))
                                   (+ diag
                                      (get matrix (list (- x 1) (- y 1))))
                                   Float/POSITIVE_INFINITY))))]
            (if (get matrix (list x (- y 1)))
              (!
               (merge
                matrix
                {(list x y) (get-min matrix
                                     x y
                                     (nth horiz-char-list x)
                                     (nth vert-char-list y)
                                     )})
               (+ 1 x) y
               horiz-char-list vert-char-list)
              matrix)))]
    (if (<= j y)
      (let [new-matrix
            (merge
             matrix
             (add-one-row matrix 0 j
                          horiz-char-list
                          vert-char-list))]
        (create-matrix new-matrix
                       (+ 1 j)
                       y
                       horiz-char-list
                       vert-char-list))
      matrix)))

(defn create-initial-row [char-list-horiz char-list-vert
                          i
                          current-val]
  "i is an index into char-list-horiz"
  (if (> (.size char-list-horiz) 0)
    (merge
     {(list i 0) current-val}
     (create-initial-row (rest char-list-horiz)
                         char-list-vert
                         (+ i 1)
                         (+ current-val 1)
                         ))
    {}))


(defn matrix-header [charlist]
  (if (> (.size charlist) 0)
    (str "<th>" (first charlist) "</th>"
         (matrix-header (rest charlist)))))

(defn tablize [matrix horiz-char-list vert-char-list j path]
  (let [tablize-row (fn ! [matrix i j horiz-char-list vert-char-list path]
                      (if (get matrix (list i j))
                        (str
                         "<td"
                         (if (get path (list i j))
                           (if (=
                                (nth horiz-char-list i)
                                (nth vert-char-list j))
                             " class='corrent'"
                             " class='path'"))
                           ">"
                         (nth horiz-char-list i) "/"
                         (nth vert-char-list j) ":"
                         (get matrix (list i j))
                         "</td>"
                         (! matrix (+ i 1) j horiz-char-list vert-char-list path))
                        ""))]
    (if (get matrix (list 0 j))
      (str
       "<tr>"
       "<th>"
       j
       "</th>"
       "<th>"
       ;; j'th element of vertical string.
       (nth vert-char-list j)
       "</th>"
       (tablize-row matrix 0 j horiz-char-list vert-char-list path)
       "</tr>"
       (tablize matrix horiz-char-list vert-char-list (+ 1 j) path))
      "")))

(defn shell-x-axis [x y]
  (if (>= x 0)
    (cons (list x y)
          (shell-x-axis (- x 1) y))))
           
(defn shell-y-axis [x y]
  (if (>= y 0)
    (cons (list x y)
          (shell-y-axis x (- y 1)))))

(defn shells [x y]
  (if (>= x 0)
    (cons
     (concat (shell-x-axis x y)
             (if (> y 0)
               (shell-y-axis x (- y 1))))
     (shells
      (if (>= x y)
        (- x 1)
        x)
      (if (>= y x)
        (- y 1)
        y)))))

(defn next-candidates [min-key]
;  min-key)
  (let [key (nth (first min-key) 0)
        x (first key)
        y (second key)]
    (if (and (> x 0)
             (> y 0))
      (list 

       (list (- x 1)
             (- y 1))


       (list x
             (- y 1))



       (list (- x 1)
             y)


       
       )
      (if (> x 0)
        (list 
         (list (- x 1)
               y))
        (if (> y 0)
          (list
           (list x
                 (- y 1))))))))

        

(defn find-path [shells matrix min-in-upper-shell candidates]
  (let [find-min-in-shell
        (fn ! [shell min-key min-value matrix in-upper-shell all-candidates]
          ;; initially: [*shell* nil Float/POSITIVE_INFINITY *matrix* nil]
          (if (> (.size shell) 0)
            (let [candidate (get matrix (first shell))
                  current-min-x (first min-key)
                  current-min-y (second min-key)
                  candidate-x (first (first shell))
                  candidate-y (second (first shell))]
              ;; replace current min if it's better.
              ;; 'candidate' is the (potentially smaller) value we are
              ;; testing against current minimum 'min-value'.
              ;; 'candidate''s 'x' value must be smaller than current min-value's
              ;; 'x' value.
              (if
                  (or 
                   (> min-value candidate)
                   false)

                ;; true: replace current minimum value with current candidate;
                ;; continue testing other candidates with new current minimum.
                (! (rest shell) (first shell) candidate matrix in-upper-shell candidates)
                
                ;; false: reject current candidate; continue with existing minimum.
                (! (rest shell) min-key min-value matrix in-upper-shell candidates)))
            
            {min-key {:candidates all-candidates
                      :in-upper-shell (nth (first in-upper-shell) 0)
                      :in-upper-shell-score (get matrix (nth (first in-upper-shell) 0))
                      :min-x (first min-key)
                      :min-y (second min-key)
                      :score min-value}}))]
    (if (> (.size shells) 0)
      (let [
            min-in-this-shell
            (find-min-in-shell candidates nil Float/POSITIVE_INFINITY matrix min-in-upper-shell candidates)
            ]
        (merge
         (merge
          min-in-this-shell
          {})
         (find-path (rest shells) matrix min-in-this-shell (next-candidates min-in-this-shell)))))))

(defn lookup-x-key [path-diag key]
  "sequential lookup through list."
  (if (> (.size path-diag) 0)
    (if (= (first (first (first path-diag)))
           key)
      (first path-diag)
      (lookup-x-key (rest path-diag) key))))

(defn lookup-y-key [path key]
  "sequential lookup through list."
  (if (> (.size path) 0)
    (if (= (second (first (first path)))
           key)
      (first path)
      (lookup-x-key (rest path) key))))

(defn print-out-shell [shell]
  (if (> (.size shell) 0)
    (str (first shell)
         (print-out-shell (rest shell)))
    ""))

(defn matrix [word1 word2]
  (let [explode
        (fn [string]
          "abc => (\"a\" \"b\" \"c\")"
          (rest (string/split (java.util.regex.Pattern/compile "") string)))
        wordlist1 (explode word1)
        wordlist2 (explode word2)
        matrix
        (create-matrix
         (create-initial-row
          wordlist1 wordlist2 0
          (if (= (first wordlist1)
                 (first wordlist2))
            0
            1))
         1 (- (.size wordlist2) 1)
         wordlist1
         wordlist2)
        shells
        (cons (list (list (- (.size wordlist1) 1) (- (.size wordlist2) 1)))
              (shells (- (.size wordlist1) 2)
                      (- (.size wordlist2) 2)))
        path (find-path shells matrix nil
                        (list (list (- (.size wordlist1) 1) (- (.size wordlist2) 1))))]
    {:italian word1
     :xth (get path (list 7 10))
     :test (str "<table class='matrix'>"
                "<tr>"
                "<th colspan='2'> </th>"
                (matrix-header (range 0 (.size wordlist1)))
                "</tr>"
                "<tr>"
                "<th colspan='2'> </th>"
                (matrix-header wordlist1)
                "</tr>"
                (tablize
                 matrix
                 wordlist1
                 wordlist2 0 path)
                "</table>")}))

(defn test []
  (matrix "a test two"
          "test three two"))
