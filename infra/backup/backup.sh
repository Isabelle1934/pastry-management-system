#!/bin/sh
# Um backup: banco (pg_dump, formato custom) + arquivos enviados (tar.gz), na mesma pasta datada.
# Variáveis: PGHOST, PGDATABASE, PGUSER, PGPASSWORD; /uploads (somente leitura) e /backups.
set -eu

stamp=$(date +%Y-%m-%d_%H%M%S)
target="/backups/$stamp"
tmp="/backups/.tmp-$stamp"
mkdir -p "$tmp"

pg_dump --format=custom --no-owner --file="$tmp/database.dump"
tar -czf "$tmp/uploads.tar.gz" -C /uploads .
# Confere se o dump pode ser lido antes de considerar o backup válido.
pg_restore --list "$tmp/database.dump" > /dev/null

mv "$tmp" "$target"
echo "Backup concluído: $target ($(du -sh "$target" | cut -f1))"

# Remove backups mais antigos que BACKUP_KEEP_DAYS dias.
find /backups -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"${BACKUP_KEEP_DAYS:-14}" -exec rm -rf {} +
