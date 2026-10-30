# API REST (planejada)

- Base: `/api`. JSON em UTF-8. Datas `YYYY-MM-DD`, horários `HH:mm`, instantes ISO-8601 com fuso.
- Valores monetários como **string decimal** (`"165.00"`) para evitar erros de ponto flutuante no JavaScript.
- Erros: `application/problem+json` com `code` estável, `title`, `detail` em português e `errors[]` por campo em falhas de validação.
- Requisições que alteram estado exigem header `X-XSRF-TOKEN`.
- Paginação: `?page=0&size=20&sort=pickupDate,asc`.

## Públicos — `/api/public`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health` | ✅ Fase 1 — `{"status":"UP"}` (Actuator, sem detalhes) |
| GET | `/csrf` | Inicializa o cookie CSRF |
| GET | `/bakery` | Dados públicos da confeitaria (nome, apresentação, contatos) |
| GET | `/catalog` | Tamanhos (com limites de massas/recheios), massas e recheios ativos — sem preços internos se assim definido |
| GET | `/availability?from=&to=` | Datas e horários disponíveis (considera funcionamento, bloqueios, antecedência e ocupação) |
| POST | `/orders` | Cria solicitação (multipart: JSON `order` + `referencePhotos[]` + `topperPhoto`). Retorna `trackingCode` |

## Acompanhamento — `/api/tracking`

| Método | Rota | Descrição |
|---|---|---|
| POST | `/verify` | `{ trackingCode, whatsappLast4 }` → abre sessão de acompanhamento restrita ao pedido |
| GET | `/order` | Dados do pedido da sessão verificada |
| PUT | `/order` | Edição pela cliente (somente antes do aceite do orçamento) |
| POST | `/order/quote/{version}/accept` | Aceita o orçamento vigente |
| POST | `/order/quote/{version}/decline` | Recusa o orçamento (motivo opcional) |
| POST | `/order/payments/deposit-proof` | Envia comprovante do sinal (multipart) |
| GET | `/order/files/{fileId}` | Baixa arquivo do próprio pedido |
| POST | `/logout` | Encerra a sessão de acompanhamento |

## Administrativos — `/api/admin` (autenticação obrigatória)

### Autenticação
| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/login` | Login (e-mail + senha) |
| POST | `/auth/logout` | Logout |
| GET | `/auth/me` | Usuária logada |
| POST | `/auth/password/forgot` | Solicita e-mail de redefinição (público, resposta sempre igual) |
| POST | `/auth/password/reset` | Redefine com token (público) |
| POST | `/auth/password/change` | Troca de senha logada |

### Configurações e catálogo
| Método | Rota | Descrição |
|---|---|---|
| GET/PUT | `/settings` | Configurações da confeitaria |
| GET/POST | `/catalog/sizes` · PUT/PATCH `/catalog/sizes/{id}` | Tamanhos (PATCH para ativar/desativar) |
| GET/POST | `/catalog/flavors` · PUT/PATCH `/catalog/flavors/{id}` | Massas |
| GET/POST | `/catalog/fillings` · PUT/PATCH `/catalog/fillings/{id}` | Recheios |

### Agenda
| Método | Rota | Descrição |
|---|---|---|
| GET/PUT | `/schedule/weekly` | Modelo semanal (dias, horários, capacidades) |
| GET/POST | `/schedule/blocks` · DELETE `/schedule/blocks/{id}` | Bloqueios de data ou horário |
| GET | `/schedule/calendar?from=&to=` | Visão de ocupação: capacidade, confirmados, solicitações por horário |

### Pedidos
| Método | Rota | Descrição |
|---|---|---|
| GET | `/orders?status=&from=&to=&q=` | Lista com filtros |
| GET | `/orders/{id}` | Detalhe completo (personalização, fotos, orçamentos, pagamentos, estados) |
| PUT | `/orders/{id}` | Edição pela proprietária |
| POST | `/orders/{id}/reject` | Recusa a solicitação (motivo) |
| POST | `/orders/{id}/cancel` | Cancela (motivo) |
| POST | `/orders/{id}/production` | `{ status }` — em produção / pronto / entregue |
| GET | `/orders/{id}/history` | Histórico |
| GET | `/orders/{id}/files/{fileId}` | Arquivo do pedido |

### Orçamentos
| Método | Rota | Descrição |
|---|---|---|
| POST | `/orders/{id}/quotes` | Cria rascunho (itens, validade, observações) |
| PUT | `/orders/{id}/quotes/{quoteId}` | Edita rascunho |
| POST | `/orders/{id}/quotes/{quoteId}/send` | Envia à cliente (torna imutável; anterior vira SUPERSEDED) |
| GET | `/orders/{id}/quotes` | Todas as versões |

### Pagamentos
| Método | Rota | Descrição |
|---|---|---|
| GET | `/orders/{id}/payments` | Histórico e resumo (total, sinal, recebido, saldo) |
| POST | `/orders/{id}/payments` | Registra pagamento pela proprietária (ex.: dinheiro, saldo na retirada) |
| POST | `/payments/{paymentId}/confirm` | Confirma — no caso do sinal, confirma o pedido e reserva a capacidade |
| POST | `/payments/{paymentId}/reject` | Rejeita comprovante (motivo) |

### Financeiro, dashboard e avisos
| Método | Rota | Descrição |
|---|---|---|
| GET | `/finance/summary?from=&to=` | Faturamento previsto, recebido, pendente, despesas, lucro estimado |
| GET/POST | `/finance/expenses` · PUT/DELETE `/finance/expenses/{id}` | Despesas (exclusão lógica) |
| GET/POST | `/finance/categories` · PUT `/finance/categories/{id}` | Categorias |
| GET | `/dashboard` | Indicadores gerais |
| GET | `/notifications?unread=true` · POST `/notifications/{id}/read` | Avisos |

Este documento será atualizado com os contratos exatos (DTOs) conforme cada fase for implementada.
