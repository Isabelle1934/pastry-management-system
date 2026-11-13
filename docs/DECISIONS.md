# Registro de Decisões

Formato: contexto → decisão → consequências. Status: **Aceita**, **Proposta** (aguardando confirmação) ou **Substituída**.

## ADR-001 — Monólito modular com frontend separado no mesmo domínio
**Status:** Proposta · 29/10/2026
Uma única usuária administrativa e baixo volume não justificam microsserviços. Uma API Spring Boot organizada por módulos + SPA React, servidas no mesmo domínio via Caddy. Simplifica sessão, CSRF e CORS; um único deploy.

## ADR-002 — Pacote por módulo com camadas internas
**Status:** Proposta
`modules/<modulo>/{controller,service,repository,entity,dto}`. Atende à separação por responsabilidade pedida e mantém cada funcionalidade localizada.

## ADR-003 — Sessão no servidor (cookie HttpOnly) em vez de JWT
**Status:** Proposta
JWT exigiria guardar token no navegador (localStorage é vulnerável a XSS) ou montar refresh/revogação manualmente. Sessão com Spring Session JDBC permite logout real, revogação de todas as sessões após troca de senha e não expõe token ao JavaScript. Consequência: CSRF obrigatório (`CookieCsrfTokenRepository`), e frontend e API no mesmo domínio.

## ADR-004 — Capacidade da agenda por contador com UPDATE condicional
**Status:** Aceita (implementada na Fase 5; teste com 20 confirmações simultâneas para 3 vagas → exatamente 3)
Alternativas: `SERIALIZABLE` (exige retentativas), *advisory locks* (mais opaco), contar reservas com `SELECT FOR UPDATE` (não trava linhas inexistentes). Escolha: tabela `slot_occupancy` com `UPDATE ... WHERE reserved_count < capacidade`, mais `slot_reservation` única por pedido. Atômico, simples de testar.

## ADR-005 — Processo agendado com @Scheduled + ShedLock
**Status:** Proposta
Vencimentos e avisos rodam no backend a cada 5 min. ShedLock evita execução duplicada se houver mais de uma instância; as atualizações são condicionais (`WHERE status = 'SENT'`) e os avisos têm `dedup_key` único → idempotente.

## ADR-006 — Arquivos em disco persistente privado, validados por conteúdo
**Status:** Aceita (revisada na Fase 6: sem Apache Tika)
Disco local (volume) fora da área pública, nomes UUID, detecção do tipo real pela assinatura binária dos 4 formatos aceitos (JPEG, PNG, WEBP, PDF) — o Tika seria uma dependência grande para um conjunto tão pequeno, download apenas pelo backend com autorização. Interface `FileStorage` permite trocar por armazenamento de objetos no futuro.

## ADR-007 — Código de acompanhamento + 4 últimos dígitos do WhatsApp
**Status:** Aceita (Fase 7: acesso por sessão restrita ao pedido por 2 h; limites por IP e por código)
Código aleatório de ~80 bits (Crockford Base32) e segunda verificação pelos 4 últimos dígitos do WhatsApp, com limite de tentativas. Protege os dados caso o código seja compartilhado ou vazado em um print.

## ADR-008 — Dinheiro em NUMERIC/BigDecimal e strings na API
**Status:** Proposta
Evita erros de ponto flutuante. Sinal = 50% arredondado em centavos (HALF_UP); saldo = total − sinal.

## ADR-009 — Estados como VARCHAR + CHECK e quatro dimensões separadas
**Status:** Proposta
Comercial, orçamento, pagamento e produção em campos/entidades distintos, conforme requisito.

## ADR-010 — Maven Wrapper e versões fixadas
**Status:** Proposta
Maven não está instalado; o wrapper garante a mesma versão para qualquer ambiente. Versões exatas de Spring Boot, React, Vite etc. serão confirmadas (estáveis, com suporte) e fixadas na Fase 1.

## ADR-011 — Versões fixadas na Fase 1
**Status:** Aceita · 30/10/2026
Spring Boot **4.1.2** (versão estável padrão do Spring Initializr; 4.2 ainda em RC), Java 25, PostgreSQL 18, React 19.3, Vite 8.3, TypeScript 7.0 e oxlint — estes três últimos vêm do template oficial `create-vite` (react-ts). Pacote base `br.com.confeitaria` (nome da marca pendente — P1; renomear depois é mecânico).

## ADR-012 — Health check pelo Actuator em /api/public/health
**Status:** Aceita
O Actuator foi mapeado para `/api/public` expondo **apenas** `health`, sem detalhes (`show-details: never`). Evita um controller próprio e não expõe informações internas; demais endpoints do Actuator respondem 404.

## ADR-013 — CORS por filtro com lista vinda de variável de ambiente
**Status:** Aceita
`CorsFilter` + `CorsConfigurationSource` (em vez de `WebMvcConfigurer`) para cobrir também o Actuator e ser reaproveitado pelo Spring Security na Fase 3. Lista vazia (produção, mesma origem) = nenhuma origem externa permitida. Testes cobrem origem permitida e origem desconhecida (403).

## ADR-014 — Sem perfil padrão; configuração local por .env opcional
**Status:** Aceita
O backend não ativa `dev` automaticamente: sem perfil e sem `DB_URL` ele não sobe (falha rápida, evita produção com padrões de desenvolvimento). O perfil `dev` importa `../.env` se existir e tem padrões que batem com o `docker-compose.dev.yml`. Porta do PostgreSQL local publicada só em `127.0.0.1`.

## ADR-015 — Histórico imutável garantido pelo banco
**Status:** Aceita · 30/10/2026
Além de a aplicação não oferecer update/delete (repositório só com `save` e consultas, entidade `@Immutable`), um trigger no PostgreSQL rejeita UPDATE/DELETE em `audit_log`. Mesmo um acesso direto ao banco não apaga histórico por engano. A mesma função será aplicada a `order_history`.

## ADR-016 — Erros padronizados e sem vazamento de detalhes
**Status:** Aceita
Todas as exceções (de domínio, validação e do próprio Spring MVC) viram ProblemDetail com `code` estável e texto em português. Erros inesperados retornam mensagem genérica e são registrados no log com `requestId`. Violações de integridade não repassam a mensagem do banco (pode conter dados informados pelo usuário). `X-Request-Id` vindo do proxy só é aceito se for alfanumérico (evita injeção de linhas no log).

## ADR-017 — Testes de integração com contexto compartilhado
**Status:** Aceita
A anotação `@IntegrationTest` (`@SpringBootTest` + Testcontainers) faz todas as classes de integração reutilizarem o mesmo contexto e o mesmo container PostgreSQL 18, mantendo a suíte rápida. Os testes não usam `@Transactional` automático para exercitar commits reais (bloqueio otimista, triggers, rollback).

## ADR-018 — Limite de tentativas em memória
**Status:** Aceita · 02/11/2026
`FixedWindowCounter` (janela fixa, `ConcurrentHashMap`) sem dependência externa. Suficiente para uma única instância; os contadores zeram ao reiniciar a aplicação (aceitável). Se houver mais de uma instância no futuro, trocar por armazenamento compartilhado (tabela no PostgreSQL ou Bucket4j com JDBC).

## ADR-019 — Cookie de sessão configurado explicitamente
**Status:** Aceita
No Spring Boot 4 + Spring Session, as propriedades `server.servlet.session.cookie.*` não foram aplicadas ao cookie `SESSION` (verificado nos testes). Por isso `SecurityConfig#cookieSerializer` define `HttpOnly`, `SameSite=Lax`, `Path=/` e `Secure` (por `APP_COOKIE_SECURE`). Um teste com Tomcat real (`SessionCookieHttpIntegrationTests`) confere os headers `Set-Cookie` exatos.

## ADR-020 — Política de senha por comprimento
**Status:** Aceita
Mínimo 10 e máximo 64 caracteres (≤ 72 bytes, limite do BCrypt), diferente do e-mail, sem exigir símbolos — recomendação do NIST e mais simples para uma usuária não técnica. BCrypt custo 12 via `DelegatingPasswordEncoder` (`{bcrypt}`), permitindo migrar de algoritmo sem invalidar senhas.

## ADR-021 — Ambiente de desenvolvimento com Mailpit e `.env` local
**Status:** Aceita
O `docker-compose.dev.yml` inclui o Mailpit (SMTP em `localhost:1025`, interface em `http://localhost:8025`): e-mails de desenvolvimento nunca são entregues de verdade. O `.env` local (fora do git) guarda a conta de teste.

## ADR-022 — Controle de edição concorrente por `version` no corpo da requisição
**Status:** Aceita · 02/11/2026
Além do `@Version` do JPA (que protege o instante do commit), cada edição envia a versão que a tela carregou. Se o registro mudou depois (outra aba, outro aparelho), a API responde 409 `CONCURRENT_MODIFICATION` e a tela recarrega os dados — nenhuma alteração é sobrescrita em silêncio.

## ADR-023 — Itens de catálogo são desativados, nunca excluídos
**Status:** Aceita
Tamanhos, massas e recheios serão referenciados por pedidos (Fase 6). Excluir quebraria o histórico; desativar remove do formulário público e mantém os dados. Os pedidos também guardarão o nome do item no momento da encomenda (snapshot).

## ADR-024 — Auditoria das configurações sem valores sensíveis
**Status:** Aceita
Mudanças de chave Pix, titular e e-mail de avisos são registradas como "alterado", sem o valor. Os demais campos guardam antes/depois.

## ADR-025 — Datas e horários locais sem conversão de fuso
**Status:** Aceita · 02/11/2026
Retirada é `DATE` + `TIME` no fuso da confeitaria. A opção `hibernate.jdbc.time_zone=UTC` foi **removida**: ela fazia o Hibernate deslocar `LocalTime` em 3 horas (o teste de concorrência revelou o problema — "14:00" não era encontrado). Instantes (`TIMESTAMPTZ`/`Instant`) continuam absolutos. "Hoje" é sempre `LocalDate.ofInstant(clock.instant(), America/Sao_Paulo)`, no backend e no frontend (`utils/date.ts`), evitando a troca de dia perto da meia-noite.

## ADR-026 — Reserva de vaga como serviço transacional reutilizável
**Status:** Aceita
`ReservationService.reserve/release` exigem a transação de quem chama (`MANDATORY`): a confirmação do sinal (Fase 8), o cancelamento e a remarcação ocupam/liberam a vaga atomicamente com a mudança do pedido. Remarcar libera a vaga antiga e ocupa a nova na mesma transação; se a nova estiver lotada, nada muda.

## ADR-027 — Topo de bolo como colunas do pedido
**Status:** Aceita · 02/11/2026
O topo é 0..1 por pedido e só tem tema, nome e idade (a foto vai para `stored_file`). Colunas `topper_*` em `cake_order` com `CHECK` de consistência evitam uma tabela 1:1 e um join em toda leitura.

## ADR-028 — Fotos enviadas mantêm os metadados originais (EXIF)
**Status:** Aceita, com ressalva
Fotos de celular podem conter localização (EXIF/GPS). Remover exigiria recodificar a imagem, e o Java não lê WEBP nativamente. Como as fotos só podem ser vistas pela proprietária (download autenticado, Fase 7), o risco é baixo. Reavaliar na revisão de segurança (Fase 12).

## ADR-029 — Regras de pedido em um único componente
**Status:** Aceita · 02/11/2026
`OrderDetailsApplier` valida e aplica agendamento, personalização e topo na criação pela cliente, na edição pela cliente e na edição pela proprietária. Diferença intencional: a cliente só escolhe horários disponíveis; a proprietária pode usar qualquer horário ativo da agenda (a capacidade é conferida na confirmação do sinal).

## ADR-030 — Transições de situação centralizadas na entidade
**Status:** Aceita
`CakeOrder` declara o mapa de transições permitidas da situação comercial e recusa as demais (409 `INVALID_STATUS_TRANSITION`). Mudanças de situação travam a linha do pedido (`SELECT … FOR UPDATE`) para que aceite, recusa e cancelamento simultâneos não se sobreponham.

## ADR-031 — Orçamento enviado imutável no banco
**Status:** Aceita
Além das regras na entidade, triggers no PostgreSQL impedem alterar itens/valores ou excluir um orçamento depois de enviado. Índices únicos parciais garantem no máximo um orçamento vigente e um rascunho por pedido.

## ADR-032 — Pagamentos, prazos e avisos (Fase 8)
**Status:** Aceita · 02/11/2026
Pagamentos (sinal, saldo, reembolso) nunca são apagados nem alterados após análise (triggers). Confirmar o sinal confirma o pedido e ocupa a vaga na mesma transação (SLOT_FULL desfaz tudo). Processo agendado a cada 5 min com ShedLock 7.10.1: aviso e vencimento de orçamentos, prazo do sinal (P4: padrão 48 h; com comprovante em análise ou valor recebido, só avisa), reenvio de e-mails. Avisos idempotentes por `dedup_key`. P7: reembolso registrado manualmente. Jackson 3 recusa `boolean` ausente no JSON — campos opcionais usam `Boolean`.

## ADR-033 — Produção e lista de retiradas (Fase 9)
**Status:** Aceita · 02/11/2026
Transições de produção centralizadas em `CakeOrder.changeProduction` (um passo à frente ou um atrás; só com pedido `CONFIRMED`), com o pedido travado (`FOR UPDATE`) e `version`. Entregar conclui o pedido e mantém a vaga ocupada. Sem migração: `production_status` já existia (V4); datas de cada passo ficam no histórico imutável. A lista de retiradas calcula o saldo por pedido (uma consulta por pedido, aceitável no volume de uma confeitaria; período limitado a 60 dias).

## ADR-034 — Financeiro (Fase 10)
**Status:** Aceita · 02/11/2026
Resumo calculado em SQL (`JdbcTemplate`) a cada consulta, sem tabelas de totais — evita divergência com pagamentos e orçamentos. Previsto e pendente por data de retirada; recebido por `reviewed_at` convertido para o fuso da confeitaria; despesas por `expense_date`. Despesa vinculada é a mesma linha (`order_id` opcional), nunca somada duas vezes. Exclusão de despesa lógica (`deleted_at`, trigger bloqueia DELETE) e auditada. V7 cria 4 categorias genéricas editáveis (Ingredientes, Embalagens, Equipamentos e utensílios, Outros) para o formulário já funcionar no primeiro uso. Correção: "hoje" passa a usar o fuso configurado (`AppProperties.timezone`) também na lista de retiradas — o `Clock` é UTC.

## ADR-035 — Dashboard e acabamento (Fase 11)
**Status:** Aceita · 02/11/2026
O dashboard reaproveita as regras já testadas (lista de retiradas e resumo financeiro) em vez de recalcular por conta própria; as contagens vêm de uma consulta agregada (`count(*) filter`). Menu do painel recolhível abaixo de 900 px (botão "Menu" com `aria-expanded`), link "Pular para o conteúdo" nas áreas pública e administrativa. Mensagens para a proprietária (avisos, e-mails, histórico) formatam valores e datas em pt-BR via `Texts`; a API continua com formato técnico. Identidade visual (P1) continua pendente: nada foi inventado; os tokens neutros seguem provisórios.

## ADR-036 — Implantação, backups e E2E (Fase 12)
**Status:** Aceita · 02/11/2026
Um servidor com Docker Compose: PostgreSQL e API sem portas publicadas; Caddy serve o frontend compilado, faz HTTPS automático e é o único caminho até a API — por isso `forward-headers-strategy: framework` é seguro (o Caddy substitui `X-Forwarded-For`; verificado). Backup diário (`pg_dump` custom + tar dos uploads) com retenção configurável e restauração por serviço separado (`profiles: restore`, exige `--confirm`), testada de ponta a ponta. E2E com Playwright 1.64.1 usando o Edge instalado (`channel: msedge`), sem baixar navegadores, contra a pilha de produção local. Revisão de segurança em `docs/SECURITY.md`; passo a passo em `docs/DEPLOYMENT.md`.

---

## ADR-037 — Requisitos da proprietária: delivery, cartão, prazo do sinal e financeiro enxuto

- **Despesas retiradas do painel** (pedido da proprietária): entidades, endpoints e telas removidos; as tabelas `expense`/`expense_category` ficam no banco, sem uso, para não perder dados. Hibernate valida só as entidades mapeadas, então não é preciso migração para isso.
- **Prazo do sinal** = o menor entre "X h após o aceite" e "Y h antes da data agendada" (`DepositDeadlines`), com piso de 2 h quando o aceite acontece já depois do limite pela data — um prazo vencido no aceite cancelaria o pedido sem chance de pagar. Calculado na hora (sem coluna nova), então mudar a configuração vale também para pedidos já aceitos.
- **Cartão por link**: só armazena o link informado pela proprietária e o método `CARD_LINK` no pagamento; sem integração com operadora.
- **Tipo do item do orçamento** (`quote_item.kind`) em vez de tabelas separadas: os subtotais são somas por tipo e o total continua sendo a soma dos itens (CHECK do banco inalterado).
- **Massas confirmadas** inseridas pela migração apenas se não existirem (nome sem diferenciar maiúsculas) — exceção à regra de não inserir dados por migração, por serem dados reais confirmados pela proprietária.

## ADR-038 — Tabela de bolos, modalidades de entrega e lembretes (V10)

- **Tipos de bolo como tabela própria** (`cake_category`) em vez de prefixo no nome do tamanho: a cliente escolhe o tipo e depois o tamanho; características e avisos ("Topo não incluso") ficam no tipo. "3 andares ou mais" é um tipo `consult_only`, sem tamanhos — o pedido guarda a categoria e não tem preço de tabela.
- **Cópia do preço da tabela no pedido** (`size_price_snapshot`) para preservar valores históricos e sugerir o preço inicial do orçamento. Pedidos antigos ficam sem a cópia (o valor deles está no orçamento).
- **Taxa por modalidade calculada na hora** (`DeliveryFees`), sem gravar no pedido: a taxa entra no orçamento, que é a fonte do valor. A do local da festa só é sugerida depois de confirmada.
- **Limite por horário opcional:** `capacity` vazio = sem limite. O contador de confirmados continua existindo (agenda do painel).
- **Lembrete à cliente:** sem integração com a API do WhatsApp (exige conta Business e credenciais), o lembrete automático vai por e-mail (campo opcional) e a proprietária recebe um botão de WhatsApp com a mensagem pronta. Decisão confirmada pela usuária.
- **Envio repetido:** recusa por conteúdo igual em 30 min, sem revelar o código do outro pedido. Janela configurável (`app.orders.duplicate-window`; zero desliga — usado nos testes).

## Pendências — decisões que dependem da proprietária

| # | Assunto | Proposta padrão | Necessário até |
|---|---|---|---|
| P1 | **Identidade visual:** logo (SVG/PNG), fontes oficiais, códigos exatos das cores, fotos dos produtos | — (não será inventada). Área pública pronta com visual neutro provisório; nome e apresentação já vêm de Configurações | Antes da implantação (Fase 11) |
| P2 | **Hospedagem** | VPS de baixo custo com Docker Compose | Fase 12 |
| P3 | **E-mail** (recuperação de senha e avisos): provedor SMTP e domínio/remetente | Provedor SMTP com plano gratuito; Mailpit no desenvolvimento (✅ configurado) | Fase 12 (produção) |
| P4 | **Prazo para pagamento do sinal** | ✅ Configurável (padrão 48 h; em branco = sem prazo) | — |
| P5 | **Edição da cliente com orçamento já enviado** | ✅ Orçamento invalidado e pedido volta para análise (proposta padrão) | — |
| P6 | **Limite de massas/recheios** | ✅ Implementado por tamanho (proposta padrão) — pode ser revisto | — |
| P7 | **Cancelamento com sinal já pago** | ✅ Vaga liberada; reembolso registrado manualmente | — |
| P8 | **Entregar com saldo em aberto** | ✅ Permitido, com aviso visível e confirmação explícita (proposta padrão) | — |
| P9 | **Lucro estimado** | Substituída por P18: módulo de despesas retirado | — |
| P10 | **Decoração** | ✅ Texto livre + foto (proposta padrão) | — |
| P11 | **Somente retirada** (sem entrega) | Substituída por P15: delivery configurável (desligado por padrão) | — |
| P12 | **Exibir preço-base** dos tamanhos na área pública | Exibir como "a partir de" | Fase 6 |
| P13 | **Quantidade de fotos** de referência | ✅ Até 3 do bolo + 1 do topo, 5 MB cada (proposta padrão) | — |
| P14 | **Retenção de dados pessoais** (LGPD) | Manter enquanto necessário; definir política de exclusão/anonimização. Agora inclui endereços de entrega e a lista de clientes | Fase 12 |
| P15 | **Delivery:** bairros/área atendida, valor fora da cidade, se bolos personalizados podem ser entregues, confirmação da taxa no local da festa | ✅ Bolos na cidade R$ 20,00; doces na cidade R$ 5,00; local da festa R$ 30,00 aguardando confirmação ("Confirmo a taxa" nas Configurações); fora da cidade combinado no orçamento. A opção "Bolos personalizados também podem ser entregues" continua sendo da proprietária (desligada por padrão) | Confirmar a taxa do local da festa e ligar a entrega de bolos |
| P16 | **Catálogo inicial:** categorias, produtos, preços, quantidades mínimas, dias e fotos reais | — (não será inventado). A proprietária cadastra pelo painel; sem produtos, o site funciona como antes (só bolo personalizado) | Antes de divulgar o cardápio |
| P17 | **Pagamento de produtos com preço fixo** | Mantido o fluxo atual: todo pedido passa por orçamento e sinal de 50%, mesmo só com itens de preço fixo | Se quiser venda direta sem orçamento |
| P18 | **Cálculo do lucro** sem despesas cadastradas | Lucro = recebido líquido no período (igual ao recebido). Se quiser descontar custos, definir como (percentual fixo, custo por produto etc.) | Quando quiser um lucro diferente do recebido |
| P19 | **Sabores de recheio** do bolo personalizado | ✅ Tamanhos e preços cadastrados conforme as imagens enviadas; massas Baunilha, Chocolate e Red Velvet. Os nomes dos recheios não foram informados — a proprietária cadastra no painel (não serão inventados) | Antes de divulgar o formulário |
| P20 | **Limite de produtos por dia ou por quantidade** | Estrutura pronta (dias da semana, período, quantidade mínima); limite diário de unidades não implementado até a regra ser confirmada | Se quiser limitar produção diária |
