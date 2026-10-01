import { quantileSorted } from 'd3-array'
import { scaleThreshold } from 'd3-scale'
import { DIVERGING, NO_DATA, SEQUENTIAL, type Theme } from './palette'

export const CLASS_COUNT = 7

export interface Binning {
  kind: 'sequential' | 'diverging'
  /** Class boundaries, ascending; there is one more color than thresholds. */
  thresholds: number[]
  colors: string[]
  noData: string
  colorFor: (value: number | null | undefined) => string
}

/**
 * Seven classes, the most a reader can tell apart on a map.
 * Temperature anomalies diverge around zero (symmetric classes, so gray means "about normal").
 * Every other metric uses quantile classes: emissions are so skewed (China 12,289 Mt, most countries
 * under 100) that equal intervals would paint nearly every country the lightest color.
 */
export function binningFor(metric: string, values: number[], theme: Theme): Binning {
  const sorted = values.filter(Number.isFinite).sort((a, b) => a - b)
  let kind: Binning['kind']
  let thresholds: number[]
  let colors: string[]

  if (metric === 'temperature_anomaly_c') {
    kind = 'diverging'
    const maxAbs = Math.max(0.5, ...sorted.map(Math.abs))
    const step = Math.ceil((maxAbs / 3.5) * 10) / 10
    thresholds = [-2.5, -1.5, -0.5, 0.5, 1.5, 2.5].map((k) => round(k * step))
    colors = [...DIVERGING[theme]]
  } else {
    kind = 'sequential'
    const raw = Array.from({ length: CLASS_COUNT - 1 }, (_, i) =>
      sorted.length ? quantileSorted(sorted, (i + 1) / CLASS_COUNT) ?? 0 : 0,
    )
    thresholds = [...new Set(raw.map(nice))]
    // Fewer distinct thresholds (e.g. many zeros) means fewer classes; spread them over the ramp.
    const ramp = SEQUENTIAL[theme]
    const n = thresholds.length + 1
    colors = Array.from({ length: n }, (_, i) => ramp[Math.round((i * (ramp.length - 1)) / Math.max(1, n - 1))]!)
  }

  const scale = scaleThreshold<number, string>().domain(thresholds).range(colors)
  return {
    kind,
    thresholds,
    colors,
    noData: NO_DATA[theme],
    colorFor: (v) => (v === null || v === undefined || !Number.isFinite(v) ? NO_DATA[theme] : scale(v)),
  }
}

function round(v: number): number {
  return Math.round(v * 100) / 100
}

/** Two significant figures, so legend boundaries read cleanly. */
function nice(v: number): number {
  if (v === 0) return 0
  const magnitude = 10 ** (Math.floor(Math.log10(Math.abs(v))) - 1)
  return Math.round(v / magnitude) * magnitude
}
