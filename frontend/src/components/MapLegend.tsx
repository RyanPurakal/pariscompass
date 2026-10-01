import { formatNumber } from '../lib/format'
import type { Binning } from '../lib/scales'
import styles from './MapLegend.module.css'

/** Class swatches with their boundaries, plus "No data". Values are also in the table, so color is never the only channel. */
export function MapLegend({ binning, unit, title }: { binning: Binning; unit: string; title: string }) {
  const { colors, thresholds } = binning
  return (
    <figure className={styles.legend}>
      <figcaption className={styles.title}>
        {title} <span className={styles.unit}>({unit})</span>
        <span className={styles.note}>
          {binning.kind === 'diverging' ? 'Gray is close to the 1991-2020 average' : 'Each class holds about the same number of countries'}
        </span>
      </figcaption>
      <div className={styles.row}>
        <ol className={styles.scale} aria-label="Color classes">
          {colors.map((c, i) => {
            const lo = thresholds[i - 1]
            const hi = thresholds[i]
            const range =
              lo === undefined
                ? `below ${formatNumber(hi!, unit)}`
                : hi === undefined
                  ? `${formatNumber(lo, unit)} and above`
                  : `${formatNumber(lo, unit)} to ${formatNumber(hi, unit)}`
            return (
              <li key={c + i} className={styles.cell}>
                <span className={styles.swatch} style={{ background: c }} aria-hidden="true" />
                <span className="visually-hidden">{range}</span>
                {hi !== undefined && (
                  <span className={styles.tick} aria-hidden="true">
                    {formatNumber(hi, unit)}
                  </span>
                )}
              </li>
            )
          })}
        </ol>
        <div className={styles.noData}>
          <span className={styles.swatch} style={{ background: binning.noData }} aria-hidden="true" />
          No data
        </div>
      </div>
    </figure>
  )
}
