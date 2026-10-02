import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CountrySearch } from '../components/CountrySearch'
import { FilterRow, MetricSelect, WINDOWS, WindowPicker, type WindowValue } from '../components/Filters'
import { LineChart } from '../components/LineChart'
import { EmptyState, ErrorState, LoadingBlock } from '../components/States'
import { useComparison, useCountries, useMetrics } from '../hooks/queries'
import { formatValue } from '../lib/format'
import { SERIES } from '../lib/palette'
import { assignSlots } from '../lib/slots'
import { useTheme } from '../lib/theme'
import styles from './ComparePage.module.css'

const MAX = 4
const DEFAULT_METRIC = 'co2_per_capita_t'

/** 2 to 4 countries on one metric. Selection, metric and span live in the URL so a comparison can be shared. */
export default function ComparePage() {
  const { theme } = useTheme()
  const [params, setParams] = useSearchParams()
  const metrics = useMetrics()
  const countries = useCountries()

  const selected = useMemo(
    () => [...new Set((params.get('countries') ?? '').split(',').map((c) => c.trim().toUpperCase()).filter(Boolean))].slice(0, MAX),
    [params],
  )
  const metricCode = params.get('metric') ?? DEFAULT_METRIC
  const windowValue = (params.get('since') ?? '1990') as WindowValue
  const from = WINDOWS.find((w) => w.value === windowValue)?.from

  // Color follows the country, not its position: a country keeps its slot when others are removed.
  const [assigned, setAssigned] = useState<Record<string, number>>({})
  const slots = useMemo(() => assignSlots(selected, assigned), [selected, assigned])

  const comparison = useComparison(selected, metricCode, from)
  const metric = metrics.data?.find((m) => m.code === metricCode)
  const names = new Map((countries.data ?? []).map((c) => [c.iso3, c.name]))

  const set = (next: Record<string, string | undefined>) => {
    const p = new URLSearchParams(params)
    for (const [k, v] of Object.entries(next)) {
      if (v === undefined || v === '') p.delete(k)
      else p.set(k, v)
    }
    setParams(p, { replace: true })
  }
  const setCountries = (list: string[]) => {
    setAssigned(Object.fromEntries(assignSlots(list, slots)))
    set({ countries: list.join(',') })
  }

  const series = (comparison.data?.countries ?? []).map((c) => ({
    key: c.iso3,
    label: c.name,
    color: SERIES[theme][slots.get(c.iso3) ?? 0]!,
    points: c.points,
  }))

  return (
    <div className={styles.page}>
      <header className={`${styles.intro} reveal`}>
        <p className="eyebrow">02 · Compare</p>
        <h1 className={styles.title}>Side by side</h1>
        <p className={styles.lead}>Pick two to four countries and one metric.</p>
      </header>

      <div className={`${styles.controls} reveal`} style={{ '--i': 1 } as React.CSSProperties}>
        <div className={styles.picker}>
          <ul className={styles.chips} aria-label="Selected countries">
            {selected.map((iso) => (
              <li key={iso} className={styles.chip}>
                <span className={styles.swatch} style={{ background: SERIES[theme][slots.get(iso) ?? 0] }} aria-hidden="true" />
                <Link to={`/country/${iso}`}>{names.get(iso) ?? iso}</Link>
                <button type="button" aria-label={`Remove ${names.get(iso) ?? iso}`} onClick={() => setCountries(selected.filter((c) => c !== iso))}>
                  ×
                </button>
              </li>
            ))}
          </ul>
          <div className={styles.add}>
            <CountrySearch
              label="Add a country"
              placeholder={selected.length >= MAX ? `Up to ${MAX} countries` : 'Add a country'}
              exclude={selected}
              disabled={selected.length >= MAX}
              onSelect={(c) => setCountries([...selected, c.iso3])}
            />
          </div>
        </div>
        <FilterRow label="Comparison filters">
          {metrics.data && <MetricSelect id="compare-metric" metrics={metrics.data} value={metricCode} onChange={(code) => set({ metric: code })} />}
          <WindowPicker name="compare-window" value={windowValue} onChange={(v) => set({ since: v })} />
        </FilterRow>
      </div>

      <section className={`card ${styles.chartCard} reveal`} style={{ '--i': 2 } as React.CSSProperties} aria-label="Comparison chart">
        {selected.length < 2 ? (
          <EmptyState title="Pick at least two countries">Use the search above, or start from a country page.</EmptyState>
        ) : metrics.isError ? (
          <ErrorState error={metrics.error} onRetry={() => metrics.refetch()} />
        ) : comparison.isPending ? (
          <LoadingBlock label="Loading comparison" height={340} />
        ) : comparison.isError ? (
          <ErrorState error={comparison.error} title="The comparison could not load" onRetry={() => comparison.refetch()} />
        ) : (
          <>
            <h2 className={styles.chartTitle}>
              {metric?.name ?? metricCode} <span>{metric?.unit}</span>
            </h2>
            <LineChart
              series={series}
              unit={metric?.unit ?? ''}
              height={360}
              zeroBaseline={metricCode !== 'temperature_anomaly_c'}
              title={`${metric?.name ?? metricCode} for ${series.map((s) => s.label).join(', ')}`}
              stale={comparison.isPlaceholderData}
            />
            <ul className={styles.latest} aria-label="Latest values">
              {series.map((s) => {
                const last = s.points.at(-1)
                return (
                  <li key={s.key}>
                    <span className={styles.swatch} style={{ background: s.color }} aria-hidden="true" />
                    <strong>{last ? formatValue(last.value, metric?.unit ?? '') : 'No data'}</strong>
                    <span>
                      {s.label}
                      {last && ` · ${last.year}`}
                    </span>
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </section>
    </div>
  )
}
