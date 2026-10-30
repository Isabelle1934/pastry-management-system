# Modelo de Banco de Dados (inicial)

PostgreSQL 18. Esquema gerenciado exclusivamente por migrations Flyway. Convenções:

- Tabelas e colunas em `snake_case`, nomes em inglês (código em inglês, interface em português).
- Chave primária `id BIGINT GENERATED ALWAYS AS IDENTITY` (IDs nunca expostos à cliente — ela usa o código de acompanhamento).
- Instantes em `TIMESTAMPTZ`; data/horário de retirada em `DATE` + `TIME` (hora local de `America/Sao_Paulo`).
- Dinheiro em `NUMERIC(12,2)`; no Java, `BigDecimal`. Nunca `double`.
- Estados em `VARCHAR` com `CHECK` (legíveis e fáceis de evoluir por migration).
- `created_at`, `updated_at` em tabelas mutáveis; `version` (bloqueio otimista) em `cake_order` e entidades editáveis.
- Registros financeiros e históricos **nunca são apagados** fisicamente.

## 1. Diagrama de relacionamentos

```
admin_user 1──* password_reset_token
bakery_settings (linha única)

cake_size ─┐        cake_flavor     cake_filling
           │            │                │
           ▼            ▼                ▼
cake_order 1──* order_flavor      order_filling *──1 cake_order
   │ 1──0..1 cake_topper
   │ 1──* stored_file (fotos, comprovantes)
   │ 1──* quote 1──* quote_item
   │ 1──* payment ──0..1 stored_file (comprovante)
   │ 1──0..1 slot_reservation ──* slot_occupancy (data+horário)
   │ 1──* order_history
   │ 1──* expense (opcional)
   │ 1──* notification

weekly_slot (modelo semanal de horários)   schedule_block (bloqueios)
expense_category 1──* expense
audit_log, shedlock, spring_session*
```

## 2. Tabelas

### Autenticação
**admin_user** — `id`, `email` (único, minúsculas), `name`, `password_hash`, `enabled`, `must_change_password`, `password_changed_at`, `created_at`, `updated_at`.

**password_reset_token** — `id`, `admin_user_id` FK, `token_hash` (SHA-256, único), `expires_at`, `used_at`, `created_at`. Tokens anteriores invalidados ao gerar um novo.

**spring_session / spring_session_attributes** — tabelas padrão do Spring Session JDBC.

### Configurações
**bakery_settings** (uma linha, `id = 1` com `CHECK`) — `name`, `about_text`, `whatsapp_contact`, `instagram`, `address`, `pickup_instructions`, `pix_key`, `pix_holder_name`, `min_lead_time_hours`, `max_booking_days_ahead`, `quote_validity_hours`, `quote_expiry_warning_hours`, `deposit_deadline_hours` (pendente de definição), `owner_notification_email`, `updated_at`.
O percentual do sinal (50%) é regra fixa em código, conforme requisito.

### Catálogo
**cake_size** — `id`, `name`, `description`, `servings`, `base_price`, `max_flavors`, `max_fillings`, `active`, `display_order`.
**cake_flavor** (massa) — `id`, `name`, `description`, `active`, `display_order`.
**cake_filling** (recheio) — `id`, `name`, `description`, `active`, `display_order`.
Itens de catálogo são desativados, não apagados, quando já usados em pedidos.

### Agenda
**weekly_slot** — modelo semanal: `id`, `day_of_week` (1–7), `start_time`, `capacity` (> 0), `active`. Único (`day_of_week`, `start_time`). Um dia sem horários ativos = dia sem funcionamento.

**schedule_block** — `id`, `block_date`, `start_time` (NULL = dia inteiro), `reason`, `created_at`. Único (`block_date`, `start_time`).

**slot_occupancy** — contador por horário concreto, usado para garantir capacidade:
`slot_date`, `slot_time` (PK composta), `reserved_count INT NOT NULL CHECK (reserved_count >= 0)`, `updated_at`.

**slot_reservation** — `id`, `order_id` FK **único**, `slot_date`, `slot_time`, `status` (`ACTIVE`, `RELEASED`), `created_at`, `released_at`, `release_reason`.
Índice único parcial: um pedido tem no máximo uma reserva `ACTIVE`.

### Pedidos
**cake_order**
- Identificação: `id`, `tracking_code` (único, 16 caracteres aleatórios), `created_at`, `updated_at`, `version`.
- Cliente: `customer_name`, `customer_whatsapp` (normalizado E.164, ex. `+5511999998888`).
- Agendamento solicitado: `pickup_date`, `pickup_time`.
- Personalização: `size_id` FK, `size_name_snapshot`, `decoration`, `cake_text`, `notes`.
- **Estados separados:** `commercial_status`, `production_status`, `payment_status` (ver BUSINESS_RULES.md). `payment_status` é derivado dos pagamentos e mantido pelo serviço na mesma transação.
- Encerramento: `cancelled_at`, `cancellation_reason`, `cancelled_by` (`OWNER`, `CLIENT`, `SYSTEM`).

**order_flavor / order_filling** — `order_id`, `flavor_id`/`filling_id`, `name_snapshot`. PK (`order_id`, item). Quantidade validada contra `max_flavors`/`max_fillings` do tamanho.

**cake_topper** — `order_id` PK/FK, `theme`, `name`, `age`.

**order_history** — `id`, `order_id`, `event_type` (`CREATED`, `EDITED_BY_CLIENT`, `EDITED_BY_OWNER`, `STATUS_CHANGED`, `QUOTE_SENT`, `QUOTE_ACCEPTED`, `PAYMENT_CONFIRMED`, `EXPIRED`, …), `actor` (`CLIENT`, `OWNER`, `SYSTEM`), `description`, `details JSONB` (valores antes/depois, sem dados sensíveis desnecessários), `created_at`. Somente inserção.

### Orçamentos
**quote** — `id`, `order_id`, `version_number`, `status`, `total`, `deposit_amount`, `notes`, `valid_until`, `sent_at`, `responded_at`, `created_at`, `expired_at`, `expiry_reason`.
- Único (`order_id`, `version_number`).
- Índice único parcial: no máximo um orçamento `SENT` ou `ACCEPTED` por pedido.
- Após `SENT`, o conteúdo é imutável; alterar = nova versão (anterior → `SUPERSEDED`).

**quote_item** — `id`, `quote_id`, `position`, `name`, `description`, `quantity NUMERIC(10,2) > 0` (padrão 1), `unit_price >= 0`, `subtotal` (= quantidade × unitário, calculado no backend, conferido por `CHECK`).

### Pagamentos
**payment** — `id`, `order_id`, `quote_id` (orçamento vigente no registro), `kind` (`DEPOSIT`, `BALANCE`, `REFUND` — pendente), `method` (`PIX`, `CASH`), `amount > 0`, `status` (`PENDING_REVIEW`, `CONFIRMED`, `REJECTED`), `proof_file_id` FK nullable, `submitted_by` (`CLIENT`, `OWNER`), `submitted_at`, `reviewed_at`, `review_note`, `created_at`.
Nunca apagado; correções são feitas por novo registro ou rejeição com motivo.

### Arquivos
**stored_file** — `id`, `storage_key` (UUID, único), `order_id` FK, `purpose` (`ORDER_REFERENCE`, `TOPPER_REFERENCE`, `PAYMENT_PROOF`), `original_name` (saneado), `content_type` (detectado), `size_bytes`, `sha256`, `uploaded_by`, `created_at`, `deleted_at`.

### Financeiro
**expense_category** — `id`, `name` (único), `active`.
**expense** — `id`, `description`, `amount > 0`, `expense_date`, `category_id` FK, `order_id` FK nullable, `created_at`, `updated_at`, `deleted_at` (exclusão lógica com registro em `audit_log`).
Uma despesa é um único registro: o vínculo com pedido é apenas um atributo — por isso nunca é somada duas vezes.

### Avisos e auditoria
**notification** — `id`, `type` (`QUOTE_EXPIRING`, `QUOTE_EXPIRED`, `QUOTE_ACCEPTED`, `PROOF_SUBMITTED`, `NEW_ORDER`, …), `order_id`, `quote_id`, `message`, `dedup_key` (**único** — garante idempotência), `created_at`, `read_at`, `email_status`, `email_sent_at`, `email_attempts`.

**audit_log** — `id`, `actor`, `action`, `entity_type`, `entity_id`, `details JSONB`, `created_at`.

**shedlock** — tabela padrão do ShedLock.

## 3. Índices principais

- `cake_order (commercial_status, pickup_date)`, `cake_order (pickup_date, pickup_time)`, `cake_order (tracking_code)` único.
- `quote (status, valid_until)` — busca de vencimentos.
- `payment (order_id)`, `payment (status)`.
- `expense (expense_date)`, `order_history (order_id, created_at)`.

## 4. Migrations planejadas

| Versão | Conteúdo |
|---|---|
| V1 | admin_user, password_reset_token, spring_session, audit_log, shedlock |
| V2 | bakery_settings (linha inicial vazia de dados de negócio), catálogo |
| V3 | agenda: weekly_slot, schedule_block, slot_occupancy, slot_reservation |
| V4 | pedidos: cake_order, order_flavor, order_filling, cake_topper, order_history, stored_file |
| V5 | quote, quote_item |
| V6 | payment |
| V7 | expense_category, expense |
| V8 | notification |

Migrations são criadas na fase de cada módulo (não todas de uma vez) e nunca editadas depois de aplicadas. Nenhum dado fictício é inserido por migration; dados de demonstração existirão apenas em perfil `dev`.
