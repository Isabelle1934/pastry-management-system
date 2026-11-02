#!/bin/sh
# Restaura um backup criado por backup.sh. APAGA os dados atuais do banco e dos arquivos enviados.
# Uso (com a API parada), na raiz do projeto:
#   docker compose -f infra/docker-compose.prod.yml --env-file .env.prod stop backend
#   docker compose -f infra/docker-compose.prod.yml --env-file .env.prod run --rm restore 2026-11-02_030000 --confirm
#   docker compose -f infra/docker-compose.prod.yml --env-file .env.prod start backend
set -eu

name="${1:-}"
if [ -z "$name" ] || [ "${2:-}" != "--confirm" ]; then
  echo "Uso: restore.sh <pasta-do-backup> --confirm   (apaga os dados atuais)" >&2
  ls -1 /backups 2>/dev/null | grep '^20' >&2 || true
  exit 2
fi
source_dir="/backups/$name"
[ -f "$source_dir/database.dump" ] || { echo "Backup não encontrado: $source_dir" >&2; exit 1; }

echo "Restaurando banco de $source_dir ..."
pg_restore --clean --if-exists --no-owner --single-transaction --dbname="$PGDATABASE" "$source_dir/database.dump"

echo "Restaurando arquivos enviados ..."
find /uploads -mindepth 1 -delete
tar -xzf "$source_dir/uploads.tar.gz" -C /uploads

echo "Restauração concluída."
