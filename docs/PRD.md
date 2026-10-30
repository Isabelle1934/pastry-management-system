# PRD — Sistema de Gestão e Encomendas para Confeitaria

## 1. Problema

Informações de clientes, pedidos, datas, horários e pagamentos ficam espalhadas em conversas de WhatsApp. Isso gera risco de esquecer pedidos, aceitar mais encomendas do que a produção comporta e perder o controle do que foi pago.

## 2. Objetivo

Centralizar todo o ciclo da encomenda — solicitação, orçamento, sinal, produção, retirada e financeiro — em uma plataforma web simples, segura e utilizável no celular e no computador.

## 3. Usuárias

| Perfil | Descrição | Acesso |
|---|---|---|
| Cliente | Pessoa que encomenda o bolo. Não cria conta. | Área pública + página de acompanhamento (código + verificação do WhatsApp) |
| Proprietária | Única usuária administrativa inicialmente. Pode não ter conhecimento técnico. | Painel administrativo com login |
| Sistema | Processos agendados (vencimento de orçamentos, avisos) | Interno |

## 4. Escopo funcional

### 4.1 Área pública
- Página inicial: logo, nome, breve apresentação, fotos de produtos, informações relevantes, botão de encomenda.
- Formulário em 5 etapas: (1) nome e WhatsApp; (2) data e horário disponível; (3) tamanho, massa(s), recheio(s), decoração, nome/frase, observações, foto de referência opcional; (4) topo de bolo opcional — tema, nome, idade, foto opcional; (5) revisão com possibilidade de voltar e corrigir.
- Ao enviar: código único de acompanhamento.
- Página de acompanhamento: dados do pedido, data/horário, personalizações, orçamento (aceitar/recusar), situação dos pagamentos, envio de comprovante do sinal, andamento da produção.
- Edição da solicitação pela cliente enquanto o orçamento não tiver sido aceito.

### 4.2 Área administrativa
- **Dashboard:** pedidos novos, pendentes, próximas retiradas, em produção, prontos, orçamentos próximos do vencimento, recebimentos, valores pendentes.
- **Pedidos:** listar, filtrar, visualizar, editar, aprovar (criar orçamento), recusar, cancelar, atualizar andamento, histórico.
- **Agenda:** dias de funcionamento, horários, limite de pedidos por horário, bloqueio de datas e de horários, antecedência mínima.
- **Produtos:** tamanhos (com preço-base e quantidade de massas/recheios permitidos), massas, recheios.
- **Orçamentos:** itens (nome, descrição, quantidade, valor unitário, subtotal), total automático, versões preservadas, validade configurável.
- **Pagamentos:** sinal de 50% + saldo de 50% na retirada; Pix e dinheiro; conferência manual de comprovantes; histórico preservado.
- **Produção:** em produção → pronto → entregue.
- **Financeiro:** faturamento previsto, recebimentos efetivos, pendências, despesas gerais e vinculadas a pedidos, lucro estimado.
- **Configurações:** dados da confeitaria, validade de orçamento, antecedência mínima, chave Pix, avisos.
- **Autenticação:** login, logout, recuperação de senha por e-mail.

## 5. Requisitos não funcionais

- Responsividade com prioridade igual para celular e computador.
- Respeito à identidade visual existente (off-white, marrom, dourado, nude; logo, fontes e cores oficiais fornecidas pela proprietária). **Nenhuma identidade nova será criada.**
- Segurança: senhas com hash, sessão segura, CSRF, CORS restrito, limitação de tentativas, uploads validados por conteúdo, comprovantes protegidos, dados pessoais não expostos em logs.
- Consistência: capacidade da agenda garantida no banco sob concorrência; históricos imutáveis de orçamento, pagamento e pedido.
- Confiabilidade: vencimento de orçamentos e avisos executados no backend, mesmo com o painel fechado.
- Backups do banco e dos arquivos.
- Fuso horário de referência: `America/Sao_Paulo`. Moeda: BRL.

## 6. Fora de escopo

- Integração automática com bancos, Pix automático ou gateways de pagamento.
- Contabilidade fiscal, emissão de nota fiscal.
- Contas de cliente, login de cliente.
- Múltiplos usuários administrativos e perfis de permissão (o modelo não impede adicionar depois).
- Entrega em domicílio (o fluxo assume **retirada**).
- Envio automático de mensagens pelo WhatsApp.
- Preços fixos para decoração e topo (sempre definidos no orçamento).
- Catálogo/loja com carrinho.

## 7. Critérios de sucesso

- Toda encomenda nasce no sistema com código de acompanhamento.
- Nenhum horário recebe mais pedidos confirmados que o limite configurado, mesmo com confirmações simultâneas.
- A proprietária sabe, em uma tela, o que precisa fazer hoje e quanto há a receber.
- Nenhuma alteração apaga orçamento ou pagamento anterior.

## 8. Itens pendentes de definição

Ver seção **Pendências** em [DECISIONS.md](DECISIONS.md).
