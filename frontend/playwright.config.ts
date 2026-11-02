import { defineConfig } from '@playwright/test'

/**
 * Teste de ponta a ponta contra a pilha de produção rodando localmente
 * (infra/docker-compose.prod.yml). Veja docs/DEPLOYMENT.md, seção "Teste E2E".
 * Usa o Microsoft Edge instalado no computador (nenhum navegador é baixado).
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '*.e2e.ts',
  timeout: 120_000,
  expect: { timeout: 10_000 },
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'e2e/playwright-report', open: 'never' }]],
  outputDir: 'e2e/test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8088',
    channel: process.env.E2E_BROWSER_CHANNEL ?? 'msedge',
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
