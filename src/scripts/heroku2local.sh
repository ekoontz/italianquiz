curl -o latest.dump $(heroku pg:backups public-url --app verbcoach)
createdb verbcoach_restore
pg_restore --verbose --clean --no-acl --no-owner -U verbcoach -d verbcoach_restore latest.dump
