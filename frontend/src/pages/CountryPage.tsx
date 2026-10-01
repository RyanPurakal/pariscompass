import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError, type MetricSeries } from '../api/client'
import { AlignmentPanel } from '../components/AlignmentPanel'
import { WINDOWS, WindowPicker, type WindowValue } from '../components/Filters'
import { LineChart, type LineSeries } from '../components/LineChart'
import { ProjectionPanel } from '../components/ProjectionPanel'
import { StatTile } from '../components/StatTile'
import { EmptyState, ErrorState, LoadingBlock } from '../components/States'
import { useCountryMetrics, useCountrySeries } from '../hooks/queries'
import { formatValue } from '../lib/format'
import { SERIES } from '../lib/palette'
import { useTheme } from '../lib/theme'
import styles from './CountryPage.module.css'

const CHART_METRICS = [
  'co2_total_mt',
  'co2_per_capita_t',
  'low_carbon_share_elec_pct',
  'renewables_share_elec_pct',
  'temperature_anomaly_c',
]

export default function CountryPage() {
  const iso3 = (useParams().iso3 ?? '').toUpperCase()
  const { theme } = useTheme()
  const [window, setWindow] = useState<WindowValue>('1990')
  const from = WINDOWS.find((w) => w.value === window)?.from

  const metrics = useCountryMetrics(iso3)
  const allSeries = useCountrySeries(iso3, CHART_METRICS)
  const windowed = useCountrySeries(iso3, CHART_METRICS, from)

  const byMetric = useMemo(() => index(windowed.data?.series), [windowed.data])
  const co2History = useMemo(() => index(allSeries.data?.series).get('co2_total_mt')?.points ?? [], [allSeries.data])

  if (metrics.isPending) return <LoadingBlock label="Loading country" height={480} />
  if (metrics.isError) {
    if (metrics.error instanceof ApiError && metrics.error.code === 'COUNTRY_NOT_FOUND') {
      return (
        <EmptyState title={`No country with code ${iso3}`}>
          <Link to="/">Back to the atlas</Link>
        </EmptyState>
      )
    }
    return <ErrorState error={metrics.error} title="This country could not load" onRetry={() => metrics.refetch()} />
  }

  const m = metrics.data
  const colors = SERIES[theme]
  const line = (code: string, label: string, color: string): LineSeries => ({
    key: code,
    label,
    color,
    points: byMetric.get(code)?.points ?? [],
  })

  return (
    <div className={styles.page}>
      <header className={`${styles.header} reveal`}>
        <p className="eyebrow">
          <Link to="/">Atlas</Link> / Country profile
        </p>
        <h1 className={styles.name}>
          {m.name} <span className={styles.code}>{m.iso3}</span>
        </h1>
        <Link className="button button-quiet" to={`/compare?countries=${m.iso3}`}>
          Compare with other countries
        </Link>
      </header>

      <section className={`${styles.tiles} reveal`} style={{ '--i': 1 } as React.CSSProperties} aria-label="Latest values">
        <StatTile label="CO2 per person" value={formatValue(m.co2PerCapita, 't CO2/person')} year={m.years.co2PerCapita} />
        <StatTile label="CO2 emissions" value={formatValue(m.co2TotalMt, 'Mt CO2')} year={m.years.co2TotalMt} />
        <StatTile label="Renewables share of electricity" value={formatValue(m.renewablesSharePct, '%')} year={m.years.renewablesSharePct} />
        <StatTile
          label="Temperature anomaly"
          value={formatValue(m.temperatureAnomalyC, '°C')}
          year={m.years.temperatureAnomalyC}
          note="vs 1991-2020"
        />
      </section>

      <div className={`${styles.split} reveal`} style={{ '--i': 2 } as React.CSSProperties}>
        <AlignmentPanel iso3={iso3} />
        <ProjectionPanel iso3={iso3} name={m.name} observed={co2History} />
      </div>

      <section className={`${styles.trends} reveal`} style={{ '--i': 3 } as React.CSSProperties} aria-labelledby="trends-title">
        <div className={styles.trendsHeader}>
          <div>
            <p className="eyebrow">History</p>
            <h2 id="trends-title" className={styles.sectionTitle}>
              Trends
            </h2>
          </div>
          <WindowPicker value={window} onChange={setWindow} />
        </div>

        {windowed.isPending ? (
          <LoadingBlock label="Loading trends" height={300} />
        ) : windowed.isError ? (
          <ErrorState error={windowed.error} title="Trends could not load" onRetry={() => windowed.refetch()} />
        ) : (
          <div className={styles.charts}>
            <ChartCard title="CO2 emissions" unit="Mt CO2">
              <LineChart series={[line('co2_total_mt', 'CO2 emissions', colors[0]!)]} unit="Mt CO2" title={`${m.name} CO2 emissions`} stale={windowed.isPlaceholderData} />
            </ChartCard>
            <ChartCard title="CO2 per person" unit="t CO2/person">
              <LineChart series={[line('co2_per_capita_t', 'CO2 per person', colors[0]!)]} unit="t CO2/person" title={`${m.name} CO2 per person`} stale={windowed.isPlaceholderData} />
            </ChartCard>
            <ChartCard title="Electricity mix" unit="% of generation">
              <LineChart
                series={[
                  line('low_carbon_share_elec_pct', 'Low-carbon', colors[0]!),
                  line('renewables_share_elec_pct', 'Renewables', colors[1]!),
                ]}
                unit="%"
                title={`${m.name} electricity mix`}
                stale={windowed.isPlaceholderData}
              />
            </ChartCard>
            <ChartCard title="Temperature anomaly" unit="°C vs 1991-2020">
              <LineChart
                series={[line('temperature_anomaly_c', 'Temperature anomaly', colors[0]!)]}
                unit="°C"
                zeroBaseline={false}
                title={`${m.name} temperature anomaly`}
                stale={windowed.isPlaceholderData}
              />
            </ChartCard>
          </div>
        )}
      </section>
    </div>
  )
}

function ChartCard({ title, unit, children }: { title: string; unit: string; children: React.ReactNode }) {
  return (
    <div className={`card ${styles.chartCard}`}>
      <h3 className={styles.chartTitle}>
        {title} <span>{unit}</span>
      </h3>
      {children}
    </div>
  )
}

function index(series: MetricSeries[] | undefined): Map<string, MetricSeries> {
  return new Map((series ?? []).map((s) => [s.metric, s]))
}
