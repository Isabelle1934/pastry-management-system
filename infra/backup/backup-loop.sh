#!/bin/sh
# Executa backup.sh na inicialização e depois a cada BACKUP_INTERVAL_SECONDS (padrão: 1 dia).
# Uma falha é registrada no log e o ciclo continua.
while true; do
  sh /scripts/backup.sh || echo "ERRO: backup falhou em $(date)" >&2
  sleep "${BACKUP_INTERVAL_SECONDS:-86400}"
done
