# Sistema de Gestão e Encomendas — Confeitaria

Plataforma web para centralizar encomendas personalizadas de bolos: a cliente faz a solicitação por um formulário público e acompanha o pedido por um código; a proprietária gerencia agenda, orçamentos, pagamentos, produção e financeiro em um painel administrativo.

> **Status atual:** Fase 9 concluída — andamento da produção e lista de retiradas. Fase 8 — pagamentos, prazos automáticos e avisos. Telas do painel das Fases 8 e 9 ainda sem verificação manual no navegador. Próxima: Fase 10 (financeiro). Veja [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/PRD.md](docs/PRD.md) | Requisitos do produto, escopo e fora de escopo |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arquitetura, stack, estrutura de pastas, segurança, arquivos, implantação |
| [docs/DATABASE.md](docs/DATABASE.md) | Modelo de dados, entidades, relacionamentos e restrições |
| [docs/API.md](docs/API.md) | Endpoints REST planejados |
| [docs/BUSINESS_RULES.md](docs/BUSINESS_RULES.md) | Regras de negócio, máquinas de estado, agenda, vencimento |
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | Fases, entregas e critérios de aceite |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Registro de decisões técnicas (ADRs) e pendências |

## Stack

| Camada | Versão fixada |
|---|---|
| Java | 25 LTS (Eclipse Temurin 25.0.5) |
| Spring Boot | 4.1.2 (Web MVC, Data JPA, Validation, Flyway, Actuator, Security 7.1, Session JDBC, Mail) |
| Maven | via Maven Wrapper (`backend/mvnw`) |
| PostgreSQL | 18 (imagem `postgres:18-alpine`) |
| Node.js | 24 LTS |
| Frontend | React 19.3, React Router 8.5, TypeScript 7.0, Vite 8.3, oxlint |
| Testes backend | JUnit 5, MockMvc, Testcontainers |

## Pré-requisitos

- JDK 25 com `JAVA_HOME` configurado
- Node.js 24 LTS
- Docker Desktop **em execução**

## Como rodar em desenvolvimento

1. Configuração local (opcional — sem `.env` são usados os padrões de desenvolvimento):
   ```bash
   cp .env.example .env
   ```
   Para criar a conta da proprietária na primeira execução, preencha `APP_ADMIN_EMAIL`, `APP_ADMIN_NAME` e `APP_ADMIN_INITIAL_PASSWORD` no `.env` (a senha é provisória e será trocada no primeiro acesso).
2. Banco de dados e caixa de e-mail local (Mailpit):
   ```bash
   docker compose -f infra/docker-compose.dev.yml up -d --wait
   ```
   E-mails enviados pelo sistema (ex.: redefinição de senha) aparecem em http://localhost:8025.
3. Backend (porta 8080), a partir de `backend/`:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```
4. Frontend (porta 5173), a partir de `frontend/`:
   ```bash
   npm install && npm run dev
   ```
5. Acesse http://localhost:5173 — a página deve mostrar **"Servidor conectado"**. O painel fica em http://localhost:5173/admin.

O Vite encaminha `/api` para o backend, então o navegador enxerga uma única origem (igual à produção).

## Testes e verificações

```bash
cd backend && ./mvnw test
```
Os testes de integração sobem um PostgreSQL 18 temporário via Testcontainers (Docker precisa estar ligado).

```bash
cd frontend && npm test && npm run build && npm run lint
```

## Estrutura

```
backend/   API Spring Boot (pacote br.com.confeitaria)
frontend/  SPA React + TypeScript + Vite
infra/     Docker Compose (dev; produção na Fase 12)
docs/      Documentação de produto, arquitetura e decisões
```
