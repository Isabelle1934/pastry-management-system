# Revisão de segurança (Fase 12 — 02/11/2026)

Resultado da revisão antes da implantação. "Verificado" = conferido com teste automatizado ou na pilha de
produção rodando localmente (`infra/docker-compose.prod.yml`).

## Controles e como foram verificados

| Área | Controle | Verificação |
|---|---|---|
| Autenticação | Sessão no servidor (Spring Session JDBC), cookie `SESSION` HttpOnly, SameSite=Lax, Secure em produção; BCrypt custo 12; senha provisória obriga troca; limite de tentativas de login | Testes de integração (Fase 3); E2E troca a senha provisória no primeiro acesso |
| CSRF | Cookie `XSRF-TOKEN` + cabeçalho `X-XSRF-TOKEN` em toda requisição que altera dados | Testes de integração (403 `CSRF_INVALID`) |
| Autorização | Só `/api/admin/**` exige login; acompanhamento da cliente limitado ao próprio pedido (código + 4 dígitos, sessão de 2 h) | Testes de integração; 401 sem sessão conferido na pilha local |
| Limites por IP | Login, recuperação de senha, encomenda e acompanhamento | **Verificado na pilha local:** 25 consultas com `X-Forwarded-For` falso diferente a cada vez → 429 a partir da 21ª. O Caddy descarta o cabeçalho vindo de fora e a API não tem porta publicada |
| Uploads | Extensão e conteúdo real (assinatura) validados, até 5 MB, nome aleatório, fora de pasta pública, download só autenticado com `no-store` e `nosniff` | Testes de integração; PDF falso recusado (400) na verificação da Fase 8 |
| Cabeçalhos HTTP | Site: CSP `default-src 'self'` (sem scripts/estilos externos ou inline), HSTS, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, sem cabeçalho `Server`. API: CSP `default-src 'none'` | Conferido com `curl` na pilha local |
| Cache | `index.html` com `no-cache`; arquivos com hash por 1 ano; API com `no-store` | Conferido com `curl` |
| Erros | Sem stack trace nem mensagem interna na resposta; `requestId` para correlacionar com o log | Configuração `server.error.*` + `GlobalExceptionHandler` |
| Logs | Sem senhas, tokens, e-mails ou WhatsApp; token de redefinição de senha mascarado no log do Caddy | Revisão de todos os `log.*` do backend; filtro no `Caddyfile` |
| Segredos | Somente variáveis de ambiente; `.env`/`.env.prod` fora do Git; `.env.example` e `.env.prod.example` sem credenciais reais; produção não sobe sem as variáveis obrigatórias | `.gitignore`; `${VAR:?}` no Compose |
| Banco | Sem porta publicada em produção; histórico, pagamentos e despesas protegidos contra exclusão por triggers | Compose; testes de integração |
| Containers | API roda com usuário sem privilégios; imagens de versões fixas | `backend/Dockerfile` |
| Dependências | `npm audit --omit=dev`: 0 vulnerabilidades | Executado em 02/11/2026 |
| Actuator | Só `/api/public/health`, sem detalhes | Conferido com `curl` |

## Riscos aceitos / pontos de atenção

- **Limites em memória:** valem para uma única instância da API (o caso deste projeto). Reiniciar a API zera
  os contadores.
- **Metadados de fotos (EXIF/GPS):** não são removidos. As fotos só podem ser vistas pela proprietária
  (download autenticado). Remover exigiria recodificar as imagens; reavaliar se o acesso mudar.
- **Dependências Java:** sem verificação automática de vulnerabilidades (OWASP Dependency-Check exige base
  local grande). Recomenda-se atualizar o Spring Boot nas versões de correção e rodar
  `./mvnw versions:display-dependency-updates` periodicamente.
- **Backups:** ficam no servidor até serem copiados para fora; a cópia externa é responsabilidade da operação
  (ver `docs/DEPLOYMENT.md`).
- **Retenção de dados pessoais (LGPD, P14):** ainda sem política definida pela proprietária; nada é apagado
  automaticamente.
