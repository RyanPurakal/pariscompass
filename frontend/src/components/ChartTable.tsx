import type { LineSeries } from './LineChart'
import { formatValue } from '../lib/format'
import styles from './ChartTable.module.css'

/** Every chart's table twin, collapsed by default: the accessible, color-free way to read the values. */
export function ChartTable({ title, series, unit }: { title: string; series: LineSeries[]; unit: string }) {
  const years = [...new Set(series.flatMap((s) => s.points.map((p) => p.year)))].sort((a, b) => b - a)
  const lookup = series.map((s) => new Map(s.points.map((p) => [p.year, p.value])))
  return (
    <details className={styles.details}>
      <summary>Show data table</summary>
      <div className={styles.scroller} tabIndex={0} role="region" aria-label={`${title} data`}>
        <table className={styles.table}>
          <caption className="visually-hidden">{title}</caption>
          <thead>
            <tr>
              <th scope="col">Year</th>
              {series.map((s) => (
                <th key={s.key} scope="col">
                  {s.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {years.map((y) => (
              <tr key={y}>
                <th scope="row" className="tabular">
                  {y}
                </th>
                {lookup.map((m, i) => (
                  <td key={series[i]!.key} className="tabular">
                    {m.has(y) ? formatValue(m.get(y), unit) : ''}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}
