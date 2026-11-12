import type { Availability, BakeryInfo, PublicCatalog } from '../../../../types/public'
import type { FieldErrors, OrderFormState } from '../orderForm'

export interface StepProps {
  form: OrderFormState
  errors: FieldErrors
  update: (patch: Partial<OrderFormState>) => void
  catalog: PublicCatalog
  availability: Availability
  bakery: BakeryInfo
  /** Na edição pelo acompanhamento não há envio de fotos. */
  allowPhotos?: boolean
}
