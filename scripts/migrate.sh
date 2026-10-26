#!/usr/bin/env bash
# Applies every scripts/migrations/*.sql not yet recorded in schema_migrations, in the order git first
# added them, recording each (with its sha256) once it succeeds. Files in baseline.txt were applied by
# hand before this existed: they are recorded without running. A recorded file whose content changed
# fails the run, since it would never be applied again. Needs DB_HOST, DB_PORT, DB_USER, DB_PASSWORD,
# DB_NAME and full git history (fetch-depth: 0).
set -euo pipefail

dir=scripts/migrations
export MYSQL_PWD="$DB_PASSWORD"
db() { mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --batch --skip-column-names "$DB_NAME" "$@"; }
sum() { sha256sum "$1" | cut -d' ' -f1; }

db -e "CREATE TABLE IF NOT EXISTS schema_migrations (
         filename   VARCHAR(255) NOT NULL PRIMARY KEY,
         checksum   CHAR(64)     NOT NULL,
         applied_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP)"

while read -r name; do
  [[ "$name" == *.sql ]] && db -e "INSERT IGNORE INTO schema_migrations (filename, checksum) VALUES ('$name', '$(sum "$dir/$name")')"
done < "$dir/baseline.txt"

# Oldest first by the commit that added each file; a file not committed yet goes last.
ordered=$( { git log --reverse --diff-filter=A --name-only --format= -- "$dir/*.sql"; ls "$dir"/*.sql; } \
  | sed "s#^$dir/##" | awk '!seen[$0]++')

for name in $ordered; do
  [ -f "$dir/$name" ] || continue
  checksum=$(sum "$dir/$name")
  recorded=$(db -e "SELECT checksum FROM schema_migrations WHERE filename = '$name'")
  if [ -n "$recorded" ]; then
    [ "$recorded" = "$checksum" ] || { echo "::error::$name changed after it was applied; add a new migration instead"; exit 1; }
    continue
  fi
  echo "Applying $name"
  db < "$dir/$name"
  db -e "INSERT INTO schema_migrations (filename, checksum) VALUES ('$name', '$checksum')"
done
echo "Migrations up to date"
