import { describe, expect, it } from 'vitest'
import { formatNumber, formatPlain, formatValue } from './format'

describe('formatValue', () => {
  it('formats each unit the way the UI shows it', () => {
    expect(formatValue(4904.12, 'Mt CO2')).toBe('4,904 Mt CO2')
    expect(formatValue(14.197, 't CO2/person')).toBe('14.2 t CO2/person')
    expect(formatValue(25.638, '%')).toBe('25.6%')
    expect(formatValue(0.833, '°C')).toBe('+0.83 °C')
    expect(formatValue(-0.4, '°C')).toBe('-0.40 °C')
    expect(formatValue(1450935785, 'people')).toBe('1.5B')
    expect(formatValue(12289.037, 'Mt CO2')).toBe('12,289 Mt CO2')
  })

  it('says No data for missing values', () => {
    expect(formatValue(null, '%')).toBe('No data')
    expect(formatValue(undefined, '%')).toBe('No data')
  })
})

describe('formatNumber and formatPlain', () => {
  it('keeps axis ticks short', () => {
    expect(formatNumber(25000, 'Mt CO2')).toBe('25K')
    expect(formatNumber(5, 't CO2/person')).toBe('5.0')
  })

  it('keeps table cells at full precision without the unit', () => {
    expect(formatPlain(41.271, 't CO2/person')).toBe('41.3')
    expect(formatPlain(1.04, '°C')).toBe('+1.04')
  })
})
