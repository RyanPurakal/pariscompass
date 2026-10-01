import styles from './StatTile.module.css'

/** Label, value (proportional figures), and the year the value is from. */
export function StatTile({ label, value, year, note }: { label: string; value: string; year?: number | null; note?: string }) {
  return (
    <div className={styles.tile}>
      <p className={styles.label}>{label}</p>
      <p className={styles.value}>{value}</p>
      <p className={styles.meta}>
        {year ? <span className="tabular">{year}</span> : 'No data'}
        {note && <span> · {note}</span>}
      </p>
    </div>
  )
}
