import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'

/*
 * Ciclo completo de uma encomenda, como a cliente e a proprietária fazem no navegador:
 * encomenda → orçamento → aceite → comprovante do sinal → confirmação → produção → saldo → entrega.
 *
 * Variáveis de ambiente (valores de TESTE da pilha local, nunca de produção):
 *   E2E_ADMIN_EMAIL, E2E_ADMIN_PASSWORD (senha provisória da conta inicial),
 *   E2E_ADMIN_NEW_PASSWORD (senha definida no primeiro acesso; usada nas execuções seguintes).
 */

const ADMIN_EMAIL = required('E2E_ADMIN_EMAIL')
const ADMIN_INITIAL_PASSWORD = required('E2E_ADMIN_PASSWORD')
const ADMIN_PASSWORD = required('E2E_ADMIN_NEW_PASSWORD')
const RUN = Date.now().toString(36).toUpperCase()
const CUSTOMER = `Cliente E2E ${RUN}`
const WHATSAPP = '(11) 98765-4321'
const PDF = Buffer.from('%PDF-1.7\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n', 'ascii')

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`Defina ${name} (veja docs/DEPLOYMENT.md, seção "Teste E2E").`)
  return value
}

/** Chamadas diretas à API com a sessão do navegador (usadas só para preparar catálogo e agenda). */
async function api(context: BrowserContext, method: 'GET' | 'POST' | 'PUT', path: string, data?: unknown) {
  const request: APIRequestContext = context.request
  await request.get('/api/public/csrf')
  const token = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await request.fetch(`/api${path}`, {
    method,
    data,
    headers: method === 'GET' ? {} : { 'X-XSRF-TOKEN': token },
  })
  return response
}

/** Opções do formulário são cartões clicáveis (o rádio/checkbox fica sob o texto), como a cliente usa. */
async function choose(page: Page, text: string) {
  const option = page.locator('label', { hasText: text })
  await option.click()
  await expect(option.locator('input')).toBeChecked()
}

async function loginAsOwner(page: Page) {
  await page.goto('/admin/login')
  await page.getByLabel('E-mail').fill(ADMIN_EMAIL)
  await page.getByLabel('Senha', { exact: true }).fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Entrar' }).click()
  const greeting = page.getByRole('heading', { name: /^Olá/ })
  await expect(greeting.or(page.getByRole('alert'))).toBeVisible()
  if (!(await greeting.isVisible())) {
    // Primeira execução: a conta ainda usa a senha provisória e precisa trocá-la.
    await page.getByLabel('Senha', { exact: true }).fill(ADMIN_INITIAL_PASSWORD)
    await page.getByRole('button', { name: 'Entrar' }).click()
    await expect(page).toHaveURL(/trocar-senha/)
    await page.getByLabel('Senha atual').fill(ADMIN_INITIAL_PASSWORD)
    await page.getByLabel('Nova senha', { exact: true }).fill(ADMIN_PASSWORD)
    await page.getByLabel('Repita a nova senha').fill(ADMIN_PASSWORD)
    await page.getByRole('button', { name: 'Salvar nova senha' }).click()
  }
  await expect(page.getByRole('heading', { name: /^Olá/ })).toBeVisible()
}

/** Catálogo com nomes únicos desta execução e horários das 10:00 em todos os dias da semana. */
async function prepareCatalogAndSchedule(context: BrowserContext) {
  const created = await Promise.all([
    api(context, 'POST', '/admin/catalog/sizes', {
      name: `Médio ${RUN}`, servings: 20, basePrice: '150.00', maxFlavors: 2, maxFillings: 1, displayOrder: 0,
    }),
    api(context, 'POST', '/admin/catalog/flavors', { name: `Chocolate ${RUN}`, displayOrder: 0 }),
    api(context, 'POST', '/admin/catalog/fillings', { name: `Brigadeiro ${RUN}`, displayOrder: 0 }),
  ])
  for (const response of created) expect(response.status(), await response.text()).toBe(201)

  for (let day = 1; day <= 7; day++) {
    const response = await api(context, 'POST', '/admin/schedule/slots', { dayOfWeek: day, startTime: '10:00', capacity: 50 })
    expect([201, 409], await response.text()).toContain(response.status())
  }
}

test('encomenda percorre todo o ciclo até a entrega', async ({ browser }) => {
  const owner = await browser.newContext()
  const client = await browser.newContext()
  const ownerPage = await owner.newPage()
  const clientPage = await client.newPage()

  await test.step('proprietária entra e prepara catálogo e agenda', async () => {
    await loginAsOwner(ownerPage)
    await prepareCatalogAndSchedule(owner)
  })

  let trackingCode = ''
  await test.step('cliente faz a encomenda de bolo personalizado', async () => {
    await clientPage.goto('/encomendar?bolo=1')
    await clientPage.getByLabel('Nome completo').fill(CUSTOMER)
    await clientPage.getByLabel('WhatsApp (com DDD)').fill(WHATSAPP)
    await clientPage.getByRole('button', { name: 'Continuar' }).click()

    // Com produtos no cardápio aparece a etapa "O que você deseja?" (o bolo personalizado já vem marcado).
    const itemsStep = clientPage.getByRole('heading', { name: 'O que você deseja?' })
    const cakeStep = clientPage.getByRole('heading', { name: 'Seu bolo' })
    await expect(itemsStep.or(cakeStep)).toBeVisible()
    if (await itemsStep.isVisible()) await clientPage.getByRole('button', { name: 'Continuar' }).click()

    // Tamanho sem tipo de bolo: aparece em "Outros tamanhos" quando há tipos cadastrados.
    const others = clientPage.locator('label:has(input[name="cakeCategoryId"])', { hasText: 'Outros tamanhos' })
    if (await others.count()) await others.click()
    await choose(clientPage, `Médio ${RUN}`)
    await choose(clientPage, `Chocolate ${RUN}`)
    await choose(clientPage, `Brigadeiro ${RUN}`)
    await clientPage.getByLabel('Decoração').fill('Flores em tons de rosa')
    await clientPage.getByRole('button', { name: 'Continuar' }).click()

    await choose(clientPage, 'Não, obrigado(a)')
    await clientPage.getByRole('button', { name: 'Continuar' }).click()

    await clientPage.locator('label:has(input[name="pickupDate"])').first().click()
    await choose(clientPage, '10:00')
    await clientPage.getByRole('button', { name: 'Continuar' }).click()

    await expect(clientPage.getByRole('heading', { name: 'Revise sua encomenda' })).toBeVisible()
    await clientPage.getByRole('button', { name: 'Enviar encomenda' }).click()
    await expect(clientPage.getByRole('heading', { name: 'Encomenda enviada!' })).toBeVisible()
    trackingCode = (await clientPage.getByLabel('Código de acompanhamento').textContent())?.trim() ?? ''
    expect(trackingCode).toMatch(/^[0-9A-Z]{4}(-[0-9A-Z]{4}){3}$/)
  })

  await test.step('proprietária envia o orçamento', async () => {
    await ownerPage.goto(`/admin/pedidos?busca=${trackingCode}`)
    await ownerPage.getByRole('link', { name: new RegExp(CUSTOMER) }).click()
    await ownerPage.getByRole('button', { name: 'Montar orçamento' }).click()
    // A sugestão traz o bolo (preço da tabela) e a decoração em linha separada; o teste usa só o bolo.
    await ownerPage.getByRole('button', { name: 'Remover item 2' }).click()
    await ownerPage.getByLabel('Item', { exact: true }).fill('Bolo personalizado')
    await ownerPage.getByLabel('Valor (R$)').fill('165,00')
    await ownerPage.getByRole('button', { name: 'Enviar para a cliente' }).click()
    await ownerPage.getByRole('button', { name: 'Confirmar envio' }).click()
    await expect(ownerPage.getByText('Orçamento enviado', { exact: true }).first()).toBeVisible()
  })

  await test.step('cliente aceita e envia o comprovante do sinal', async () => {
    await clientPage.goto('/acompanhar')
    await clientPage.getByLabel('Código de acompanhamento').fill(trackingCode)
    await clientPage.getByLabel('4 últimos dígitos do seu WhatsApp').fill('4321')
    await clientPage.getByRole('button', { name: 'Consultar' }).click()
    await clientPage.getByRole('button', { name: 'Aceitar orçamento' }).click()
    await clientPage.getByRole('button', { name: 'Confirmar aceite' }).click()
    await expect(clientPage.getByText(/pague o sinal de R\$\s82,50/)).toBeVisible()

    await clientPage.locator('input[type="file"]').setInputFiles({
      name: 'comprovante.pdf', mimeType: 'application/pdf', buffer: PDF,
    })
    await expect(clientPage.getByText('Em conferência pela confeitaria')).toBeVisible()
  })

  await test.step('proprietária confirma o sinal, produz e entrega', async () => {
    await ownerPage.reload()
    await ownerPage.getByRole('button', { name: 'Confirmar recebimento' }).click()
    await expect(ownerPage.getByText('Vaga reservada')).toBeVisible()

    await ownerPage.getByRole('button', { name: 'Iniciar produção' }).click()
    await ownerPage.getByRole('button', { name: 'Marcar como pronto' }).click()
    await expect(ownerPage.getByText(/Ainda falta receber R\$\s82,50/)).toBeVisible()
    await expect(ownerPage.getByRole('button', { name: 'Marcar como entregue' })).toBeDisabled()

    await ownerPage.getByRole('button', { name: 'Registrar pagamento' }).click()
    await ownerPage.getByRole('button', { name: 'Registrar', exact: true }).click()
    await expect(ownerPage.getByText('Pagamento: Pago')).toBeVisible()

    await ownerPage.getByRole('button', { name: 'Marcar como entregue' }).click()
    await expect(ownerPage.getByText('Produção: Entregue')).toBeVisible()
  })

  await test.step('cliente vê o pedido entregue', async () => {
    await clientPage.reload()
    await expect(clientPage.getByRole('status')).toContainText('Pedido entregue')
  })

  await test.step('pagamentos aparecem no financeiro do mês', async () => {
    await ownerPage.goto('/admin/financeiro')
    await expect(ownerPage.getByText('Recebido no período')).toBeVisible()
    const received = ownerPage.locator('dt', { hasText: 'Recebido no período' }).locator('xpath=following-sibling::dd[1]')
    await expect(received).not.toContainText('R$ 0,00')
  })

  await owner.close()
  await client.close()
})
