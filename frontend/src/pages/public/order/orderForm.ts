/**
 * Estado e validação do formulário de encomenda, separados da interface para poderem ser testados.
 * O backend valida tudo de novo — estas regras só orientam a cliente antes do envio.
 */
import type { FulfillmentType, OrderItemInput, PublicProduct } from '../../../types/product'
import type { OrderPayload, PublicSize } from '../../../types/public'

export const MAX_REFERENCE_PHOTOS = 3
export const MAX_FILE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const ACCEPTED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp']

/** Etapas possíveis; as de bolo e topo só aparecem quando a cliente quer um bolo personalizado. */
export type StepKey = 'customer' | 'items' | 'cake' | 'topper' | 'schedule' | 'review'

export const STEP_LABELS: Record<StepKey, string> = {
  customer: 'Seus dados',
  items: 'Produtos',
  cake: 'Seu bolo',
  topper: 'Topo de bolo',
  schedule: 'Data e entrega',
  review: 'Revisão',
}

/**
 * Sequência de etapas. Sem produtos no catálogo, a etapa "Produtos" não aparece e a encomenda é de bolo
 * personalizado, como antes.
 */
export function visibleSteps(form: Pick<OrderFormState, 'customCake'>, hasProducts: boolean): StepKey[] {
  const steps: StepKey[] = ['customer']
  if (hasProducts) steps.push('items')
  if (form.customCake) steps.push('cake', 'topper')
  steps.push('schedule', 'review')
  return steps
}

export interface OrderFormState {
  customerName: string
  customerWhatsapp: string
  customCake: boolean
  items: OrderItemInput[]
  pickupDate: string
  pickupTime: string
  fulfillmentType: FulfillmentType
  deliveryAddress: string
  deliveryDistrict: string
  deliveryReference: string
  sizeId: number | null
  flavorIds: number[]
  fillingIds: number[]
  decoration: string
  cakeText: string
  notes: string
  referencePhotos: File[]
  topperRequested: boolean
  topperTheme: string
  topperName: string
  topperAge: string
  topperPhoto: File | null
}

export const initialOrderForm: OrderFormState = {
  customerName: '',
  customerWhatsapp: '',
  customCake: true,
  items: [],
  pickupDate: '',
  pickupTime: '',
  fulfillmentType: 'PICKUP',
  deliveryAddress: '',
  deliveryDistrict: '',
  deliveryReference: '',
  sizeId: null,
  flavorIds: [],
  fillingIds: [],
  decoration: '',
  cakeText: '',
  notes: '',
  referencePhotos: [],
  topperRequested: false,
  topperTheme: '',
  topperName: '',
  topperAge: '',
  topperPhoto: null,
}

export type FieldErrors = Partial<Record<keyof OrderFormState, string>>

/** Em qual etapa está cada campo (para levar a cliente ao erro devolvido pelo servidor). */
export const FIELD_STEP: Record<string, StepKey> = {
  customerName: 'customer',
  customerWhatsapp: 'customer',
  customCake: 'items',
  items: 'items',
  sizeId: 'cake',
  flavorIds: 'cake',
  fillingIds: 'cake',
  decoration: 'cake',
  cakeText: 'cake',
  referencePhotos: 'cake',
  topperRequested: 'topper',
  topperTheme: 'topper',
  topperName: 'topper',
  topperAge: 'topper',
  topperPhoto: 'topper',
  pickupDate: 'schedule',
  pickupTime: 'schedule',
  fulfillmentType: 'schedule',
  deliveryAddress: 'schedule',
  deliveryDistrict: 'schedule',
  deliveryReference: 'schedule',
  notes: 'schedule',
}

/** Aceita os formatos comuns de celular/fixo brasileiro com DDD (a normalização final é do servidor). */
export function looksLikeBrazilianPhone(value: string): boolean {
  let digits = value.replace(/\D/g, '')
  if (digits.startsWith('55') && (digits.length === 12 || digits.length === 13)) digits = digits.slice(2)
  if (digits.startsWith('0') && (digits.length === 11 || digits.length === 12)) digits = digits.slice(1)
  if (digits.length === 11) return digits[2] === '9' && digits[0] !== '0' && digits[1] !== '0'
  if (digits.length === 10) return /[2-5]/.test(digits[2]) && digits[0] !== '0' && digits[1] !== '0'
  return false
}

export function validatePhoto(file: File): string | undefined {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type) || !ACCEPTED_EXTENSIONS.includes(extension)) {
    return `"${file.name}" não é uma foto aceita. Use JPG, PNG ou WEBP.`
  }
  if (file.size > MAX_FILE_BYTES) {
    return `"${file.name}" tem mais de 5 MB. Escolha uma foto menor.`
  }
  return undefined
}

export interface ValidationContext {
  size?: PublicSize
  products?: PublicProduct[]
}

export function validateStep(step: StepKey, form: OrderFormState, context: ValidationContext = {}): FieldErrors {
  const errors: FieldErrors = {}
  const { size, products = [] } = context
  if (step === 'customer') {
    const name = form.customerName.trim()
    if (name.length < 2) errors.customerName = 'Informe seu nome completo.'
    else if (name.length > 120) errors.customerName = 'O nome deve ter no máximo 120 caracteres.'
    if (!form.customerWhatsapp.trim()) errors.customerWhatsapp = 'Informe seu WhatsApp.'
    else if (!looksLikeBrazilianPhone(form.customerWhatsapp))
      errors.customerWhatsapp = 'Informe um WhatsApp válido com DDD, ex.: (11) 99999-8888.'
  }
  if (step === 'items') {
    if (!form.customCake && form.items.length === 0)
      errors.items = 'Escolha pelo menos um produto ou o bolo personalizado.'
    for (const item of form.items) {
      const product = products.find((p) => p.id === item.productId)
      if (product && item.quantity < product.minQuantity) {
        errors.items = `A quantidade mínima de "${product.name}" é ${product.minQuantity}.`
        break
      }
    }
  }
  if (step === 'cake') {
    if (!size) errors.sizeId = 'Escolha o tamanho.'
    if (form.flavorIds.length === 0) errors.flavorIds = 'Escolha pelo menos uma massa.'
    else if (size && form.flavorIds.length > size.maxFlavors)
      errors.flavorIds = `Este tamanho permite até ${size.maxFlavors} ${size.maxFlavors === 1 ? 'massa' : 'massas'}.`
    if (form.fillingIds.length === 0) errors.fillingIds = 'Escolha pelo menos um recheio.'
    else if (size && form.fillingIds.length > size.maxFillings)
      errors.fillingIds = `Este tamanho permite até ${size.maxFillings} ${size.maxFillings === 1 ? 'recheio' : 'recheios'}.`
    if (form.cakeText.length > 100) errors.cakeText = 'Máximo de 100 caracteres.'
    if (form.decoration.length > 1000) errors.decoration = 'Máximo de 1000 caracteres.'
    if (form.referencePhotos.length > MAX_REFERENCE_PHOTOS)
      errors.referencePhotos = `Envie no máximo ${MAX_REFERENCE_PHOTOS} fotos.`
    const invalidPhoto = form.referencePhotos.map(validatePhoto).find(Boolean)
    if (invalidPhoto) errors.referencePhotos = invalidPhoto
  }
  if (step === 'topper' && form.topperRequested) {
    if (!form.topperTheme.trim()) errors.topperTheme = 'Informe o tema do topo.'
    if (form.topperAge.trim() && !/^\d{1,3}$/.test(form.topperAge.trim()))
      errors.topperAge = 'Informe a idade em números.'
    else if (form.topperAge.trim() && Number(form.topperAge) > 120) errors.topperAge = 'Idade inválida.'
    if (form.topperPhoto) {
      const invalid = validatePhoto(form.topperPhoto)
      if (invalid) errors.topperPhoto = invalid
    }
  }
  if (step === 'schedule') {
    if (!form.pickupDate) errors.pickupDate = 'Escolha a data.'
    else if (!form.pickupTime) errors.pickupTime = 'Escolha o horário.'
    if (form.fulfillmentType === 'DELIVERY' && !form.deliveryAddress.trim())
      errors.deliveryAddress = 'Informe o endereço de entrega.'
    if (form.notes.length > 1000) errors.notes = 'Máximo de 1000 caracteres.'
  }
  return errors
}

/** Ao trocar de tamanho, mantém só as escolhas que cabem no novo limite. */
export function applySize(form: OrderFormState, size: PublicSize): OrderFormState {
  return {
    ...form,
    sizeId: size.id,
    flavorIds: form.flavorIds.slice(0, size.maxFlavors),
    fillingIds: form.fillingIds.slice(0, size.maxFillings),
  }
}

/**
 * Marca/desmarca uma opção. Com limite 1, escolher outra substitui a anterior;
 * acima disso, novas escolhas são ignoradas quando o limite já foi atingido.
 */
export function toggleChoice(selected: number[], id: number, max: number): number[] {
  if (selected.includes(id)) return selected.filter((value) => value !== id)
  if (max === 1) return [id]
  if (selected.length >= max) return selected
  return [...selected, id]
}

export function toPayload(form: OrderFormState): OrderPayload {
  const cake = form.customCake
  const topper = cake && form.topperRequested
  const delivery = form.fulfillmentType === 'DELIVERY'
  return {
    customerName: form.customerName.trim(),
    customerWhatsapp: form.customerWhatsapp.trim(),
    pickupDate: form.pickupDate,
    pickupTime: form.pickupTime,
    customCake: cake,
    sizeId: cake ? form.sizeId : null,
    flavorIds: cake ? form.flavorIds : [],
    fillingIds: cake ? form.fillingIds : [],
    decoration: cake ? form.decoration.trim() : '',
    cakeText: cake ? form.cakeText.trim() : '',
    notes: form.notes.trim(),
    topperRequested: topper,
    topperTheme: topper ? form.topperTheme.trim() : '',
    topperName: topper ? form.topperName.trim() : '',
    topperAge: topper && form.topperAge.trim() ? Number(form.topperAge) : null,
    items: form.items.map(({ productId, quantity }) => ({ productId, quantity })),
    fulfillmentType: form.fulfillmentType,
    deliveryAddress: delivery ? form.deliveryAddress.trim() : '',
    deliveryDistrict: delivery ? form.deliveryDistrict.trim() : '',
    deliveryReference: delivery ? form.deliveryReference.trim() : '',
  }
}
