import { describe, expect, it } from 'vitest'
import { DIVERGING, NO_DATA, SEQUENTIAL } from './palette'
import { binningFor, CLASS_COUNT } from './scales'

describe('binningFor', () => {
  // A skewed distribution like CO2 totals: many small emitters, a few huge ones.
  const skewed = [0.5, 1, 2, 3, 5, 8, 12, 20, 40, 80, 150, 400, 900, 4904, 12289]

  it('uses at most seven quantile classes for skewed metrics', () => {
    const b = binningFor('co2_total_mt', skewed, 'light')
    expect(b.kind).toBe('sequential')
    expect(b.colors.length).toBeLessThanOrEqual(CLASS_COUNT)
    expect(b.thresholds.length).toBe(b.colors.length - 1)
    expect([...b.thresholds].sort((a, z) => a - z)).toEqual(b.thresholds)
  })

  it('spreads countries across classes instead of painting most the lightest color', () => {
    const b = binningFor('co2_total_mt', skewed, 'light')
    const used = new Set(skewed.map((v) => b.colorFor(v)))
    expect(used.size).toBeGreaterThanOrEqual(6)
  })

  it('collapses duplicate thresholds when many values are equal', () => {
    const b = binningFor('renewables_share_elec_pct', [0, 0, 0, 0, 0, 0, 0, 0, 50, 100], 'light')
    expect(new Set(b.thresholds).size).toBe(b.thresholds.length)
    expect(b.colors.length).toBe(b.thresholds.length + 1)
  })

  it('maps missing values to the no-data color', () => {
    const b = binningFor('co2_total_mt', skewed, 'light')
    expect(b.colorFor(null)).toBe(NO_DATA.light)
    expect(b.colorFor(undefined)).toBe(NO_DATA.light)
    expect(b.colorFor(Number.NaN)).toBe(NO_DATA.light)
  })

  it('reverses the ramp in dark mode so low values recede into the dark surface', () => {
    expect(binningFor('co2_total_mt', skewed, 'dark').colorFor(0.1)).toBe(SEQUENTIAL.dark[0])
    expect(SEQUENTIAL.dark[0]).toBe(SEQUENTIAL.light.at(-1))
  })

  it('centers temperature anomalies on zero with a neutral middle class', () => {
    const b = binningFor('temperature_anomaly_c', [-1.2, -0.3, 0, 0.4, 1.1, 2.7], 'light')
    expect(b.kind).toBe('diverging')
    expect(b.thresholds).toHaveLength(6)
    expect(b.thresholds[2]).toBeCloseTo(-b.thresholds[3]!)
    expect(b.colorFor(0)).toBe(DIVERGING.light[3])
    expect(b.colorFor(2.7)).toBe(DIVERGING.light[6])
    expect(b.colorFor(-2.7)).toBe(DIVERGING.light[0])
  })
})
