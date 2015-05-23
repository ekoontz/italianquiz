#!/bin/sh

DATABASE_URL="postgres://verbcoach:verbcoach@localhost:5432/verbcoach"
USE_SSL="false"

lein ring server-headless
