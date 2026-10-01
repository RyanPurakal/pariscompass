const compact = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 })
const grouped = (digits: number) =>
  new Intl.NumberFormat('en', { minimumFractionDigits: digits, maximumFractionDigits: digits })

/** Decimal places that suit a metric's unit. */
function digitsFor(unit: string): number {
  if (unit === '%' || unit.startsWith('t ')) return 1
  if (unit === '°C') return 2
  return 0
}

/** A value with its unit, compact for big magnitudes: "4,904 Mt CO2", "14.2 t CO2/person", "+0.83 °C". */
export function formatValue(value: number | null | undefined, unit: string): string {
  if (value === null || value === undefined || Number.isNaN(value)) return 'No data'
  if (unit === 'people') return compact.format(value)
  if (unit === '°C') return `${value > 0 ? '+' : ''}${grouped(2).format(value)} °C`
  if (unit === '%') return `${grouped(1).format(value)}%`
  const digits = Math.abs(value) >= 10_000 ? 0 : digitsFor(unit)
  return `${grouped(digits).format(value)} ${unit}`
}

/** Number at the unit's full precision, without the unit: for table cells whose header names the unit. */
export function formatPlain(value: number, unit: string): string {
  if (unit === 'people' || Math.abs(value) >= 10_000) return grouped(0).format(value)
  if (unit === '°C') return `${value > 0 ? '+' : ''}${grouped(2).format(value)}`
  return grouped(digitsFor(unit)).format(value)
}

/** Number only, for axis ticks and legends: compact above 10k. */
export function formatNumber(value: number, unit: string): string {
  if (Math.abs(value) >= 10_000) return compact.format(value)
  const digits = unit === '°C' ? 1 : digitsFor(unit)
  return grouped(Math.abs(value) < 10 ? digits : 0).format(value)
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en', { dateStyle: 'medium', timeStyle: 'short' })
}
