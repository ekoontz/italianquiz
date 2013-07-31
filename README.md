[![Build Status](https://secure.travis-ci.org/ekoontz/italianquiz.png?branch=master)](http://travis-ci.org/ekoontz/italianquiz)

# Quick Start

## Install Prerequisites

You need git, Apache httpd, mongo, leiningen and italianquiz. We start by installing the first three.

```
sudo yum -y install git httpd

echo "
[10gen]
name=10gen Repository
baseurl=http://downloads-distro.mongodb.org/repo/redhat/os/x86_64
gpgcheck=0
enabled=1" > 10gen.repo

sudo cp 10gen.repo /etc/yum.repos.d/
sudo yum update
sudo yum -y install mongo-10gen mongo-10gen-server
sudo /etc/init.d/mongod start
```

## Configure Apache HTTP

We'll configure Apache to map /italian to our clojure webserver
which we'll set up in the next section, which will listen on port
3000.

```
echo "
ProxyPreserveHost on
ProxyPass /italian http://localhost:3000
ProxyPassReverse /italian http://localhost:3000
<Location /italian>
  Options All
  Order allow,deny
  allow from all
</Location>
" > italianquiz.conf

cat /etc/httpd/conf/httpd.conf italianquiz.conf > httpd.conf
sudo cp httpd.conf /etc/httpd/conf
sudo /etc/init.d/httpd restart
```

## Install leiningen

```
mkdir -p bin
wget https://raw.github.com/technomancy/leiningen/stable/bin/lein -O bin/lein
chmod 755 bin/lein
echo "
PATH=$PATH:$HOME/bin
export PATH
" >> .bash_profile
. .bash_profile
which lein
```

The last step should return:

```
~/bin/lein
```

## Start up italianquiz server

```
git clone git://github.com/ekoontz/italianquiz.git italianquiz
cd italianquiz/
lein ring server-headless
```

You should see output such as:

```
WARNING: resolve already refers to: #'clojure.core/resolve in namespace: italianverbs.unify, being replaced by: #'italianverbs.unify/resolve
WARNING: get-in already refers to: #'clojure.core/get-in in namespace: italianverbs.unify, being replaced by: #'italianverbs.unify/get-in
WARNING: merge already refers to: #'clojure.core/merge in namespace: italianverbs.unify, being replaced by: #'italianverbs.unify/merge
WARNING: get-in already refers to: #'clojure.core/get-in in namespace: italianverbs.test.generate, being replaced by: #'italianverbs.unify/get-in
WARNING: resolve already refers to: #'clojure.core/resolve in namespace: italianverbs.test.generate, being replaced by: #'italianverbs.unify/resolve
WARNING: merge already refers to: #'clojure.core/merge in namespace: italianverbs.test.generate, being replaced by: #'italianverbs.unify/merge
WARNING: get-in already refers to: #'clojure.core/get-in in namespace: italianverbs.sandbox, being replaced by: #'italianverbs.sandbox/get-in
WARNING: test already refers to: #'clojure.core/test in namespace: italianverbs.quiz, being replaced by: #'italianverbs.quiz/test
2013-07-31 04:50:48.152:INFO:oejs.Server:jetty-7.6.1.v20120215
2013-07-31 04:50:48.313:INFO:oejs.AbstractConnector:Started SelectChannelConnector@0.0.0.0:3000
Started server on port 3000
```

At this point you may point your browser at http://yourhostname/italian and you are up and running!

## Enabling Workbook Mode

Workbook allows you to access a web-based Clojure REPL (read-eval-print loop) running within the italianquiz server. For security, this REPL is
within a Clojail environment (https://github.com/flatland/clojail). The REPL is available at http://yourhostname/italian/workbook but
you must also add a .java.policy to your home directory like so:

```
echo "
grant {
  permission java.security.AllPermission;
};" > ~/.java.policy

```

Note that the above is very permissive and not recommended. I intend to narrow this down once I understand how .java.policy rules work.

# Hacking:

## Server path routing

A client interacts with italianquiz through an HTTP interface. The
HTTP routes that a client can access are defined in src/italianverbs/core.clj.

To modify the routes:

1. Start with src/italianverbs/core.clj
2. Follow the routes in (defroutes main-routes).
3. Look at :body attribute for each route:
   This holds the function that is executed for that route.

## Ajax, Routes, and Page Structure

1. create a static html page whose head is:

(html/head)

This includes all the necessary CSS and javascript.

and a <body> that simply has:

2. <body onload="ajax_quiz()"/>

3. The ajax_quiz() javascript function is defined in resources/public/js/quiz.js.

See quiz.clj:quiz/minimal() for a minimal function that does step 1.
See core.clj:defroutes()'s "/quiz/minimal" for a route that calls quiz/minimal().

Sequentially:

-ajax_quiz() creates the form elements: empty #quiz_table and user-guess-submittal input form and button.
ajax_quiz() then calls get_next_question() which AJAX-GETs /guess/question/. Resulting content is put in
top of form to pose question to user.

-button element created in previous step calls submit_user_response() with the user's guess.

-submit_user_response() AJAX-POSTs to /evaluate/tr.

-resulting content (evaluation of user's guess) is appended by submit_user_response()'s callback to #quiz_table.

Schematically:

    ajax_quiz()
    \---> get_next_question() ---> "GET /guess/question" ---HTML---> #ajax_question
    |
    \---> user clicks button ---> submit_user_response() ---> "POST /evaluate/tr" --HTML---> #quiz_table
    |
    \---> user checks a preference box ---> submit_quiz_filters() --> "POST /quiz/filter/ajax" ---(quiz/set-filters) 
                                                                                           |
                                                                                           \--> --302 GET /quiz/filter/ajax --HTML--> #prefs


Dependencies:

    ...................................................
    .  .  .   .  .  .   .                             .
    .  .  .   .  .  .   . quiz                        .
    .  .  .   .  .  .   ...............................
    .  .  .   .  .  .          .         .      .     . 
    .  .  .   .  .  . generate .         . lev  . xml . 
    .  .  .   .  .  ............         ..............
    .  .  .   .  .             .         .      .         
    .  .  .   .  .  search     . lexicon . html .         
    .  .  .   .  ...............         ........         
    .  .  .   .                .         .                
    .  .  .   . grammar        .         .
    .  .  .   ............................ 
    .  .  .                              .
    .  .  .  lexiconfn                   .
    .  .  ................................
    .  .                                 .
    .  . morphology                      .
    .  ...................................
    .                                    .
    .  fs                                .
    ......................................

Note that morphology does not have direct access to the lexicon for now: it should
only be concerned with regular morphology rules, which excludes lexical information
(or so we assume for now).

# Populating mongodb with questions

To populate mongodb with a set of questions, run:

    sh ./populate.sh <number of questions>

which is simply a wrapper around:

    lein run -m italianverbs.populate <number of questions>

# License:

-resources/public/js/jsquery-1.6.4.min.js
  is provided under the LGPL version 2 per http://jquery.org/license/.
-remainder: see LICENSE file.

Structure sharing using Clojure refs

    user> (def myref (ref {}))
    #'user/myref
    user> (def mymap2 {:a myref :b myref})
    #'user/mymap2
    user> (dosync (ref-set myref {:c 42}))
    {:c 42}
    user> mymap2
    {:a #<Ref@55187eb3: {:c 42}>, :b #<Ref@55187eb3: {:c 42}>}
    user> (dosync (ref-set myref {:c 43}))
    {:c 43}
    user> mymap2
    {:a #<Ref@55187eb3: {:c 43}>, :b #<Ref@55187eb3: {:c 43}>}
    user> (dosync (alter myref (fn [ref] (merge ref {:foo 99}))))
    {:foo 99, :c 43}
    user> mymap2
    {:a #<Ref@55187eb3: {:foo 99, :c 43}>, :b #<Ref@55187eb3: {:foo 99, :c 43}>}
    user> (dosync (alter myref (fn [ref] (merge ref {:foo 100}))))
    {:foo 100, :c 43}
    user> mymap2
    {:a #<Ref@55187eb3: {:foo 100, :c 43}>, :b #<Ref@55187eb3: {:foo 100, :c 43}>}
    user>


