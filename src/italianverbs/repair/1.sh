#!/bin/sh

#DATABASE_URL=postgres://<user>:<password>@<host>:<port>/<db>

lein run -m italianverbs.repair.1/repair
