import type { AlignmentBand, AlignmentComponent } from '../api/client'
import { useAlignment } from '../hooks/queries'
import { ErrorState, LoadingBlock } from './States'
import styles from './AlignmentPanel.module.css'

const COMPONENT_LABELS: Record<string, { title: string; describe: (c: AlignmentComponent) => string }> = {
  emissions_trend: {
    title: 'CO2 trend',
    describe: (c) => `${signed(c.value, 2)}% per year, ${c.fromYear}-${c.toYear}`,
  },
  emissions_level: {
    title: 'CO2 per person',
    describe: (c) => `${c.value?.toFixed(1)} t in ${c.toYear}`,
  },
  clean_power_level: {
    title: 'Low-carbon electricity',
    describe: (c) => `${c.value?.toFixed(1)}% of generation in ${c.toYear}`,
  },
  clean_power_momentum: {
    title: 'Clean power momentum',
    describe: (c) => `${signed(c.value, 2)} pp per year, ${c.fromYear}-${c.toYear}`,
  },
}

/** Status is never color alone: each band has an icon shape and a word. */
const BAND: Record<AlignmentBand, { label: string; className: string; icon: string }> = {
  HIGH: { label: 'High alignment', className: styles.good!, icon: 'M3 8.5 L6.5 12 L13 4.5' },
  MEDIUM: { label: 'Medium alignment', className: styles.warning!, icon: 'M3 8 L13 8' },
  LOW: { label: 'Low alignment', className: styles.critical!, icon: 'M4 4 L12 12 M12 4 L4 12' },
}

export function AlignmentPanel({ iso3 }: { iso3: string }) {
  const { data, isPending, isError, error, refetch } = useAlignment(iso3)

  if (isPending) return <LoadingBlock label="Loading alignment score" height={360} />
  if (isError) return <ErrorState error={error} title="Alignment score unavailable" onRetry={() => refetch()} />

  const band = data.band ? BAND[data.band] : null
  return (
    <section className={`card ${styles.panel}`} aria-labelledby="alignment-title">
      <header className={styles.header}>
        <p className="eyebrow">Paris alignment · formula {data.formulaVersion}</p>
        <h2 id="alignment-title" className="visually-hidden">
          Paris alignment score
        </h2>
      </header>

      {data.score !== null && band ? (
        <div className={styles.hero}>
          <p className={styles.score}>
            {data.score.toFixed(1)}
            <span className={styles.outOf}>/100</span>
          </p>
          <p className={`${styles.band} ${band.className}`}>
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
              <circle cx="8" cy="8" r="7.5" className={styles.bandDisc} />
              <path d={band.icon} fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
            </svg>
            {band.label}
          </p>
        </div>
      ) : (
        <p className={styles.noScore}>No score: {data.reason}</p>
      )}

      <ul className={styles.components}>
        {data.components.map((c) => {
          const meta = COMPONENT_LABELS[c.key]
          const weight = c.effectiveWeight ?? c.weight
          return (
            <li key={c.key} className={styles.component} data-missing={!c.available || undefined}>
              <div className={styles.componentHead}>
                <span className={styles.componentTitle}>{meta?.title ?? c.key}</span>
                <span className={`${styles.subScore} tabular`}>{c.subScore !== null ? c.subScore.toFixed(0) : 'n/a'}</span>
              </div>
              <div
                className={styles.meter}
                role="meter"
                aria-label={`${meta?.title ?? c.key} sub-score`}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={c.subScore ?? 0}
                aria-valuetext={c.subScore !== null ? `${c.subScore.toFixed(0)} out of 100` : 'not available'}
              >
                <span style={{ width: `${c.subScore ?? 0}%` }} />
              </div>
              <p className={styles.componentMeta}>
                {c.available && meta ? meta.describe(c) : `Not available: ${c.note}`}
                {c.available && c.note && <> · {c.note}</>}
                {' · '}weight {(weight * 100).toFixed(0)}%
              </p>
            </li>
          )
        })}
      </ul>

      <p className={styles.disclaimer}>
        {data.disclaimer}{' '}
        <a href="https://github.com/RyanPurakal/pariscompass/blob/main/SCORING.md">Read the methodology</a>.
      </p>
    </section>
  )
}

function signed(v: number | null, digits: number): string {
  if (v === null) return 'n/a'
  return `${v > 0 ? '+' : ''}${v.toFixed(digits)}`
}
