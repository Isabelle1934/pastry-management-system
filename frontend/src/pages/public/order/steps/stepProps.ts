import type { Availability, PublicCatalog } from '../../../../types/public'
import type { FieldErrors, OrderFormState } from '../orderForm'

export interface StepProps {
  form: OrderFormState
  errors: FieldErrors
  update: (patch: Partial<OrderFormState>) => void
  catalog: PublicCatalog
  availability: Availability
  /** Na edição pelo acompanhamento não há envio de fotos. */
  allowPhotos?: boolean
}
