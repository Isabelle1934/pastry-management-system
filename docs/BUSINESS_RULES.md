# Regras de Negócio

Itens marcados com **[PENDENTE]** dependem de confirmação da proprietária (ver [DECISIONS.md](DECISIONS.md)). Até lá, vale o comportamento padrão proposto.

## 1. Fluxo principal

1. Cliente envia solicitação → `commercial_status = PENDING_REVIEW`.
2. Proprietária analisa; pode editar, recusar (`REJECTED`) ou criar orçamento.
3. Orçamento enviado → pedido `QUOTE_SENT`, orçamento `SENT` com `valid_until`.
4. Cliente aceita → orçamento `ACCEPTED`, pedido `AWAITING_DEPOSIT`. Cliente recusa → orçamento `DECLINED`, pedido `DECLINED_BY_CLIENT`.
5. Cliente envia comprovante do sinal → pagamento `PENDING_REVIEW`; pedido segue `AWAITING_DEPOSIT`, `payment_status = DEPOSIT_UNDER_REVIEW`.
6. Proprietária confere e confirma → **na mesma transação:** pagamento `CONFIRMED`, reserva de capacidade, pedido `CONFIRMED`, `payment_status = DEPOSIT_PAID`.
7. Produção: `NOT_STARTED → IN_PRODUCTION → READY → DELIVERED`.
8. Na retirada, a proprietária registra o saldo (Pix ou dinheiro) → `payment_status = PAID`.
9. Marcado como entregue → pedido `COMPLETED`.

## 2. Estados separados

### Situação comercial (`cake_order.commercial_status`)
| Estado | Significado |
|---|---|
| `PENDING_REVIEW` | Solicitação nova, aguardando análise |
| `QUOTE_SENT` | Orçamento enviado, aguardando resposta da cliente |
| `AWAITING_DEPOSIT` | Orçamento aceito, aguardando sinal confirmado |
| `CONFIRMED` | Sinal confirmado; capacidade ocupada |
| `COMPLETED` | Entregue |
| `REJECTED` | Recusado pela proprietária |
| `DECLINED_BY_CLIENT` | Cliente recusou o orçamento |
| `CANCELLED` | Cancelado (proprietária, cliente ou sistema — ver `cancelled_by`/`cancellation_reason`) |

Transições permitidas são validadas no serviço; qualquer outra retorna erro `INVALID_STATUS_TRANSITION`.

### Situação do orçamento (`quote.status`)
`DRAFT → SENT → ACCEPTED | DECLINED | EXPIRED | SUPERSEDED`. `DRAFT` pode ser descartado.

### Situação dos pagamentos (`cake_order.payment_status`, derivada)
| Estado | Regra |
|---|---|
| `NONE` | Nenhum pagamento confirmado nem em análise |
| `DEPOSIT_UNDER_REVIEW` | Existe comprovante de sinal em análise |
| `DEPOSIT_PAID` | Soma confirmada ≥ valor do sinal e < total |
| `PAID` | Soma confirmada ≥ total do orçamento aceito |

Cada pagamento individual tem seu próprio status: `PENDING_REVIEW`, `CONFIRMED`, `REJECTED`.

### Situação da produção (`cake_order.production_status`)
`NOT_STARTED → IN_PRODUCTION → READY → DELIVERED`. Só pode sair de `NOT_STARTED` com pedido `CONFIRMED`. `DELIVERED` exige `READY`. **[PENDENTE]** entregar sem saldo quitado: padrão = permitido com aviso visível de saldo em aberto.

## 3. Agenda e disponibilidade (✅ implementada na Fase 5)

- Horários oferecidos = modelo semanal ativo do dia − bloqueios (dia inteiro ou horário) − horários antes da antecedência mínima − horários além do limite máximo de dias (se configurado) − horários com `reserved_count >= capacity`.
- **Uma solicitação não reserva capacidade.** Vários pedidos podem solicitar o mesmo horário; o painel mostra quantas solicitações existem por horário.
- A capacidade só é ocupada na **confirmação do sinal**.
- Bloquear data/horário não cancela pedidos já confirmados; o painel avisa sobre conflitos.
- Reduzir a capacidade abaixo do já reservado é permitido, mas não remove reservas existentes (o horário apenas deixa de aceitar novas confirmações).
- O horário escolhido é validado no backend no envio da solicitação (`AvailabilityService.isBookable`) e novamente na confirmação (`ReservationService.reserve`).
- Na confirmação **não** se aplica a antecedência mínima (ela vale para a cliente escolher o horário); aplicam-se horário existente/ativo, bloqueios e limite.
- Valores padrão editáveis: antecedência mínima de 24 h e agendamento até 90 dias à frente.

### Controle de concorrência na confirmação do sinal
Em uma única transação:
1. `SELECT ... FOR UPDATE` no pedido (impede confirmar o mesmo pedido duas vezes).
2. Lê a capacidade vigente do horário (modelo semanal) e verifica bloqueios.
3. Garante a linha do contador: `INSERT INTO slot_occupancy (...) VALUES (..., 0) ON CONFLICT DO NOTHING`.
4. Incremento condicional e atômico:
   `UPDATE slot_occupancy SET reserved_count = reserved_count + 1 WHERE slot_date = ? AND slot_time = ? AND reserved_count < ?`
   — se nenhuma linha for afetada, o horário está lotado: a transação é desfeita e a proprietária recebe o erro `SLOT_FULL` (com opção de remarcar o pedido para outro horário).
5. Insere `slot_reservation` (único por pedido), confirma pagamento, atualiza estados e histórico.

O `UPDATE` condicional trava a linha do horário: duas confirmações simultâneas são serializadas pelo PostgreSQL e a segunda reavalia `reserved_count < capacidade` com o valor já atualizado. Teste de concorrência com várias threads é obrigatório (Fase 5/12).

### Liberação de capacidade
Cancelamento, remarcação ou expiração de pedido com reserva ativa: decrementa `reserved_count` e marca a reserva como `RELEASED` com motivo, na mesma transação. Remarcação = liberar o horário antigo + reservar o novo (mesma transação, mesmas regras).

## 3.1 Solicitação pública (✅ Fase 6)

- Todas as regras são revalidadas no servidor: WhatsApp válido, tamanho ativo, massas/recheios ativos, sem repetição e dentro do limite do tamanho, horário ainda disponível, tema obrigatório quando há topo.
- Decoração é texto livre + fotos (P10); apenas retirada (P11); até 3 fotos de referência e 1 do topo, 5 MB cada (P13).
- Código de acompanhamento: 16 caracteres Crockford Base32 (80 bits) — `TrackingCodes`.
- Limite de 5 solicitações por hora por IP.
- A solicitação **não** ocupa vaga; o horário só é garantido na confirmação do sinal.

## 4. Edição pela cliente

- Permitida em `PENDING_REVIEW` e `QUOTE_SENT` (antes do aceite).
- **[PENDENTE]** Padrão proposto: se houver orçamento `SENT` e a cliente editar, o orçamento passa a `SUPERSEDED` e o pedido volta a `PENDING_REVIEW` (a proprietária precisa reavaliar).
- Após o aceite, somente a proprietária edita; toda edição gera `order_history` com antes/depois.

## 5. Orçamentos

- Itens: nome, descrição, quantidade (padrão 1), valor unitário; `subtotal = quantidade × unitário`; `total = Σ subtotais`. Cálculos sempre no backend, arredondamento `HALF_UP` em centavos.
- `sinal = total × 50%` arredondado para centavos; `saldo = total − sinal` (a soma fecha exatamente o total).
- Preço-base do tamanho é apenas referência para montar o orçamento. Decoração e topo não têm preço fixo.
- Orçamento enviado é imutável. Nova proposta = nova versão; a anterior vira `SUPERSEDED`. Nada é apagado.
- Só existe um orçamento `SENT`/`ACCEPTED` por pedido por vez (garantido por índice único parcial).
- Aceite exige orçamento `SENT` e `now() < valid_until` (verificado no backend no momento do aceite).
- **[PENDENTE]** Alteração de valores após aceite (ex.: cliente pediu mudança): padrão = proprietária cria nova versão que a cliente precisa aceitar novamente; pagamentos já registrados permanecem vinculados ao pedido e são abatidos do novo total.

## 6. Pagamentos

- Regra: 50% de sinal, 50% na retirada. Métodos: Pix e dinheiro. Sem integração bancária.
- Cliente pode enviar comprovante do sinal (JPG, PNG, PDF) somente com pedido em `AWAITING_DEPOSIT`.
- Proprietária pode registrar pagamento diretamente (ex.: sinal em dinheiro), já como `CONFIRMED`.
- Confirmação de sinal com valor menor que o sinal: **[PENDENTE]** padrão = permitido apenas se a proprietária marcar explicitamente "aceitar como sinal".
- Resumo exibido: total, sinal, recebido (Σ confirmados), saldo restante (total − recebido), situação.
- Pagamentos nunca são apagados; editar um orçamento não altera pagamentos.

## 7. Vencimento de orçamentos

- Validade padrão configurável (`quote_validity_hours`); pode ser ajustada por orçamento ao enviar.
- Antecedência do aviso configurável (`quote_expiry_warning_hours`).
- **Processo agendado no backend** (a cada 5 min, com ShedLock), idempotente:
  1. **Aviso:** orçamentos `SENT` com `valid_until − aviso ≤ now() < valid_until` → cria `notification` com `dedup_key = QUOTE_EXPIRING:<quoteId>` (índice único — nunca duplica) e envia e-mail para a proprietária. Falha de e-mail é registrada e reenviada nas execuções seguintes (com limite de tentativas).
  2. **Vencimento:** orçamentos `SENT` com `valid_until ≤ now()` → em transação por orçamento, com `UPDATE ... WHERE status = 'SENT'` (se outra execução já processou, nada acontece): orçamento `EXPIRED`, pedido `CANCELLED` com `cancelled_by = SYSTEM`, motivo "Orçamento vencido sem resposta" e horário registrados; histórico e aviso gerados.
  3. Capacidade: se não há sinal confirmado, não há reserva — nada a liberar; o horário volta a aparecer como disponível naturalmente.
- **[PENDENTE]** Prazo para pagamento do sinal após o aceite (pedido em `AWAITING_DEPOSIT` sem sinal confirmado): padrão proposto = prazo configurável (`deposit_deadline_hours`), com aviso e cancelamento automático pelas mesmas regras, **exceto** se houver comprovante em análise (nesse caso só avisa a proprietária).
- O processo usa o relógio do servidor em UTC; exibição em `America/Sao_Paulo`.

## 8. Acompanhamento pela cliente

- Código: 16 caracteres do alfabeto Crockford Base32 gerados com `SecureRandom` (~80 bits), exibidos como `XXXX-XXXX-XXXX-XXXX`.
- Para ver os dados, a cliente informa o código **e** os 4 últimos dígitos do WhatsApp cadastrado. Tentativas limitadas por IP e por código (ex.: 5 falhas → bloqueio de 15 min). Mensagem de erro genérica.
- Após verificar, a sessão do navegador fica autorizada somente para aquele pedido (expira em poucas horas).
- A página mostra apenas o necessário: o WhatsApp aparece mascarado.

## 9. Financeiro

- **Faturamento previsto:** soma dos totais dos orçamentos aceitos de pedidos `CONFIRMED`/`COMPLETED` (e, separadamente, `AWAITING_DEPOSIT`), pela data de retirada no período.
- **Recebimentos efetivos:** soma dos pagamentos `CONFIRMED`, pela data de confirmação.
- **Valores pendentes:** total − recebido dos pedidos ativos.
- **Despesas:** gerais e vinculadas a pedidos; cada despesa é um registro único e entra uma única vez nos totais.
- **Lucro estimado:** **[PENDENTE]** padrão = faturamento previsto − despesas do período; exibido também o resultado de caixa (recebido − despesas). Rotulado sempre como "estimado".
- Sem contabilidade fiscal.

## 10. Catálogo (Fase 4)

- Cada tamanho define quantas massas (`maxFlavors`) e quantos recheios (`maxFillings`) a cliente pode escolher, de 1 a 10 (pendência P6 resolvida com a proposta padrão).
- Preço-base é referência ("a partir de"); o valor final vem sempre do orçamento.
- Itens não são excluídos, apenas desativados: somem do formulário público e permanecem nos pedidos antigos.

## 11. Validações principais

- Nome: 2–120 caracteres. WhatsApp: número brasileiro válido (DDD + 8/9 dígitos), normalizado.
- Data/horário: existente na disponibilidade no momento do envio.
- Massas/recheios: ativos e dentro dos limites do tamanho.
- Textos livres com limite de tamanho; conteúdo tratado como texto (sem HTML).
- Fotos: até 3 de referência do bolo + 1 do topo **[PENDENTE: quantidade]**.
