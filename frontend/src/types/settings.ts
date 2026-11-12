export interface BakerySettings {
  name: string | null
  aboutText: string | null
  whatsappContact: string | null
  instagram: string | null
  address: string | null
  pickupInstructions: string | null
  pixKey: string | null
  pixHolderName: string | null
  quoteValidityHours: number
  quoteExpiryWarningHours: number
  depositDeadlineHours: number | null
  ownerNotificationEmail: string | null
  pickupEnabled: boolean
  deliveryEnabled: boolean
  deliveryInfo: string | null
  customCakeEnabled: boolean
  customCakeDelivery: boolean
  version: number
}

export type BakerySettingsInput = Omit<BakerySettings, 'version'> & { version: number }
