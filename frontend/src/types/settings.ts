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
  ownerNotificationEmail: string | null
  version: number
}

export type BakerySettingsInput = Omit<BakerySettings, 'version'> & { version: number }
