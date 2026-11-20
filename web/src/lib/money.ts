/** Money helpers. The API sends integer minor units plus an ISO currency code. */

export function minorDigits(currency: string): number {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
    .maximumFractionDigits ?? 2
}

/** 123456 EGP -> "EGP 1,234.56" */
export function formatMinor(minor: number, currency: string): string {
  const digits = minorDigits(currency)
  const value = minor / 10 ** digits
  const text = new Intl.NumberFormat('en', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(value))
  return `${minor < 0 ? '-' : ''}${currency} ${text}`
}

/**
 * Parses user input like "1,250.5" into minor units without floating point drift.
 * Returns null for anything that is not a plain positive amount with at most the currency's decimals.
 */
export function parseToMinor(input: string, currency: string): number | null {
  const digits = minorDigits(currency)
  const clean = input.replace(/[,\s]/g, '')
  const match = /^(\d+)(?:\.(\d*))?$/.exec(clean)
  if (!match) return null
  const [, whole, fraction = ''] = match
  if (fraction.length > digits) return null
  const minor = Number(whole) * 10 ** digits + Number(fraction.padEnd(digits, '0') || '0')
  return Number.isSafeInteger(minor) ? minor : null
}

/** Minor units back to the decimal string the API expects ("600.50"). */
export function minorToDecimal(minor: number, currency: string): string {
  const digits = minorDigits(currency)
  if (digits === 0) return String(minor)
  const sign = minor < 0 ? '-' : ''
  const abs = Math.abs(minor).toString().padStart(digits + 1, '0')
  return `${sign}${abs.slice(0, -digits)}.${abs.slice(-digits)}`
}
