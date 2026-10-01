import { extent } from 'd3-array'
import { scaleLinear } from 'd3-scale'
import { useMemo } from 'react'
import { CartesianGrid, Line, LineChart as RLineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatNumber, formatValue } from '../lib/format'
import { ChartTable } from './ChartTable'
import styles from './LineChart.module.css'

export interface LineSeries {
  key: string
  label: string
  color: string
  points: { year: number; value: number }[]
  /** Projections only: dashing reads as "not observed". */
  dashed?: boolean
}

interface Props {
  series: LineSeries[]
  unit: string
  title: string
  height?: number
  /** Include zero in the y range (magnitudes); off for anomalies. */
  zeroBaseline?: boolean
  stale?: boolean
  /** Direct labels at line ends (2 to 4 series). Off when lines meet, e.g. observed flowing into projected. */
  endLabels?: boolean
}

const MARGIN = { top: 12, right: 16, bottom: 4, left: 4 }
const LABEL_MARGIN = 92
const X_AXIS_HEIGHT = 28
const LABEL_GAP_PX = 16

type Row = { year: number } & Record<string, number | undefined>

/**
 * Line chart following the dataviz specs: 2px lines, hairline solid grid, one y-axis, crosshair tooltip
 * listing every series at the hovered year, a legend for 2+ series, end labels for up to 4 series
 * (dropped when they would collide), and a table view so no value is hover-only.
 */
export function LineChart({ series, unit, title, height = 260, zeroBaseline = true, stale, endLabels = true }: Props) {
  const rows = useMemo(() => mergeByYear(series), [series])

  const yScale = useMemo(() => {
    const values = series.flatMap((s) => s.points.map((p) => p.value))
    const [lo = 0, hi = 1] = extent(values)
    const domain: [number, number] = zeroBaseline ? [Math.min(0, lo), Math.max(0, hi)] : [lo, hi]
    if (domain[0] === domain[1]) domain[1] = domain[0] + 1
    return scaleLinear().domain(domain).nice(5)
  }, [series, zeroBaseline])
  const [yMin, yMax] = yScale.domain() as [number, number]

  // Which end labels fit: estimate each label's pixel y and keep those at least LABEL_GAP_PX apart.
  const labelled = useMemo(() => {
    if (!endLabels || series.length < 2 || series.length > 4) return new Set<string>()
    const plot = height - MARGIN.top - MARGIN.bottom - X_AXIS_HEIGHT
    const ends = series
      .filter((s) => s.points.length > 0 && !s.dashed)
      .map((s) => ({ key: s.key, y: (1 - (s.points.at(-1)!.value - yMin) / (yMax - yMin)) * plot }))
      .sort((a, b) => a.y - b.y)
    const keep = new Set<string>()
    let lastY = -Infinity
    for (const e of ends) {
      if (e.y - lastY >= LABEL_GAP_PX) {
        keep.add(e.key)
        lastY = e.y
      }
    }
    return keep
  }, [series, height, yMin, yMax, endLabels])

  if (series.every((s) => s.points.length === 0)) {
    return <p className={styles.empty}>No data for this period.</p>
  }

  const showLegend = series.length >= 2
  return (
    <figure className={styles.figure} data-stale={stale || undefined}>
      {showLegend && (
        <ul className={styles.legend} aria-label="Legend">
          {series.map((s) => (
            <li key={s.key}>
              <svg width="18" height="8" aria-hidden="true">
                <line x1="1" y1="4" x2="17" y2="4" stroke={s.color} strokeWidth="2" strokeLinecap="round" strokeDasharray={s.dashed ? '4 3' : undefined} />
              </svg>
              {s.label}
            </li>
          ))}
        </ul>
      )}
      <div className={styles.plot} aria-hidden="true">
        <ResponsiveContainer width="100%" height={height}>
          <RLineChart data={rows} margin={{ ...MARGIN, right: labelled.size > 0 ? LABEL_MARGIN : MARGIN.right }}>
            <CartesianGrid vertical={false} className={styles.grid} />
            <XAxis
              dataKey="year"
              type="number"
              domain={['dataMin', 'dataMax']}
              tickCount={6}
              allowDecimals={false}
              tickLine={false}
              height={X_AXIS_HEIGHT}
              className={styles.axis}
            />
            <YAxis
              domain={[yMin, yMax]}
              ticks={yScale.ticks(5)}
              tickFormatter={(v: number) => (Number.isInteger(v) ? formatNumber(v, '') : formatNumber(v, unit))}
              tickLine={false}
              axisLine={false}
              width={56}
              className={styles.axis}
            />
            {!zeroBaseline && yMin < 0 && yMax > 0 && <ReferenceLine y={0} className={styles.zero} ifOverflow="hidden" />}
            <Tooltip
              cursor={{ className: styles.crosshair }}
              isAnimationActive={false}
              content={({ active, payload, label }) =>
                active && payload?.length ? (
                  <div className={styles.tooltip}>
                    <span className={styles.tooltipYear}>{label}</span>
                    {series.map((s) => {
                      const item = payload.find((p) => p.dataKey === s.key)
                      if (item?.value === undefined || item.value === null) return null
                      return (
                        <div key={s.key} className={styles.tooltipRow}>
                          <svg width="14" height="8" aria-hidden="true">
                            <line x1="1" y1="4" x2="13" y2="4" stroke={s.color} strokeWidth="2" strokeDasharray={s.dashed ? '3 2' : undefined} />
                          </svg>
                          <strong>{formatValue(Number(item.value), unit)}</strong>
                          <span>{s.label}</span>
                        </div>
                      )
                    })}
                  </div>
                ) : null
              }
            />
            {series.map((s) => (
              <Line
                key={s.key}
                dataKey={s.key}
                name={s.label}
                stroke={s.color}
                strokeWidth={2}
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeDasharray={s.dashed ? '6 4' : undefined}
                dot={false}
                activeDot={{ r: 4, strokeWidth: 2, className: styles.activeDot }}
                connectNulls={false}
                isAnimationActive={false}
                label={(props: { x?: number | string; y?: number | string; index?: number }) => {
                  const last = lastIndex(rows, s.key)
                  if (props.index !== last || !labelled.has(s.key)) return <g key={`${s.key}-${props.index}`} />
                  return (
                    <text key={`${s.key}-end`} x={Number(props.x) + 8} y={Number(props.y)} dy="0.35em" className={styles.endLabel}>
                      {s.label}
                    </text>
                  )
                }}
              />
            ))}
          </RLineChart>
        </ResponsiveContainer>
      </div>
      <ChartTable title={title} series={series} unit={unit} />
    </figure>
  )
}

function mergeByYear(series: LineSeries[]): Row[] {
  const byYear = new Map<number, Row>()
  for (const s of series) {
    for (const p of s.points) {
      const row = byYear.get(p.year) ?? { year: p.year }
      row[s.key] = p.value
      byYear.set(p.year, row)
    }
  }
  return [...byYear.values()].sort((a, b) => a.year - b.year)
}

function lastIndex(rows: Row[], key: string): number {
  for (let i = rows.length - 1; i >= 0; i--) if (rows[i]![key] !== undefined) return i
  return -1
}
