# Plano de Implementação

Uma fase por vez. Ao final de cada fase: testes executados e resultado real, arquivos alterados, decisões registradas em [DECISIONS.md](DECISIONS.md), documentação atualizada e aprovação antes de seguir.

## Diagnóstico do ambiente (29/10/2026)

| Item | Situação | Ação |
|---|---|---|
| Repositório | Pasta vazia, **sem git** | `git init` + `.gitignore` na Fase 1 |
| Java / JDK | Temurin 25.0.5 em `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot` (`JAVA_HOME` de sistema configurado; terminais abertos antes da instalação precisam ser reabertos) | ✅ |
| Maven | Não instalado | Desnecessário: usar Maven Wrapper |
| Node.js / npm | v24.15.0 / 11.12.1 | OK (Node 24 LTS) |
| Docker | 29.5.2 instalado, **daemon parado** | Abrir o Docker Desktop (PostgreSQL local e Testcontainers) |
| PostgreSQL | Cliente `psql` ausente | Rodar PostgreSQL 18 em container |
| Git | 2.45.1 | OK |
| SO | Windows 11 Pro | Scripts de infra pensados para Linux (servidor) |

## Fases

### Fase 0 — Análise e planejamento ✅ (este documento)
Documentos em `docs/`, diagnóstico, pendências.

### Fase 1 — Estrutura inicial ✅ (30/10/2026)
- `git init`, `.gitignore`, `.editorconfig`, `.env.example`.
- Backend Spring Boot (Initializr/BOM) com versões fixadas após checagem de compatibilidade; perfis `dev`/`test`/`prod`; endpoint `GET /api/public/health`.
- Frontend Vite + React + TS; proxy `/api` → `localhost:8080`; tokens de estilo (placeholder neutro até receber a identidade oficial).
- `infra/docker-compose.dev.yml` com PostgreSQL 18.
- **Aceite:** backend sobe conectado ao banco; frontend exibe resposta do health; `mvnw test` e `npm run build` passam.

### Fase 2 — Banco de dados ✅ (30/10/2026)
- Flyway V1 (base), tratamento global de erros, estrutura de histórico/auditoria, Testcontainers.
- **Aceite:** migrations aplicam do zero; teste de integração de persistência passa.

### Fase 3 — Autenticação ✅ (02/11/2026)
- Login/logout por sessão, CSRF, rate limit, reset de senha por e-mail (Mailpit no dev), criação da conta inicial por variável de ambiente, tela de login e rotas protegidas.
- **Testes:** login ok/erro, bloqueio por tentativas, CSRF ausente → 403, rota admin sem sessão → 401, ciclo de reset (expiração e uso único).

### Fase 4 — Produtos e configurações ✅ (02/11/2026)
- CRUD de tamanhos/massas/recheios (desativação), configurações da confeitaria, telas administrativas.
- **Testes:** validações, limites de seleção, desativação preserva pedidos.

### Fase 5 — Agenda ✅ (02/11/2026)
- Modelo semanal, bloqueios, antecedência, cálculo de disponibilidade, `slot_occupancy`/`slot_reservation`, serviço de reserva/liberação.
- **Testes:** geração de horários (incluindo fuso e virada de dia), bloqueios, **teste de concorrência** com N threads confirmando no mesmo horário com capacidade K → exatamente K sucessos.

### Fase 6 — Encomendas públicas ✅ (02/11/2026 — visual neutro provisório até P1)
- Página inicial (com identidade visual oficial), formulário em 5 etapas, uploads seguros, geração de código, tela de sucesso.
- **Testes:** validações backend, upload com extensão falsa rejeitado, arquivo grande rejeitado, código único.

### Fase 7 — Pedidos e orçamentos
- Lista/detalhe/edição/recusa/cancelamento, histórico, orçamentos com itens, versões, envio, aceite/recusa pela cliente, verificação do acompanhamento.
- **Testes:** cálculo de totais e sinal, imutabilidade após envio, versões, transições inválidas.

### Fase 8 — Pagamentos e acompanhamento
- Comprovante do sinal, confirmação/rejeição, registro manual, resumo financeiro do pedido, página de acompanhamento completa, processo agendado de vencimento e avisos por e-mail.
- **Testes:** confirmação reserva capacidade, horário lotado, pagamentos preservados ao revisar orçamento, job idempotente (executar duas vezes = mesmo resultado).

### Fase 9 — Produção
- Atualização de andamento, histórico, lista de próximas retiradas.

### Fase 10 — Financeiro
- Despesas e categorias, indicadores por período.
- **Testes:** previsto × recebido, despesa vinculada contada uma vez.

### Fase 11 — Dashboard e acabamento
- Indicadores, revisão de responsividade (celular e desktop), acessibilidade básica, revisão visual com a proprietária.

### Fase 12 — Testes e implantação
- Fluxo completo E2E (Playwright), revisão de segurança, configuração de produção (Docker Compose + Caddy), backups com teste de restauração, `docs/DEPLOYMENT.md`.

## Dependências entre fases

Fases 3–5 dependem da 2. A 6 depende de 4 e 5. A 7 depende de 6. A 8 depende de 5 e 7. A identidade visual oficial é necessária até o início da **Fase 6**; o provedor de e-mail, até a **Fase 3** (no desenvolvimento usa-se Mailpit local); a hospedagem, até a **Fase 12**.
