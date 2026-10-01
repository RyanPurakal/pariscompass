import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import type { CountryInfo } from '../api/client'
import { useCountries } from '../hooks/queries'
import styles from './CountrySearch.module.css'

const MAX_RESULTS = 8

interface Props {
  onSelect: (country: CountryInfo) => void
  label?: string
  placeholder?: string
  /** ISO3 codes to leave out (already chosen elsewhere). */
  exclude?: string[]
  disabled?: boolean
}

/**
 * WAI-ARIA combobox: type to filter by name or ISO3, arrows to move, Enter to choose, Escape to close.
 * Focus stays in the input; the active option is announced through aria-activedescendant.
 */
export function CountrySearch({ onSelect, label = 'Find a country', placeholder = 'Search countries', exclude = [], disabled }: Props) {
  const { data: countries = [], isError } = useCountries()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const id = useId()
  const listId = `${id}-list`

  const results = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    const excluded = new Set(exclude)
    return countries
      .filter((c) => !excluded.has(c.iso3))
      .filter((c) => c.name.toLowerCase().includes(q) || c.iso3.toLowerCase() === q)
      .sort((a, b) => Number(!a.name.toLowerCase().startsWith(q)) - Number(!b.name.toLowerCase().startsWith(q)))
      .slice(0, MAX_RESULTS)
  }, [countries, query, exclude])

  const choose = (c: CountryInfo) => {
    onSelect(c)
    setQuery('')
    setOpen(false)
    setActive(0)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((i) => Math.min(i + 1, results.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter') {
      const c = results[active]
      if (open && c) {
        e.preventDefault()
        choose(c)
      }
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const expanded = open && results.length > 0
  return (
    <div className={styles.root}>
      <label htmlFor={id} className="visually-hidden">
        {label}
      </label>
      <svg className={styles.icon} width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
        <circle cx="7" cy="7" r="5" fill="none" stroke="currentColor" strokeWidth="1.5" />
        <path d="M11 11 L14.5 14.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      </svg>
      <input
        ref={inputRef}
        id={id}
        className={styles.input}
        type="text"
        role="combobox"
        autoComplete="off"
        spellCheck={false}
        placeholder={isError ? 'Countries unavailable' : placeholder}
        disabled={disabled || isError}
        aria-expanded={expanded}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={expanded ? `${listId}-${active}` : undefined}
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
          setActive(0)
        }}
        onKeyDown={onKeyDown}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
      />
      <ul id={listId} role="listbox" className={styles.list} hidden={!expanded} aria-label={label}>
        {results.map((c, i) => (
          <li
            key={c.iso3}
            id={`${listId}-${i}`}
            role="option"
            aria-selected={i === active}
            className={styles.option}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => choose(c)}
            onMouseEnter={() => setActive(i)}
          >
            <span>{c.name}</span>
            <span className={styles.code}>{c.iso3}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
