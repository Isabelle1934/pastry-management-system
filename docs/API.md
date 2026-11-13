# API REST

- Base: `/api`. JSON em UTF-8. Datas `YYYY-MM-DD`, horários `HH:mm`, instantes ISO-8601 com fuso.
- Valores monetários como **string decimal** (`"165.00"`) para evitar erros de ponto flutuante no JavaScript.
- Erros: `application/problem+json` (RFC 9457). Exemplo:
  ```json
  {
    "type": "about:blank", "title": "Bad Request", "status": 400,
    "detail": "Alguns campos não estão corretos. Verifique e tente novamente.",
    "code": "VALIDATION_ERROR",
    "errors": [{ "field": "name", "message": "Informe o nome." }],
    "requestId": "3f0c…"
  }
  ```
  Códigos genéricos: `VALIDATION_ERROR` (400), `MALFORMED_REQUEST` (400), `NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `CONFLICT` / `CONCURRENT_MODIFICATION` (409), `PAYLOAD_TOO_LARGE` (413), `UNSUPPORTED_MEDIA_TYPE` (415), `BUSINESS_RULE` ou código específico da regra (422), `INTERNAL_ERROR` (500). Erros 500 nunca expõem mensagem interna.
- Toda resposta traz o header `X-Request-Id` (também no corpo dos erros) para localizar o registro no log.
- Requisições que alteram estado exigem header `X-XSRF-TOKEN`.
- Paginação: `?page=0&size=20&sort=pickupDate,asc`.

## Públicos — `/api/public`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health` | ✅ Fase 1 — `{"status":"UP"}` (Actuator, sem detalhes) |
| GET | `/csrf` | ✅ Fase 3 — 204; emite o cookie `XSRF-TOKEN` (o frontend o envia de volta no header `X-XSRF-TOKEN`) |
| GET | `/bakery` | ✅ Fase 4 — `{name, aboutText, whatsappContact, whatsappLink, instagram, address, pickupInstructions, pickupEnabled, deliveryEnabled, deliveryInfo, customCakeEnabled, customCakeDelivery, deliveryFee, deliveryFeeSweets, partyVenueFee, punctualityNotice}`. `deliveryInfo` e as taxas só vêm com o delivery ativo; `partyVenueFee` só depois de confirmada pela proprietária. Não expõe chave Pix, e-mail de avisos nem regras de orçamento |
| GET | `/catalog` | ✅ Fase 4 / catálogo — opções ativas do bolo personalizado: `cakeCategories[{id, name, description, notice, consultOnly, maxFlavors, maxFillings}]` (tipos de bolo ativos), `sizes[{id, name, description, servings, basePrice, maxFlavors, maxFillings, categoryId, weightKg, diameterCm}]` (só tamanhos ativos em tipo ativo ou sem tipo), `flavors[{id, name, description}]`, `fillings[...]`; produtos visíveis (ativos ou temporariamente indisponíveis, em categoria ativa e dentro do período): `categories[{id, name, description}]` (só as que têm produto), `products[{id, categoryId, name, description, price (null = sob orçamento), unitLabel, minQuantity, available, orderable, seasonal, availableFrom, availableUntil, availableDays[1–7], pickupAllowed, deliveryAllowed, photoIds[]}]`; `customCakeEnabled` |
| GET | `/products/{productId}/photos/{fileId}` | ✅ Foto de produto visível no catálogo (404 para produto inativo ou foto de outro produto). Cache público de 7 dias — o conteúdo de um id nunca muda |
| GET | `/availability?from=&to=` | ✅ Fase 5 — `{from, to, days:[{date, times:["14:00"]}]}`: só horários que podem ser escolhidos agora (sem contagens). Sem parâmetros: de hoje até 62 dias ou o limite configurado |
| POST | `/orders` | ✅ Fase 6 — multipart: parte `order` (JSON: `customerName, customerWhatsapp, pickupDate, pickupTime, customCake (padrão true), sizeId, flavorIds[], fillingIds[], decoration, cakeText, notes, topperRequested, topperTheme, topperName, topperAge, items[{productId, quantity, notes?}], fulfillmentType (PICKUP/DELIVERY, padrão PICKUP), deliveryAddress, deliveryDistrict, deliveryReference, cakeCategoryId, deliveryZone (CITY/PARTY_VENUE/OUTSIDE_CITY, padrão CITY com delivery), customerEmail?`). Tamanho/massa/recheio só são exigidos com `customCake`; sem tamanho, só com `cakeCategoryId` de um tipo "consulte valores" e com `decoration` preenchida; com delivery a cliente informa também o bairro; envio igual (mesma cliente, data/horário e itens) em até 30 min → 409 `DUPLICATE_ORDER` (`app.orders.duplicate-window`); sem bolo é preciso ao menos 1 item; topo só com bolo; com `DELIVERY` o endereço é obrigatório. O mesmo corpo (sem nome/WhatsApp) é usado em `PUT /api/tracking/order`; até 3 `referencePhotos` e 1 `topperPhoto` (JPG/PNG/WEBP, 5 MB cada). 201 `{trackingCode: "XXXX-XXXX-XXXX-XXXX"}`. Erros por campo (400), 413 acima do limite do servidor, 429 após 5 envios/h por IP. Não ocupa vaga na agenda |

## Acompanhamento — `/api/tracking`

| Método | Rota | Descrição |
|---|---|---|
| POST | `/verify` | ✅ `{trackingCode, whatsappLast4}` → visão do pedido e sessão autorizada só para ele por 2 h. Código aceita minúsculas, espaços/hífens e O/I/L. 401 `TRACKING_NOT_FOUND` (mesma resposta para código inexistente ou dígitos errados); 429 após 5 falhas por código ou 20 tentativas por IP em 15 min |
| GET | `/order` | ✅ Visão da cliente (WhatsApp mascarado, orçamento vigente, situações). 401 `TRACKING_REQUIRED` sem verificação ou após 2 h |
| PUT | `/order` | ✅ Edição (agendamento, personalização, topo; sem fotos) com `version`. Só em `PENDING_REVIEW`/`QUOTE_SENT` (409 `ORDER_NOT_EDITABLE`); se havia orçamento enviado, ele vira `SUPERSEDED` e o pedido volta a `PENDING_REVIEW` |
| POST | `/order/quotes/{quoteId}/accept` | ✅ Aceita a versão vigente → pedido `AWAITING_DEPOSIT` (não ocupa vaga). 409 `QUOTE_EXPIRED` após a validade |
| POST | `/order/quotes/{quoteId}/decline` | ✅ `{reason?}` → pedido `DECLINED_BY_CLIENT` |
| POST | `/order/payments/deposit-proof` | ✅ Multipart `proof` (JPG/PNG/PDF, conteúdo real validado, até 5 MB), `amount?` (padrão: valor do sinal) e `method?` (`PIX` padrão ou `CARD_LINK`; `CASH` → 400). Só em `AWAITING_DEPOSIT` e sem outro comprovante em análise (409 `PROOF_ALREADY_SUBMITTED`); cria pagamento `PENDING_REVIEW` e aviso à proprietária |
| GET | `/order/files/{fileId}` | ✅ Download de comprovantes do próprio pedido (`no-store`); 404 para arquivos de outro pedido ou fotos internas |
| POST | `/logout` | ✅ Encerra o acesso ao pedido |

## Administrativos — `/api/admin` (autenticação obrigatória)

### Autenticação
| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/login` | ✅ `{email, password}` → 200 `{id, name, email, mustChangePassword}` + cookie `SESSION`. Erros: 401 `INVALID_CREDENTIALS` (mesma resposta para e-mail inexistente, senha errada ou conta desativada), 429 `TOO_MANY_ATTEMPTS` |
| POST | `/auth/logout` | ✅ 204; encerra a sessão |
| GET | `/auth/me` | ✅ Usuária logada; 401 `UNAUTHENTICATED` sem sessão |
| POST | `/auth/password/forgot` | ✅ Público. `{email}` → 202 com mensagem sempre igual. 429 se o IP exceder o limite |
| POST | `/auth/password/reset` | ✅ Público. `{token, newPassword}` → 204. 400 `INVALID_RESET_TOKEN` ou `VALIDATION_ERROR` (`newPassword`) |
| POST | `/auth/password/change` | ✅ `{currentPassword, newPassword}` → 204. Mantém a sessão atual e encerra as demais |

Enquanto a senha for provisória, qualquer `/api/admin/**` fora de `/api/admin/auth/**` responde 403 `PASSWORD_CHANGE_REQUIRED`. Falha de CSRF: 403 `CSRF_INVALID`.

### Configurações e catálogo
| Método | Rota | Descrição |
|---|---|---|
| GET/PUT | `/settings` | ✅ Configurações. PUT exige `version`; `deliveryFee` (taxa dentro da cidade, padrão 20.00), `cardPaymentLink` (só `https://`; `""` remove) e `depositCutoffHours` (0–720, padrão 24; 0 = sem limite), `deliveryFeeSweets` (padrão 5.00), `partyVenueFee` (padrão 30.00) + `partyVenueFeeConfirmed`, `punctualityNotice` (obrigatório), `depositReminderHours` (1–720, padrão 12) — vazios mantêm o valor atual; `deliveryFee` passa a ser a taxa de entrega de bolos; WhatsApp normalizado (E.164 no banco, exibido como `(11) 99999-8888`); Instagram aceita `@usuario` ou link; `quoteExpiryWarningHours < quoteValidityHours` |
| GET/POST | `/catalog/cake-categories` · PUT `/catalog/cake-categories/{id}` · PATCH `/catalog/cake-categories/{id}/active` | ✅ Tipos de bolo `{name, description?, notice?, consultOnly?, maxFlavors?, maxFillings?, displayOrder?, version?}`; resposta com `sizeCount`. Tipo com tamanhos não vira "consulte valores" (400 `consultOnly`) |
| GET/POST | `/catalog/sizes` · PUT `/catalog/sizes/{id}` · PATCH `/catalog/sizes/{id}/active` | ✅ Tamanhos (+ `categoryId?`, `weightKg?`, `diameterCm?`; tipo "consulte valores" não aceita tamanhos) |
| GET/POST | `/catalog/flavors` · PUT `/catalog/flavors/{id}` · PATCH `/catalog/flavors/{id}/active` | ✅ Massas |
| GET/POST | `/catalog/fillings` · PUT `/catalog/fillings/{id}` · PATCH `/catalog/fillings/{id}/active` | ✅ Recheios |

| GET/POST | `/catalog/categories` · PUT `/catalog/categories/{id}` · PATCH `/catalog/categories/{id}/active` | ✅ Categorias de produtos `{name, description?, displayOrder?, version?}`; resposta inclui `productCount`. Desativar tira a categoria e seus produtos do site |
| GET/POST | `/catalog/products` · GET/PUT `/catalog/products/{id}` | ✅ Produtos `{categoryId, name, description?, price? (vazio = sob orçamento), unitLabel?, minQuantity?, orderable?, seasonal?, availableFrom?, availableUntil?, availableDays?[1–7], pickupAllowed?, deliveryAllowed?, displayOrder?, version?}`. Resposta inclui `status`, `categoryName` e `photoIds[]` |
| PATCH | `/catalog/products/{id}/status` | ✅ `{status: ACTIVE/UNAVAILABLE/INACTIVE, version}` — nada é apagado |
| POST | `/catalog/products/{id}/photos` | ✅ Multipart `photo` (JPG/PNG/WEBP, 5 MB, conteúdo verificado); até 6 fotos; a primeira é a capa |
| DELETE · GET | `/catalog/products/{id}/photos/{fileId}` | ✅ Remove a foto (exclusão lógica) · baixa a foto (também de produtos inativos) |

Regras do catálogo: nome único por lista sem diferenciar maiúsculas (409 `DUPLICATE_NAME`); edição e ativação exigem `version` (409 `CONCURRENT_MODIFICATION` se o item mudou em outra tela); itens não são excluídos, só desativados (`{active, version}`).

### Agenda
| Método | Rota | Descrição |
|---|---|---|
| GET/PUT | `/schedule/rules` | ✅ `{minLeadTimeHours, maxBookingDaysAhead, version}` |
| GET/POST | `/schedule/slots` · PUT/DELETE `/schedule/slots/{id}` | ✅ Modelo semanal `{dayOfWeek (1=seg…7=dom), startTime "HH:mm", capacity?}` (vazio = sem limite de pedidos confirmados); PUT `{capacity?, active, version}`. 409 `DUPLICATE_SLOT` |
| GET/POST | `/schedule/blocks` · DELETE `/schedule/blocks/{id}` | ✅ `{date, startTime \| null (dia inteiro), reason}`. Resposta traz `confirmedOrders` (pedidos confirmados no período, que não são cancelados). 409 `DUPLICATE_BLOCK` |
| GET | `/schedule/calendar?from=&to=` | ✅ Até 62 dias: por dia, `blockedAllDay` e `slots[{time, capacity, reserved, blocked, tooSoon, beyondLimit, bookable}]`. A contagem de solicitações sem sinal entra na Fase 6 |

### Pedidos
| Método | Rota | Descrição |
|---|---|---|
| GET | `/orders?status=&q=&from=&to=&page=&size=` | ✅ Paginada (`{content, page, size, totalElements, totalPages}`); busca por nome, código (com/sem hífens) ou dígitos do WhatsApp |
| GET | `/orders/counts` | ✅ Quantidade por situação comercial |
| GET | `/orders/{id}` | ✅ Detalhe: dados, escolhas, topo, situações, `slotReserved`, arquivos, todas as versões de orçamento e histórico; `items[].subtotal`, `customerEmail`, `deliveryZone`, `cakeCategoryId/Name`, `sizePrice` (preço da tabela copiado no pedido) e `suggestedDeliveryFee` (taxa da modalidade; vazio = informar manualmente) |
| PUT | `/orders/{id}` | ✅ Edição pela proprietária (corpo do formulário + `version`); horário precisa existir na agenda; se o pedido ocupa vaga, ela é remarcada (409 `SLOT_FULL` se lotado). 409 `ORDER_CLOSED` em pedidos encerrados |
| POST | `/orders/{id}/reject` | ✅ `{reason, version}` — só antes do aceite; orçamento pendente vira `SUPERSEDED` |
| POST | `/orders/{id}/cancel` | ✅ `{reason, version}` — libera a vaga; pagamentos não são apagados |
| POST | `/orders/{id}/production` | ✅ `{status, version, note?, confirmBalanceDue?}` — um passo por vez (`NOT_STARTED → IN_PRODUCTION → READY → DELIVERED`), podendo voltar um passo; só com pedido `CONFIRMED` (409 `ORDER_NOT_CONFIRMED`); salto de passo = 409 `INVALID_PRODUCTION_TRANSITION`. `DELIVERED` conclui o pedido (`COMPLETED`); com saldo em aberto exige `confirmBalanceDue: true` (409 `BALANCE_DUE`). Devolve o detalhe do pedido |
| GET | `/orders/{id}/files/{fileId}` | ✅ Download com tipo detectado, `no-store`, imagem `inline` e PDF `attachment`; 404 se o arquivo não for do pedido |

### Orçamentos
| Método | Rota | Descrição |
|---|---|---|
| PUT | `/orders/{id}/quotes/draft` | ✅ Cria/atualiza o rascunho `{items:[{kind?, name, description, quantity, unitPrice}], notes, validityHours?}` — `kind`: `PRODUCT` (padrão), `EXTRA`, `TOPPER` ou `DELIVERY`; subtotais, total, sinal (50%, HALF_UP) e saldo calculados no servidor. As respostas de orçamento trazem `breakdown {products, extras, topper, delivery}` |
| DELETE | `/orders/{id}/quotes/draft` | ✅ Descarta o rascunho |
| POST | `/orders/{id}/quotes/send` | ✅ Envia: nova versão `SENT` com `validUntil`; a vigente anterior vira `SUPERSEDED`; pedido → `QUOTE_SENT`. 409 `QUOTE_EMPTY` sem itens/valor |
| GET | `/orders/{id}/quotes` | ✅ Todas as versões (mais recente primeiro) |

### Pagamentos
| Método | Rota | Descrição |
|---|---|---|
| GET | `/orders/{id}/payments` | ✅ `{total, depositAmount, received, balanceDue, status, proofUnderReview, depositDueAt, payments[]}` (o mesmo resumo vem em `GET /orders/{id}` → `payments`). Valores em string decimal |
| POST | `/orders/{id}/payments` | ✅ `{kind: DEPOSIT\|BALANCE\|REFUND, method: PIX\|CASH\|CARD_LINK, amount, notes?, acceptAsDeposit?}` — registrado já `CONFIRMED`. Se o recebido atingir o sinal (ou `acceptAsDeposit`), o pedido em `AWAITING_DEPOSIT` vai a `CONFIRMED` e reserva a vaga (409 `SLOT_FULL` se lotado). Reembolso não pode passar do recebido |
| POST | `/payments/{paymentId}/confirm` | ✅ `{note?, acceptAsDeposit?}` — confirma comprovante em análise; mesma regra de confirmação do pedido |
| POST | `/payments/{paymentId}/reject` | ✅ `{reason}` — rejeita comprovante; a cliente pode enviar outro |

### Produção
| Método | Rota | Descrição |
|---|---|---|
| GET | `/production?days=` | ✅ Pedidos `CONFIRMED` com retirada até hoje + `days` (padrão 14, máx. 60), incluindo atrasados (`overdue`), ordenados por data/horário, com `total` e `balanceDue` |

### Financeiro, dashboard e avisos

Datas `from`/`to` no formato `AAAA-MM-DD`, no fuso da confeitaria (America/Sao_Paulo).
| Método | Rota | Descrição |
|---|---|---|
| GET | `/finance/summary?from=&to=` | ✅ Padrão: mês atual; até 366 dias (400 fora disso). `forecastRevenue`/`forecastOrders` (orçamentos aceitos de pedidos `CONFIRMED`/`COMPLETED`, por data de retirada), `awaitingDepositRevenue`/`awaitingDepositOrders` (à parte), `received` = `receivedDeposits` + `receivedBalances` − `refunds` (pagamentos confirmados, por data de confirmação), `pending` (a receber: total − recebido dos confirmados/concluídos do período), `profit` (lucro = recebido líquido no período; sem cadastro de custos — P18). Os endpoints de despesas e categorias de despesas foram retirados a pedido da proprietária (404); as tabelas antigas continuam no banco |
| GET | `/dashboard` | ✅ `{today, actions {newOrders, proofsToReview, awaitingClientResponse, awaitingDeposit, overduePickups}, production {notStarted, inProduction, ready, pickupsToday}, upcomingPickups[] (até 8, próximos 7 dias), expiringQuotes[] (dentro do aviso configurado), month {forecastRevenue, received, pending, profit}, monthOrders {total, pending, confirmed} (por data agendada no mês; total sem recusados/cancelados), unreadNotifications}` — mesmas regras do financeiro e da produção |
| GET | `/customers?q=&page=&size=` | ✅ Clientes derivadas dos pedidos (cada WhatsApp é uma cliente): `{name (do pedido mais recente), whatsapp, whatsappDigits, whatsappLink, orders, confirmedOrders, completedOrders, totalPaid (confirmados − reembolsos), firstOrderAt, lastOrderAt, lastPickupDate}`; busca por nome ou dígitos do WhatsApp |
| GET | `/reports?from=&to=` | ✅ Padrão: mês atual; até 366 dias. Por data agendada: `statusCounts`, `totals {orders, sold, lost, soldRevenue, averageTicket, received}`, `topProducts[]`, `categories[]`, `cakeSizes[]`, `flavors[]`, `fillings[]` (`{name, group, quantity, orders}`, só pedidos confirmados/concluídos, nomes da cópia guardada no pedido), `customCakes`, `toppers`, `fulfillment {pickup, delivery}` |
| GET | `/notifications?unread=&limit=` | ✅ Avisos mais recentes (padrão 30, máx. 100) |
| GET | `/notifications/unread-count` | ✅ `{count}` |
| POST | `/notifications/{id}/read` · `/notifications/read-all` | ✅ Marca como lido |

Erros seguem o formato ProblemDetail com `code` e `requestId` (ver início deste documento).
