# Arquitetura

## 1. Visão geral

Monólito modular: **uma** aplicação Spring Boot (API REST) + **uma** SPA React, servidas sob o **mesmo domínio**. É a solução mais simples de operar para uma única usuária administrativa e uma carga pequena, sem abrir mão de organização por módulos.

```
                 ┌──────────────────────────── servidor (VPS) ────────────────────────────┐
 navegador ──TLS──▶ Caddy (proxy reverso, HTTPS automático)                               │
                 │   ├── /            → arquivos estáticos do frontend (build do Vite)     │
                 │   └── /api/**      → backend Spring Boot :8080                          │
                 │                          ├── PostgreSQL 18 (volume persistente)        │
                 │                          ├── /data/uploads (volume persistente, privado)│
                 │                          └── SMTP externo (recuperação de senha, avisos)│
                 │   tarefa de backup diária → pg_dump + arquivos → cópia externa          │
                 └─────────────────────────────────────────────────────────────────────────┘
```

Por estar no mesmo domínio, o cookie de sessão é *first-party*, não há CORS em produção e o CSRF pode ser tratado de forma padrão.

## 2. Stack e versões

| Camada | Escolha | Observação |
|---|---|---|
| Linguagem | Java 25 LTS (Temurin) | LTS mais recente |
| Framework | Spring Boot 4.1.2 | Fixada na Fase 1 (ADR-011) |
| Build | Maven via **Maven Wrapper** (`mvnw`) | Dispensa instalação global do Maven |
| Persistência | Spring Data JPA (Hibernate) + Flyway | `ddl-auto=validate`; esquema só muda por migration |
| Banco | PostgreSQL 18 | Docker local e em produção |
| Sessão | Spring Session JDBC | Sessões sobrevivem a reinício do servidor |
| E-mail | Spring Mail (SMTP) | Provedor a definir |
| Agendamento | `@Scheduled` + ShedLock (JDBC) | Execução única mesmo se houver mais de uma instância |
| Detecção de arquivo | Assinatura binária própria (`FileType`) | 4 formatos aceitos; sem dependência extra (ADR-006 revisada) |
| Testes | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), MockMvc | Testes de integração com banco real |
| Frontend | React 19.3, TypeScript 7.0, Vite 8.3, oxlint; React Router a partir da Fase 3 | CSS Modules + variáveis CSS (tokens da identidade visual) |
| Formulários | Validação própria leve ou `react-hook-form` + `zod` | Decisão na Fase 6 conforme complexidade |
| Testes frontend | Vitest 5 (funções puras desde a Fase 4); Testing Library e Playwright para fluxos completos (Fase 12) | |

Regra: nenhuma dependência é adicionada sem necessidade clara; versões experimentais/descontinuadas não são usadas.

## 3. Estrutura de pastas

```
/
├── backend/
│   ├── mvnw, pom.xml
│   └── src/
│       ├── main/java/<pacote-base>/
│       │   ├── ConfeitariaApplication.java
│       │   ├── config/            # Jackson, clock, scheduler, propriedades tipadas
│       │   ├── security/          # SecurityFilterChain, CSRF, rate limit, UserDetails
│       │   ├── common/
│       │   │   ├── exception/     # exceções de domínio + @RestControllerAdvice (ProblemDetail)
│       │   │   ├── validation/    # validadores customizados (WhatsApp, dinheiro…)
│       │   │   └── money/ time/   # utilitários de valor e fuso
│       │   └── modules/
│       │       ├── auth/          # login, logout, reset de senha
│       │       ├── settings/      # configurações da confeitaria
│       │       ├── catalog/       # tamanhos, massas, recheios
│       │       ├── schedule/      # agenda, bloqueios, disponibilidade, reservas
│       │       ├── order/         # pedidos, topo, histórico
│       │       ├── quote/         # orçamentos e versões
│       │       ├── payment/       # pagamentos e comprovantes
│       │       ├── production/    # andamento da produção
│       │       ├── finance/       # despesas e indicadores
│       │       ├── dashboard/     # agregações
│       │       ├── tracking/      # acesso público ao pedido por código
│       │       ├── file/          # armazenamento e validação de arquivos
│       │       └── notification/  # avisos internos e e-mail
│       │     cada módulo contém: controller/ service/ repository/ entity/ dto/ (mapper/)
│       ├── main/resources/
│       │   ├── application.yml, application-dev.yml, application-prod.yml
│       │   └── db/migration/      # V1__..., V2__...
│       └── test/java/...          # unitários + integração (Testcontainers)
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig.json
│   └── src/
│       ├── components/   # componentes reutilizáveis (Button, Field, Stepper, Money…)
│       ├── pages/        # public/ (Home, OrderForm, Tracking) e admin/ (Dashboard, Orders…)
│       ├── layouts/      # PublicLayout, AdminLayout
│       ├── services/     # cliente HTTP (fetch) e chamadas por módulo
│       ├── hooks/        # useAuth, useAvailability, …
│       ├── contexts/     # AuthContext
│       ├── types/        # tipos dos DTOs
│       ├── routes/       # definição de rotas + rota protegida
│       ├── styles/       # tokens.css (cores/fontes oficiais), reset, globais
│       ├── utils/        # funções puras (validações de formulário)
│       └── assets/       # logo e imagens oficiais
├── infra/
│   ├── docker-compose.dev.yml     # PostgreSQL local
│   ├── docker-compose.prod.yml    # Caddy + backend + PostgreSQL
│   ├── Caddyfile
│   └── backup/                    # scripts de backup e restauração
├── docs/
├── .env.example
└── README.md
```

**Organização:** pacote por módulo (feature) com camadas dentro de cada módulo. Mantém as responsabilidades separadas (controller → service → repository) e evita pastas gigantes com tudo misturado. Entidades nunca saem da API: controllers recebem e devolvem DTOs (Java `record`).

## 4. Camadas e responsabilidades

- **Controller:** HTTP, validação de formato (`@Valid`), conversão DTO ↔ chamada de serviço. Sem regra de negócio.
- **Service:** regras de negócio, transações (`@Transactional`), transições de estado, registro de histórico.
- **Repository:** Spring Data JPA; consultas específicas com JPQL/SQL nativo quando necessário (ex.: reserva de capacidade).
- **Entity:** mapeamento JPA; transições de estado encapsuladas em métodos da entidade quando simples.
- **Tratamento de erros:** `GlobalExceptionHandler` (`common/exception`) devolve `application/problem+json` (RFC 9457) com código estável, mensagem em português e `requestId`, sem stack trace. Serviços lançam `NotFoundException` (404), `ConflictException` (409) ou `BusinessRuleException` (422, com código da regra, ex.: `SLOT_FULL`).
- **Rastreio:** `RequestIdFilter` gera/propaga `X-Request-Id` e o coloca no MDC; toda linha de log mostra o ID.
- **Tempo:** um bean `Clock` (UTC) é injetado nos serviços — nada chama `Instant.now()` diretamente, para que prazos e vencimentos sejam testáveis.
- **Auditoria:** `AuditService.record(...)` (`common/audit`) exige transação ativa e grava junto com a operação principal (se ela falhar, o registro também é desfeito).

## 5. Autenticação e autorização

**Escolha: sessão no servidor com cookie, não JWT.** (ver ADR-003)

- Login via `POST /api/admin/auth/login` (JSON). Sessão armazenada no PostgreSQL (Spring Session JDBC).
- Cookie `SESSION`: `HttpOnly`, `Secure` (produção), `SameSite=Lax`, `Path=/`. Nenhum token fica em `localStorage`.
- Rotação do ID de sessão no login (proteção contra *session fixation*); expiração por inatividade (ex.: 8 h) configurável.
- **CSRF:** `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN` legível pelo JS, enviado no header `X-XSRF-TOKEN`) em todas as requisições que alteram estado — inclusive as públicas.
- **Senhas:** `DelegatingPasswordEncoder` com BCrypt (custo 12). Senha mínima de 10 caracteres.
- **Recuperação de senha:** token aleatório de 256 bits enviado por e-mail; no banco fica apenas o hash SHA-256; validade de 30 min; uso único; todas as sessões existentes são encerradas após a troca. A resposta da solicitação é sempre a mesma, exista ou não o e-mail.
- **Primeira conta:** criada na inicialização a partir de `APP_ADMIN_EMAIL`, `APP_ADMIN_NAME` e `APP_ADMIN_INITIAL_PASSWORD`, somente se não existir nenhuma conta. A senha é provisória: até trocá-la, o painel responde 403 `PASSWORD_CHANGE_REQUIRED` (exceto `/api/admin/auth/**`). Não há endpoint de cadastro.
- **Link de redefinição:** `APP_PUBLIC_BASE_URL/admin/redefinir-senha#token=…`. O token vai no fragmento da URL (não é enviado ao servidor nem a logs de proxy) e o frontend o remove da barra de endereços ao abrir a página. O e-mail é enviado após o commit, de forma assíncrona (tempo de resposta igual exista ou não o e-mail).
- **Rotas:** `/api/admin/**` exige autenticação; `/api/public/**` e `/api/tracking/**` são públicas com limitação de taxa. No frontend, rota protegida apenas melhora a UX — a proteção real é o backend.

## 6. Proteções gerais

- **Rate limiting** (em memória, `FixedWindowCounter`, por IP e por identificador — suficiente para instância única, ADR-018): login (5 falhas / 15 min por e-mail; 20 por IP), pedidos de redefinição (3/h por e-mail, silencioso; 10/h por IP), uso de token de redefinição (10 / 15 min por IP). Nas próximas fases: criação de pedidos, verificação de acompanhamento, uploads. Atrás do proxy, o IP real vem de `X-Forwarded-For` (`forward-headers-strategy: framework` no perfil `prod`).
- **CORS:** desabilitado em produção (mesma origem); em desenvolvimento, apenas `http://localhost:5173`. Lista vinda de variável de ambiente.
- **Headers:** `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `X-Frame-Options: DENY`, HSTS (no Caddy).
- **Logs:** erros com ID de correlação; nomes, WhatsApp, códigos de acompanhamento, tokens e senhas **nunca** registrados (mascaramento).
- **Segredos:** somente variáveis de ambiente; `.env.example` sem valores reais.
- **Histórico:** `order_history` para eventos do pedido; `audit_log` para mudanças administrativas (configurações, agenda, catálogo).

## 7. Armazenamento de arquivos

- Diretório fora de qualquer pasta pública (`/data/uploads`, volume persistente), sem permissão de execução, servido **somente** pelo backend.
- Nome interno: `xx/UUID.bin` (subpasta pelos 2 primeiros caracteres), sem o nome nem a extensão original; metadados (nome original saneado, tipo detectado, tamanho, SHA-256) no banco (`stored_file`). Gravação em arquivo temporário + movimento atômico; se a transação for desfeita, o arquivo é apagado.
- Diretório: `APP_UPLOAD_DIR` (dev: `backend/data/uploads`, fora do git).
- Validação: extensão permitida **e** tipo detectado pelo conteúdo (assinatura binária — `FileType.detect`) devem coincidir com a lista permitida:
  - Fotos de referência: JPG, PNG, WEBP — até 5 MB cada.
  - Comprovantes: JPG, PNG, PDF — até 5 MB.
  - O `Content-Type` enviado pelo navegador é ignorado.
- Download: via endpoint autenticado (admin) ou autorizado pela sessão de acompanhamento daquele pedido; resposta com `Content-Type` detectado, `X-Content-Type-Options: nosniff`, `Content-Disposition: attachment` para PDF e `Cache-Control: private, no-store`.
- Limites do multipart no Spring e no proxy.
- Interface `FileStorage` (implementação local) para permitir trocar por armazenamento de objetos no futuro sem alterar os módulos.

## 8. Implantação (recomendação inicial)

Plataformas gratuitas costumam não oferecer disco persistente ou desligam a aplicação por inatividade — o que quebra uploads e o processo agendado de vencimento. Recomendação: **VPS pequena de baixo custo** (1–2 GB de RAM) com Docker Compose (Caddy + backend + PostgreSQL), volumes persistentes e backup diário enviado para um armazenamento externo. A escolha final está nas pendências ([DECISIONS.md](DECISIONS.md)).

## 9. Backups

- Diário: `pg_dump` em formato custom + arquivo compactado de `/data/uploads`, com retenção (ex.: 7 diários, 4 semanais).
- Cópia para fora do servidor (armazenamento de objetos ou outro destino a definir).
- Procedimento de restauração documentado e testado na Fase 12.
