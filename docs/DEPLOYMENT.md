# Implantação

Tudo roda com Docker Compose num único servidor: **PostgreSQL**, **API** (Spring Boot), **Caddy**
(site, HTTPS automático e proxy para a API) e **backup** diário. Arquivos:

| Arquivo | Para quê |
|---|---|
| `infra/docker-compose.prod.yml` | Serviços de produção |
| `.env.prod.example` | Modelo das variáveis (copiar para `.env.prod` no servidor, nunca versionar) |
| `backend/Dockerfile` | Imagem da API (usuário sem privilégios, uploads em volume próprio) |
| `infra/caddy/Dockerfile`, `infra/caddy/Caddyfile` | Compila o frontend e configura o Caddy |
| `infra/backup/*.sh` | Backup, ciclo diário e restauração |

> Pendências que dependem da proprietária: **hospedagem (P2)**, **provedor de e-mail SMTP e remetente (P3)**,
> **domínio** e **política de retenção de dados pessoais (P14)** — ver `docs/DECISIONS.md`.

## 1. Pré-requisitos

- Servidor Linux (VPS) com pelo menos 2 GB de RAM, Docker e o plugin Docker Compose.
- Um domínio (ou subdomínio) com registro DNS **A/AAAA apontando para o servidor**.
- Portas 80 e 443 liberadas no firewall (o Caddy precisa delas para obter o certificado).
- Conta num provedor SMTP (host, porta, usuário, senha e um remetente autorizado).

## 2. Primeira instalação

```bash
git clone <repositório> confeitaria && cd confeitaria
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

Preencha o `.env.prod` (salvo em **UTF-8**). Gere senhas com `openssl rand -base64 32`. Depois:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod ps
```

Todos os serviços devem ficar `healthy`/`running`. Na primeira inicialização:

1. O banco é criado pelas migrações (Flyway).
2. A conta da proprietária é criada com `APP_ADMIN_EMAIL` e a senha provisória `APP_ADMIN_INITIAL_PASSWORD`.
3. Acesse `https://SEU-DOMÍNIO/admin`, entre e **troque a senha** (o sistema exige).
4. **Apague `APP_ADMIN_INITIAL_PASSWORD` do `.env.prod`** e reinicie a API:
   `docker compose -f infra/docker-compose.prod.yml --env-file .env.prod up -d backend`
5. Em **Configurações**, preencha os dados da confeitaria, a chave Pix e o e-mail para avisos.
6. Em **Agenda** e **Produtos**, cadastre horários, tamanhos, massas e recheios.
7. Teste a recuperação de senha ("Esqueci minha senha") para confirmar que o e-mail chega.

## 3. Atualizar para uma nova versão

```bash
git pull
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod up -d --build
```

As migrações do banco rodam sozinhas na inicialização da API. Faça um backup antes (seção 4).

## 4. Backups

O serviço `backup` faz um backup ao iniciar e depois a cada 24 h, em `backups/AAAA-MM-DD_HHMMSS/`:

- `database.dump` — banco completo (`pg_dump`, verificado com `pg_restore --list` antes de ser aceito);
- `uploads.tar.gz` — fotos de referência e comprovantes.

Backups com mais de `BACKUP_KEEP_DAYS` dias (padrão 14) são apagados. **Copie a pasta `backups/` para fora
do servidor** (outro computador ou armazenamento em nuvem) regularmente — um backup que fica só no
servidor se perde junto com ele.

Backup manual a qualquer momento:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod exec backup sh /scripts/backup.sh
```

### Restaurar

A restauração **substitui** o banco e os arquivos atuais pelo conteúdo do backup escolhido.

```bash
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod stop backend
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod run --rm restore            # lista os backups
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod run --rm restore 2026-11-02_030000 --confirm
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod start backend
```

Teste de restauração realizado em 02/11/2026 na pilha local: backup → novos pedidos → restauração →
contagens de pedidos, pagamentos e arquivos (banco e disco) voltaram às do backup, e o teste E2E passou
em seguida.

## 5. Teste E2E (Playwright)

O teste `frontend/e2e/orderLifecycle.e2e.ts` percorre o ciclo inteiro no navegador: encomenda em 5 etapas →
orçamento → aceite → comprovante → confirmação → produção → saldo → entrega → financeiro. Ele roda contra a
pilha de produção **no seu computador**, com valores de teste (nunca contra o servidor real):

1. Crie um arquivo de variáveis de teste fora do repositório (ex.: `../e2e.env`) com os mesmos campos do
   `.env.prod.example`, usando: `SITE_ADDRESS=http://localhost`, `APP_PUBLIC_BASE_URL=http://localhost:8088`,
   `HTTP_PORT=8088`, `HTTPS_PORT=8443`, senhas geradas na hora e SMTP do Mailpit
   (`MAIL_HOST=host.docker.internal`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_SMTP_STARTTLS=false`,
   com `docker compose -f infra/docker-compose.dev.yml up -d mailpit`).
2. Suba a pilha com um nome de projeto separado:
   `docker compose -p confeitaria-e2e -f infra/docker-compose.prod.yml --env-file ../e2e.env up -d --build --wait`
3. Rode o teste (usa o Microsoft Edge instalado; defina `E2E_BROWSER_CHANNEL=chrome` para o Chrome):

   ```bash
   cd frontend
   E2E_ADMIN_EMAIL=<APP_ADMIN_EMAIL> E2E_ADMIN_PASSWORD=<APP_ADMIN_INITIAL_PASSWORD> \
   E2E_ADMIN_NEW_PASSWORD=<outra senha de teste> npm run e2e
   ```

   Na primeira execução o teste troca a senha provisória pela nova; nas seguintes, entra com a nova.
4. Ao terminar: `docker compose -p confeitaria-e2e -f infra/docker-compose.prod.yml --env-file ../e2e.env down -v`

## 6. Operação

| Tarefa | Comando |
|---|---|
| Ver logs da API | `docker compose -f infra/docker-compose.prod.yml --env-file .env.prod logs -f backend` |
| Ver logs do site | `... logs -f caddy` |
| Reiniciar tudo | `... restart` |
| Saúde da API | `curl https://SEU-DOMÍNIO/api/public/health` → `{"status":"UP"}` |

Cada erro mostrado na tela traz um `requestId`; procure-o nos logs da API para ver os detalhes.
