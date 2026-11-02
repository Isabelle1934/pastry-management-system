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

---

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
| P9 | **Lucro estimado** | Previsto − despesas, mostrando também recebido − despesas | Fase 10 |
| P10 | **Decoração** | ✅ Texto livre + foto (proposta padrão) | — |
| P11 | **Somente retirada** (sem entrega) | ✅ Sim (proposta padrão) | — |
| P12 | **Exibir preço-base** dos tamanhos na área pública | Exibir como "a partir de" | Fase 6 |
| P13 | **Quantidade de fotos** de referência | ✅ Até 3 do bolo + 1 do topo, 5 MB cada (proposta padrão) | — |
| P14 | **Retenção de dados pessoais** (LGPD) | Manter enquanto necessário; definir política de exclusão/anonimização | Fase 12 |
