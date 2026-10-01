import { useMemo } from 'react'
import { ApiError, type ProjectionResponse, type SeriesPoint } from '../api/client'
import { useGenerateProjection, useProjectionHistory } from '../hooks/queries'
import { formatDateTime, formatValue } from '../lib/format'
import { SERIES } from '../lib/palette'
import { useTheme } from '../lib/theme'
import { LineChart } from './LineChart'
import { ErrorState, LoadingBlock } from './States'
import styles from './ProjectionPanel.module.css'

const HISTORY_YEARS = 25

interface Props {
  iso3: string
  name: string
  /** Observed CO2 (Mt), any range; the last 25 years are drawn. */
  observed: SeriesPoint[]
}

/**
 * Five-year projection. Nothing is generated automatically: generation may call a paid model and is
 * rate limited, so it waits for the user. The newest stored projection, if any, is shown right away.
 */
export function ProjectionPanel({ iso3, name, observed }: Props) {
  const { theme } = useTheme()
  const history = useProjectionHistory(iso3)
  const generate = useGenerateProjection(iso3)

  const latest: ProjectionResponse | undefined = generate.data?.projection ?? history.data?.[0]

  const series = useMemo(() => {
    if (!latest) return []
    const recent = observed.filter((p) => p.year > latest.baseYear - HISTORY_YEARS && p.year <= latest.baseYear)
    const projected = [{ year: latest.baseYear, value: latest.baseYearCo2Mt }, ...latest.projection.projectedCo2Mt.map((p) => ({ year: p.year, value: p.co2Mt }))]
    return [
      { key: 'observed', label: 'Observed', color: SERIES[theme][0]!, points: recent },
      {
        key: 'projected',
        label: latest.generatedBy === 'model' ? 'Projected (AI)' : 'Projected (trend)',
        color: SERIES[theme][1]!,
        points: projected,
        dashed: true,
      },
    ]
  }, [latest, observed, theme])

  return (
    <section className={`card ${styles.panel}`} aria-labelledby="projection-title">
      <header className={styles.header}>
        <div>
          <p className="eyebrow">Five-year outlook</p>
          <h2 id="projection-title" className={styles.title}>
            CO2 outlook for {name}
          </h2>
        </div>
        <button type="button" className="button" onClick={() => generate.mutate()} disabled={generate.isPending}>
          {generate.isPending ? 'Generating…' : latest ? 'Regenerate' : 'Generate projection'}
        </button>
      </header>

      <div aria-live="polite">
        {generate.isPending && (
          <p className={styles.hint}>Asking the model. This usually takes 10 to 20 seconds; cached results return instantly.</p>
        )}
        {generate.isError && <GenerateError error={generate.error} />}
      </div>

      {history.isPending && !generate.data ? (
        <LoadingBlock label="Loading projections" height={200} />
      ) : history.isError && !generate.data ? (
        <ErrorState error={history.error} title="Stored projections could not be loaded" onRetry={() => history.refetch()} />
      ) : !latest ? (
        <p className={styles.hint}>
          No projection yet. Generating one sends {name}'s last 30 years of data to the model, validates the JSON it returns,
          and falls back to a labeled trend extrapolation if the output does not pass.
        </p>
      ) : (
        <>
          <ProvenanceBadge projection={latest} />
          <LineChart series={series} unit="Mt CO2" title={`${name} CO2 emissions, observed and projected`} height={240} endLabels={false} />
          <p className={styles.summary}>{latest.projection.summary}</p>
          <div className={styles.lists}>
            <div>
              <h3 className={styles.listTitle}>Drivers</h3>
              <ul>
                {latest.projection.keyDrivers.map((d) => (
                  <li key={d}>{d}</li>
                ))}
              </ul>
            </div>
            <div>
              <h3 className={styles.listTitle}>Risks to Paris alignment</h3>
              <ul>
                {latest.projection.risks.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            </div>
          </div>
          <p className={styles.meta}>
            {latest.baseYear + 5}: {formatValue(latest.projection.projectedCo2Mt.at(-1)?.co2Mt, 'Mt CO2')} vs{' '}
            {formatValue(latest.baseYearCo2Mt, 'Mt CO2')} in {latest.baseYear} ({latest.projection.co2Direction}) · confidence{' '}
            {latest.projection.confidence}
          </p>
        </>
      )}
    </section>
  )
}

function ProvenanceBadge({ projection: p }: { projection: ProjectionResponse }) {
  const isModel = p.generatedBy === 'model'
  return (
    <p className={styles.provenance} data-kind={isModel ? 'model' : 'fallback'}>
      <strong>{isModel ? `AI projection · ${p.model}` : 'Statistical fallback · trend extrapolation'}</strong>
      <span>
        {formatDateTime(p.generatedAt)}
        {p.cached && ' · served from cache'}
        {p.status === 'REPAIRED' && ' · corrected after one retry'}
        {!isModel && p.fallbackReason && ` · ${p.fallbackReason}`}
        {' · '}prompt {p.promptVersion}
      </span>
    </p>
  )
}

function GenerateError({ error }: { error: ApiError | Error }) {
  if (error instanceof ApiError && error.code === 'RATE_LIMITED') {
    return (
      <p className={styles.notice} role="alert">
        Too many projection requests. Try again in {error.retryAfterSeconds ?? 60} seconds.
      </p>
    )
  }
  return <ErrorState error={error} title="The projection could not be generated" />
}
