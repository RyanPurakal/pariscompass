import { NavLink, Outlet, ScrollRestoration, useNavigate } from 'react-router'
import { CompassMark } from './CompassMark'
import { CountrySearch } from './CountrySearch'
import { ThemeToggle } from './ThemeToggle'
import styles from './AppShell.module.css'

export function AppShell() {
  const navigate = useNavigate()
  return (
    <div className={styles.shell}>
      <a href="#main" className={styles.skip}>
        Skip to content
      </a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <NavLink to="/" className={styles.brand} aria-label="Paris Compass home">
            <CompassMark />
            <span className={styles.wordmark}>
              Paris <em>Compass</em>
            </span>
          </NavLink>
          <nav aria-label="Main" className={styles.nav}>
            <NavLink to="/" end className={({ isActive }) => (isActive ? styles.active : undefined)}>
              Atlas
            </NavLink>
            <NavLink to="/compare" className={({ isActive }) => (isActive ? styles.active : undefined)}>
              Compare
            </NavLink>
          </nav>
          <div className={styles.search}>
            <CountrySearch onSelect={(c) => navigate(`/country/${c.iso3}`)} />
          </div>
          <ThemeToggle />
        </div>
      </header>

      <main id="main" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>

      <footer className={styles.footer}>
        <p>
          Data:{' '}
          <a href="https://github.com/owid/co2-data">Our World in Data</a> (Global Carbon Project, Ember, Energy
          Institute), <a href="https://ourworldindata.org/grapher/annual-temperature-anomalies">Copernicus ERA5</a>{' '}
          via Our World in Data, CC BY 4.0. Boundaries: <a href="https://www.naturalearthdata.com/">Natural Earth</a>.
        </p>
        <p>
          The alignment score is an indicator built from historical data, not an official Paris Agreement rating.{' '}
          <a href="https://github.com/RyanPurakal/pariscompass/blob/main/SCORING.md">How it is calculated</a>.
        </p>
      </footer>
      <ScrollRestoration />
    </div>
  )
}
