import { geoEqualEarth, geoPath } from 'd3-geo'
import type { Feature, FeatureCollection, Geometry } from 'geojson'
import { useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { feature } from 'topojson-client'
import type { GeometryCollection } from 'topojson-specification'
import type { RankingEntry } from '../api/client'
import { useWorldGeometry } from '../hooks/queries'
import { formatValue } from '../lib/format'
import type { Binning } from '../lib/scales'
import { ErrorState, LoadingBlock } from './States'
import styles from './WorldMap.module.css'

const WIDTH = 960
const HEIGHT = 470

interface CountryShape {
  iso3: string | null
  name: string
  d: string
  centroid: [number, number]
}

interface Props {
  rows: RankingEntry[]
  binning: Binning
  unit: string
  metricName: string
  year: number
  total: number
  /** True while a new year/metric loads: the old map stays, dimmed, instead of a loading flash. */
  stale?: boolean
  highlighted?: string | null
  onHighlight?: (iso3: string | null) => void
  onSelect: (iso3: string) => void
}

/**
 * Choropleth on an equal-area projection (Equal Earth). Mercator would inflate high-latitude countries
 * and make their colors dominate; equal-area keeps visual weight proportional to land area.
 *
 * Keyboard: the map is one tab stop. Arrow keys move between countries in alphabetical order,
 * Enter opens the focused one. The ranking table beside it carries the same data for screen readers.
 */
export function WorldMap({ rows, binning, unit, metricName, year, total, stale, highlighted, onHighlight, onSelect }: Props) {
  const geo = useWorldGeometry()
  const containerRef = useRef<HTMLDivElement>(null)
  const pathRefs = useRef(new Map<string, SVGPathElement>())
  const [active, setActive] = useState(0)
  /** Tooltip anchor in container pixels, set from pointer or focus events (never measured during render). */
  const [tip, setTip] = useState<{ x: number; y: number; flip: boolean } | null>(null)

  const shapes = useMemo<CountryShape[]>(() => {
    if (!geo.data) return []
    const topo = geo.data
    const collection = feature(topo, topo.objects.countries as GeometryCollection<{ name: string }>) as unknown as FeatureCollection<
      Geometry,
      { name: string }
    >
    const land = collection.features.filter((f) => f.id !== 'ATA')
    const projection = geoEqualEarth().fitExtent(
      [
        [6, 6],
        [WIDTH - 6, HEIGHT - 6],
      ],
      { type: 'FeatureCollection', features: land },
    )
    const path = geoPath(projection)
    return land
      .map((f: Feature<Geometry, { name: string }>) => ({
        iso3: typeof f.id === 'string' ? f.id : null,
        name: f.properties.name,
        d: path(f) ?? '',
        centroid: path.centroid(f) as [number, number],
      }))
      .sort((a, b) => a.name.localeCompare(b.name))
  }, [geo.data])

  const byIso = useMemo(() => new Map(rows.map((r) => [r.iso3, r])), [rows])
  const focusable = useMemo(() => shapes.filter((s) => s.iso3 !== null), [shapes])

  if (geo.isPending) return <LoadingBlock label="Loading map" height={360} />
  if (geo.isError) return <ErrorState error={geo.error} title="The map could not be drawn" onRetry={() => geo.refetch()} />

  const anchorAt = (x: number, y: number, width: number) => setTip({ x, y, flip: x > width * 0.7 })

  /** Keyboard focus has no pointer: anchor the tooltip at the country's centroid. */
  const anchorAtCentroid = (shape: CountryShape) => {
    const rect = containerRef.current?.getBoundingClientRect()
    if (!rect) return
    const [cx, cy] = shape.centroid
    anchorAt((cx / WIDTH) * rect.width, (cy / HEIGHT) * rect.height, rect.width)
  }

  const focusCountry = (index: number) => {
    const next = (index + focusable.length) % focusable.length
    setActive(next)
    const iso3 = focusable[next]?.iso3
    if (iso3) pathRefs.current.get(iso3)?.focus()
  }

  const onKeyDown = (e: KeyboardEvent<SVGSVGElement>) => {
    const keys: Record<string, number> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }
    if (e.key in keys) {
      e.preventDefault()
      focusCountry(active + keys[e.key]!)
    } else if (e.key === 'Home') {
      e.preventDefault()
      focusCountry(0)
    } else if (e.key === 'End') {
      e.preventDefault()
      focusCountry(focusable.length - 1)
    } else if (e.key === 'Enter' || e.key === ' ') {
      const iso3 = focusable[active]?.iso3
      if (iso3) {
        e.preventDefault()
        onSelect(iso3)
      }
    } else if (e.key === 'Escape') {
      onHighlight?.(null)
    }
  }

  const onPointerMove = (e: PointerEvent<SVGSVGElement>) => {
    const rect = containerRef.current?.getBoundingClientRect()
    if (rect) anchorAt(e.clientX - rect.left, e.clientY - rect.top, rect.width)
  }

  const highlightedShape = highlighted ? shapes.find((s) => s.iso3 === highlighted) : undefined
  const tooltipEntry = highlighted ? byIso.get(highlighted) : undefined

  return (
    <div ref={containerRef} className={styles.root} data-stale={stale || undefined}>
      <svg
        className={styles.svg}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="group"
        aria-label={`World map of ${metricName}, ${year}. ${total} countries have data. Use the arrow keys to move between countries and Enter to open one.`}
        onKeyDown={onKeyDown}
        onPointerMove={onPointerMove}
        onPointerLeave={() => {
          setTip(null)
          onHighlight?.(null)
        }}
      >
        <g>
          {shapes.map((s) => {
            const entry = s.iso3 ? byIso.get(s.iso3) : undefined
            const fill = binning.colorFor(entry?.value)
            if (!s.iso3) {
              return <path key={s.name} d={s.d} fill={binning.noData} className={styles.inert} aria-hidden="true" />
            }
            const index = focusable.findIndex((f) => f.iso3 === s.iso3)
            const label = entry
              ? `${s.name}: ${formatValue(entry.value, unit)}, rank ${entry.rank} of ${total}`
              : `${s.name}: no data for ${year}`
            return (
              <path
                key={s.iso3}
                ref={(el) => {
                  if (el) pathRefs.current.set(s.iso3!, el)
                  else pathRefs.current.delete(s.iso3!)
                }}
                d={s.d}
                fill={fill}
                className={styles.country}
                role="link"
                aria-label={label}
                tabIndex={index === active ? 0 : -1}
                onFocus={() => {
                  setActive(index)
                  anchorAtCentroid(s)
                  onHighlight?.(s.iso3)
                }}
                onPointerEnter={() => onHighlight?.(s.iso3)}
                onClick={() => onSelect(s.iso3!)}
              />
            )
          })}
        </g>
        {highlightedShape && <path d={highlightedShape.d} className={styles.outline} aria-hidden="true" />}
      </svg>

      {highlightedShape && tip && (
        <div className={styles.tooltip} style={{ left: tip.x, top: tip.y }} data-flip={tip.flip || undefined} aria-hidden="true">
          <strong className={styles.tooltipValue}>{tooltipEntry ? formatValue(tooltipEntry.value, unit) : 'No data'}</strong>
          <span className={styles.tooltipName}>{highlightedShape.name}</span>
          {tooltipEntry && (
            <span className={styles.tooltipMeta}>
              Rank {tooltipEntry.rank} of {total} · {year}
            </span>
          )}
        </div>
      )}
    </div>
  )
}
