import type { MetricInfo } from '../api/client'
import styles from './Filters.module.css'

export function FilterRow({ children, label }: { children: React.ReactNode; label: string }) {
  return (
    <div className={styles.row} role="group" aria-label={label}>
      {children}
    </div>
  )
}

export function MetricSelect({ metrics, value, onChange, id = 'metric' }: { metrics: MetricInfo[]; value: string; onChange: (code: string) => void; id?: string }) {
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        Metric
      </label>
      <select id={id} className={styles.select} value={value} onChange={(e) => onChange(e.target.value)}>
        {metrics.map((m) => (
          <option key={m.code} value={m.code}>
            {m.name} ({m.unit})
          </option>
        ))}
      </select>
    </div>
  )
}

export function YearSlider({ min, max, value, onChange }: { min: number; max: number; value: number; onChange: (year: number) => void }) {
  return (
    <div className={`${styles.field} ${styles.grow}`}>
      <label htmlFor="year" className={styles.label}>
        Year <output htmlFor="year" className={`${styles.output} tabular`}>{value}</output>
      </label>
      <div className={styles.sliderRow}>
        <span className="tabular" aria-hidden="true">{min}</span>
        <input
          id="year"
          className={styles.slider}
          type="range"
          min={min}
          max={max}
          step={1}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
        />
        <span className="tabular" aria-hidden="true">{max}</span>
      </div>
    </div>
  )
}

export const WINDOWS = [
  { value: 'all', label: 'All years', from: undefined },
  { value: '1990', label: 'Since 1990', from: 1990 },
  { value: '2000', label: 'Since 2000', from: 2000 },
  { value: '2015', label: 'Since 2015', from: 2015 },
] as const
export type WindowValue = (typeof WINDOWS)[number]['value']

export function WindowPicker({ value, onChange, name = 'window' }: { value: WindowValue; onChange: (v: WindowValue) => void; name?: string }) {
  return (
    <fieldset className={styles.segmented}>
      <legend className={styles.label}>Time span</legend>
      <div className={styles.segments}>
        {WINDOWS.map((w) => (
          <label key={w.value}>
            <input type="radio" name={name} value={w.value} checked={value === w.value} onChange={() => onChange(w.value)} />
            <span>{w.label}</span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
