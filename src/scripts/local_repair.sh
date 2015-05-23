#!/bin/sh

DATABASE_URL="postgres://verbcoach:verbcoach@localhost:5432/verbcoach"
USE_SSL="false"

if [[ $1 ]]; then
    lein run -m italianverbs.repair.$1/repair
else
    echo "usage: local_repair <number of repair to run>"
fi
