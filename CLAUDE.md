# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Web system for a cake shop's custom orders: a public form + order tracking for customers, and an owner-only admin panel (orders, quotes, payments, schedule, production, finance, dashboard, settings). **All UI text, docs, code comments and commit messages are in Brazilian Portuguese** — keep it that way.

## Commands

Dev environment (Docker Desktop must be running; Postgres + Mailpit at http://localhost:8025):

```bash
docker compose -f infra/docker-compose.dev.yml up -d --wait
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # API on :8080
cd frontend && npm run dev                                             # SPA on :5173, proxies /api to :8080
```

Backend (`backend/`, Java 25, Spring Boot 4.1):

```bash
./mvnw test                                   # all tests (integration tests start Postgres via Testcontainers)
./mvnw test -Dtest=ProductionIntegrationTests     # one class
./mvnw test -Dtest='ProductionIntegrationTests#method'
```

Frontend (`frontend/`, React 19 + TS + Vite, Vitest, oxlint):

```bash
npm test                      # vitest run
npx vitest run src/utils/money.test.ts
npm run lint && npm run build # build = tsc -b + vite build
npm run e2e                   # Playwright, against the local production stack only — see docs/DEPLOYMENT.md §5
```

Production: Docker Compose (`infra/docker-compose.prod.yml`, env from `.env.prod`) with Postgres, API, Caddy (serves the built SPA, HTTPS, proxies `/api`) and a daily backup service. Full procedure, restore and E2E setup in `docs/DEPLOYMENT.md`.

## Architecture

Single origin by design: the browser always talks to one host (Vite proxy in dev, Caddy in prod), so session cookies are first-party. Auth is a **server-side session** (Spring Session JDBC, `SESSION` cookie) — no JWT, nothing in `localStorage`. CSRF uses the `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header on every state-changing request, public ones included; `frontend/src/services/api.ts` (`apiFetch`) handles this and turns ProblemDetail errors into `ApiError` (with `code` and `fieldErrors`).

URL spaces: `/api/admin/**` (authenticated; 403 `PASSWORD_CHANGE_REQUIRED` until the provisional password is changed), `/api/public/**` and `/api/tracking/**` (public, rate-limited per IP; tracking access = code + last 4 WhatsApp digits, 2 h session).

### Backend (`br.com.confeitaria`)

- `modules/<feature>/` — one package per domain (order, quote, payment, schedule, production, finance, dashboard, tracking, catalog, settings, notification, deadline, file, auth), each split into `controller/ dto/ entity/ repository/ service/`. Controllers hold no business rules; services own transactions, state transitions and history.
- `common/` — `exception` (`GlobalExceptionHandler` → RFC 9457 ProblemDetail with stable `code` + `requestId`; throw `NotFoundException`/`ConflictException`/`BusinessRuleException`), `audit` (`AuditService.record` requires an active transaction), `ratelimit` (in-memory, single instance), `text/Texts` (pt-BR `brl`/`date` formatting for owner-facing messages), `web` (`RequestIdFilter`).
- **Time:** inject the `Clock` bean (UTC) — never `Instant.now()`. "Today" for the bakery must be `LocalDate.ofInstant(clock.instant(), properties.timezone())` (America/Sao_Paulo); `LocalDate.now(clock)` gives the wrong day.
- **Optimistic locking:** edits carry `version`; a mismatch returns 409 `CONCURRENT_MODIFICATION`. Money is `BigDecimal`, serialized as a string.
- Order state is two axes: commercial status (quote/deposit flow) and production status (`CakeOrder.changeProduction`, one step at a time, only for confirmed orders). Rules live in `docs/BUSINESS_RULES.md`.
- **Database:** Flyway migrations `src/main/resources/db/migration/V*.sql`; Hibernate runs with `ddl-auto=validate`, so every entity change needs a new migration. History, payments and expenses are protected from DELETE by triggers (logical delete via `deleted_at`).
- Uploads: validated by extension **and** content signature (`FileType.detect`), stored outside public dirs with random names, served only through authorized endpoints.
- Jackson 3 (`tools.jackson` packages).

Tests: annotate integration tests with `support/IntegrationTest` (full context, MockMvc with security, shared Testcontainers DB, `MutableClock`, `RecordingMailSender`). Because the DB is shared across test classes, assert before/after deltas or use unique far-future dates instead of absolute counts.

### Frontend (`frontend/src`)

- `routes/router.tsx` + `routes/guards.tsx` (`RequireAuth`, `GuestOnly` — UX only; the backend enforces access). Layouts: `PublicLayout` (`wide` for full-width pages), `AdminLayout` (sidebar; drawer below 900px), `AuthLayout`.
- Data: `services/*.ts` per backend module, `types/*.ts` mirroring DTOs, `hooks/useLoader` for loading/error/reload state.
- Styling: CSS Modules + design tokens in `styles/tokens.css` (palette off-white/nude/champagne/brown/chocolate, spacing, radii, shadows, motion), shared animations in `styles/motion.module.css`, `prefers-reduced-motion` honored globally. Fonts are self-hosted via `@fontsource` because the production CSP (`infra/caddy/Caddyfile`) only allows `'self'` for scripts, styles and fonts — no CDNs or inline `<style>`/`<script>`.
- Reusable UI in `components/` (`Button`, `TextField`/`SelectField`/`TextAreaField`, `Alert`, `Loading`, `Icon` with in-house SVGs, `BrandMark`, `PhotoSlot`).
- Shop photos and logo come only from `content/site.ts` (files in `public/fotos/`); while empty, the site shows clearly labeled temporary placeholders. Never add stock or invented product photos.
- The E2E test (`e2e/orderLifecycle.e2e.ts`) selects by accessible names/labels (e.g. "Continuar", "Revise sua encomenda", "Olá…") — keep those texts stable or update the test.

## Docs

`docs/` holds the PRD, architecture, DB model, API reference (`API.md` — update when endpoints change), business rules, ADRs and open owner decisions (`DECISIONS.md`), deployment and security review.

Pending owner decisions: P1 (logo/photos), P2 (hosting/domain), P3 (SMTP provider) and P14 (personal data retention).
