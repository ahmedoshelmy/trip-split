import { describe, expect, it } from 'vitest'
import { formatMinor, minorDigits, minorToDecimal, parseToMinor } from './money'

describe('money', () => {
  it('knows minor digits per currency', () => {
    expect(minorDigits('EGP')).toBe(2)
    expect(minorDigits('JPY')).toBe(0)
  })

  it('formats minor units with sign and grouping', () => {
    expect(formatMinor(123456, 'EGP')).toBe('EGP 1,234.56')
    expect(formatMinor(-5, 'USD')).toBe('-USD 0.05')
    expect(formatMinor(1500, 'JPY')).toBe('JPY 1,500')
  })

  it('parses input exactly, refusing extra decimals', () => {
    expect(parseToMinor('600', 'EGP')).toBe(60000)
    expect(parseToMinor('1,250.5', 'EGP')).toBe(125050)
    expect(parseToMinor('0.1', 'EGP')).toBe(10)
    expect(parseToMinor('1.005', 'EGP')).toBeNull()
    expect(parseToMinor('-3', 'EGP')).toBeNull()
    expect(parseToMinor('abc', 'EGP')).toBeNull()
  })

  it('round-trips minor units to API decimals', () => {
    expect(minorToDecimal(60050, 'EGP')).toBe('600.50')
    expect(minorToDecimal(5, 'USD')).toBe('0.05')
    expect(minorToDecimal(1500, 'JPY')).toBe('1500')
  })
})
