import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { FilterRow, MetricSelect, YearSlider } from '../components/Filters'
import { MapLegend } from '../components/MapLegend'
import { RankingTable } from '../components/RankingTable'
import { EmptyState, ErrorState, LoadingBlock } from '../components/States'
import { WorldMap } from '../components/WorldMap'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useMetrics, useRankings } from '../hooks/queries'
import { binningFor } from '../lib/scales'
import { useTheme } from '../lib/theme'
import styles from './ExplorePage.module.css'

const DEFAULT_METRIC = 'co2_per_capita_t'
const EARLIEST_SLIDER_YEAR = 1900

/** The atlas: a choropleth for one metric and year, with its ranking table. State lives in the URL. */
export function ExplorePage() {
  const navigate = useNavigate()
  const { theme } = useTheme()
  const [params, setParams] = useSearchParams()
  const metrics = useMetrics()
  const [highlighted, setHighlighted] = useState<string | null>(null)

  const metricCode = params.get('metric') ?? DEFAULT_METRIC
  const metric = metrics.data?.find((m) => m.code === metricCode) ?? metrics.data?.find((m) => m.code === DEFAULT_METRIC)
  const minYear = Math.max(metric?.coverage.firstYear ?? EARLIEST_SLIDER_YEAR, EARLIEST_SLIDER_YEAR)
  const maxYear = metric?.coverage.lastYear ?? new Date().getFullYear()
  const urlYear = Number(params.get('year')) || undefined
  const sliderYear = clamp(urlYear ?? metric?.coverage.defaultYear ?? maxYear, minYear, maxYear)
  // Only slider drags are debounced; the year from the URL or the metric's default is queried directly,
  // so no request goes out for a placeholder year while the metric catalog loads.
  const [dragYear, setDragYear] = useState<number | null>(null)
  const shownYear = dragYear ?? sliderYear
  const debouncedDrag = useDebouncedValue(dragYear, 250)
  const queryYear = dragYear === null ? sliderYear : (debouncedDrag ?? sliderYear)

  const rankings = useRankings(metric?.code, metric ? clamp(queryYear, minYear, maxYear) : undefined)
  const rows = useMemo(() => rankings.data?.entries ?? [], [rankings.data])
  const binning = useMemo(
    () => binningFor(metric?.code ?? '', rows.map((r) => r.value), theme),
    [metric?.code, rows, theme],
  )

  const update = (next: Record<string, string | undefined>) => {
    const p = new URLSearchParams(params)
    for (const [k, v] of Object.entries(next)) {
      if (v === undefined) p.delete(k)
      else p.set(k, v)
    }
    setParams(p, { replace: true })
  }

  if (metrics.isPending) return <LoadingBlock label="Loading metrics" height={480} />
  if (metrics.isError) return <ErrorState error={metrics.error} title="The atlas could not load" onRetry={() => metrics.refetch()} />
  if (!metric) return <EmptyState title="No metrics available">Run the ETL to load data.</EmptyState>

  const dataYear = rankings.data?.year ?? queryYear
  const total = rankings.data?.countriesWithData ?? 0

  return (
    <div className={styles.page}>
      <header className={`${styles.intro} reveal`}>
        <p className="eyebrow">01 · Atlas</p>
        <h1 className={styles.title}>
          {metric.name}, <span className={styles.year}>{dataYear}</span>
        </h1>
        <p className={styles.lead}>
          {metric.description} {total > 0 && <>{total} countries reported a value for {dataYear}.</>} Source:{' '}
          {metric.source.name}.
        </p>
      </header>

      <div className="reveal" style={{ '--i': 1 } as React.CSSProperties}>
        <FilterRow label="Map filters">
          <MetricSelect
            metrics={metrics.data}
            value={metric.code}
            onChange={(code) => update({ metric: code, year: undefined })}
          />
          <YearSlider
            min={minYear}
            max={maxYear}
            value={shownYear}
            onChange={(y) => {
              setDragYear(y)
              update({ year: String(y) })
            }}
          />
        </FilterRow>
      </div>

      <div className={styles.grid}>
        <section className={`card ${styles.mapCard} reveal`} style={{ '--i': 2 } as React.CSSProperties} aria-label="World map">
          {rankings.isError && !rankings.data ? (
            <ErrorState error={rankings.error} title="Map data could not load" onRetry={() => rankings.refetch()} />
          ) : (
            <>
              <WorldMap
                rows={rows}
                binning={binning}
                unit={metric.unit}
                metricName={metric.name}
                year={dataYear}
                total={total}
                stale={rankings.isPlaceholderData || rankings.isFetching}
                highlighted={highlighted}
                onHighlight={setHighlighted}
                onSelect={(iso3) => navigate(`/country/${iso3}`)}
              />
              <MapLegend binning={binning} unit={metric.unit} title={metric.name} />
            </>
          )}
        </section>

        <section className={`card ${styles.tableCard} reveal`} style={{ '--i': 3 } as React.CSSProperties} aria-labelledby="ranking-title">
          <h2 id="ranking-title" className={styles.tableTitle}>
            Ranking, {dataYear}
          </h2>
          {rankings.isPending ? (
            <LoadingBlock label="Loading ranking" height={400} />
          ) : rows.length === 0 ? (
            <EmptyState title={`No data for ${dataYear}`}>Try a later year.</EmptyState>
          ) : (
            <RankingTable
              rows={rows}
              unit={metric.unit}
              caption={`Countries ranked by ${metric.name}, ${dataYear}, highest first`}
              highlighted={highlighted}
              onHighlight={setHighlighted}
            />
          )}
        </section>
      </div>
    </div>
  )
}

function clamp(v: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, v))
}
