import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import type { RankingEntry } from '../api/client'
import { formatPlain } from '../lib/format'
import styles from './RankingTable.module.css'

type SortKey = 'rank' | 'name'

interface Props {
  rows: RankingEntry[]
  unit: string
  caption: string
  highlighted?: string | null
  onHighlight?: (iso3: string | null) => void
}

/** The map's table twin: every value is readable without color or hover. Sortable, keyboard-reachable. */
export function RankingTable({ rows, unit, caption, highlighted, onHighlight }: Props) {
  const [sort, setSort] = useState<{ key: SortKey; dir: 'asc' | 'desc' }>({ key: 'rank', dir: 'asc' })

  const sorted = useMemo(() => {
    const copy = [...rows]
    copy.sort((a, b) => (sort.key === 'rank' ? a.rank - b.rank || a.name.localeCompare(b.name) : a.name.localeCompare(b.name)))
    return sort.dir === 'asc' ? copy : copy.reverse()
  }, [rows, sort])

  const toggle = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }))
  const ariaSort = (key: SortKey) => (sort.key === key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none')

  return (
    <div className={styles.scroller} tabIndex={0} role="region" aria-label={caption}>
      <table className={styles.table}>
        <caption className="visually-hidden">{caption}</caption>
        <thead>
          <tr>
            <th scope="col" aria-sort={ariaSort('rank')} className={styles.num}>
              <button type="button" onClick={() => toggle('rank')}>
                Rank
              </button>
            </th>
            <th scope="col" aria-sort={ariaSort('name')}>
              <button type="button" onClick={() => toggle('name')}>
                Country
              </button>
            </th>
            <th scope="col" className={styles.num}>
              {unit}
            </th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((r) => (
            <tr
              key={r.iso3}
              data-active={r.iso3 === highlighted || undefined}
              onPointerEnter={() => onHighlight?.(r.iso3)}
              onPointerLeave={() => onHighlight?.(null)}
            >
              <td className={`${styles.num} tabular`}>{r.rank}</td>
              <td>
                <Link to={`/country/${r.iso3}`} onFocus={() => onHighlight?.(r.iso3)}>
                  {r.name}
                </Link>
              </td>
              <td className={`${styles.num} tabular`}>{formatPlain(r.value, unit)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
