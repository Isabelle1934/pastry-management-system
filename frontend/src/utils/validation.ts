/**
 * Validações de formulário no navegador. São apenas para orientar a usuária:
 * o backend valida tudo novamente e é a fonte da verdade.
 */

export const PASSWORD_MIN_LENGTH = 10
export const PASSWORD_MAX_LENGTH = 64

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateEmail(value: string): string | undefined {
  const email = value.trim()
  if (!email) return 'Informe o e-mail.'
  if (!EMAIL_PATTERN.test(email)) return 'E-mail inválido.'
  return undefined
}

export function validateNewPassword(value: string): string | undefined {
  const length = [...value].length
  if (length < PASSWORD_MIN_LENGTH) return `A senha deve ter pelo menos ${PASSWORD_MIN_LENGTH} caracteres.`
  if (length > PASSWORD_MAX_LENGTH) return `A senha deve ter no máximo ${PASSWORD_MAX_LENGTH} caracteres.`
  return undefined
}

export function validateConfirmation(password: string, confirmation: string): string | undefined {
  if (!confirmation) return 'Repita a nova senha.'
  if (password !== confirmation) return 'As senhas não são iguais.'
  return undefined
}
