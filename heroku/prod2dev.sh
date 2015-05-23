#!/bin/sh 

# replace the dev app (verbcoach-dev)'s database with a copy of 
# the contents of the production app (verbcoach)'s database.

heroku maintenance:on -a verbcoach-dev

# create a dump of the production site:
heroku pg:backups capture -a verbcoach

# find the key of the new dump:
BACKUP_KEY=$(heroku pg:backups -a verbcoach | grep -A3 Backups | tail -n 1 | awk '{print $1}')

# get the URL from the key:
URL=$(heroku pg:backups -q -a verbcoach public-url ${BACKUP_KEY})

# restore from the above URL:
heroku pg:backups restore "${URL}" DATABASE --confirm verbcoach-dev

heroku maintenance:off -a verbcoach-dev
