(ns italianverbs.html
  (:use [hiccup core page-helpers]
	[somnium.congomongo])
  (:require
      [clojure.set :as set]
      [clojure.string :as string]
      [italianverbs.lexiconfn :as lex]
      [clojure.contrib.str-utils2 :as str-utils]))

(defn verb-row [italian]
  (html  
   [:tr 
    [:th italian] [:td
;                   (get (lex/lookup italian) :english)
"FAIL."
                   ] 
    ]))

(defn verb-table [lexicon]
  (html [:table 
	(for [verb (sort (keys lexicon))]
	     (verb-row verb))]))

(defn fs-tr [key-val-pair]
  (let [key (first key-val-pair)
	val (second key-val-pair)]
    (str "<tr> <th> " key "</th>  <td>" val "</td></tr>")))

(defn fs [lexeme]
  (str "<table class='fs'>"
       (if (get lexeme :italian)
         (str "<tr><th colspan='2' class='fs'>"
              "<a href='"
              "http://translate.google.com/"
              "#it|en|"
              (get lexeme :italian)
              "'"   ">"
              (get lexeme :italian) "</a>"
              "</th></tr>"))
       (string/join " " (seq (map fs-tr
                                  (map (fn [key]
                                         (cond
                                          (= key :_id) nil
                                          (= key :children) nil
                                        ; uncomment for debugging.
                                          (= key :fn) nil
                                          (= key :head) nil
                                          ;; featues whose values are nested feature structures.
                                          (or (= key :head-debug) (= key :comp-debug)
                                              (= key :subj)(= key :obj)
                                              (= key :det)
                                              (= key :adjunct)(= key :iobj)
                                              (= key :choose)(= key :root))
                                          (list key
                                                (fs (get lexeme key)))
                                          (= key :comp) nil
                                          true
                                          (list key
                                                (get lexeme key))))
                                       (set/difference (set (keys lexeme))
                                                       (set (list :italian)))))))
       "</table>"))

(defn tablize [parent]
  (let
      [children (get parent :children)]
      (str
     "<div class='syntax'><table class='syntax'>"
     "<tr><td style='padding-left:5%;width:90%' colspan='" (count children) "'>"
       (fs parent)
     "</td></tr>"
     "<tr>"
     ;; now show syntactic children for this parent.
     (string/join " " (map (fn [child] (str "<td>"
					    (cond (string? child)
						  child
						  (get child :children)
						  (tablize child)
						  true
						  (fs child))
					    "</td>")) children))
     "</tr>"
     "</table></div>")))


