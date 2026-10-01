import { useTheme, type ThemePreference } from '../lib/theme'
import styles from './ThemeToggle.module.css'

const OPTIONS: { value: ThemePreference; label: string }[] = [
  { value: 'system', label: 'Auto' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

/** A real radio group, so arrow keys move between options and screen readers announce the choice. */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme()
  return (
    <fieldset className={styles.group}>
      <legend className="visually-hidden">Color theme</legend>
      {OPTIONS.map((o) => (
        <label key={o.value} className={styles.option}>
          <input
            type="radio"
            name="theme"
            value={o.value}
            checked={preference === o.value}
            onChange={() => setPreference(o.value)}
          />
          <span>{o.label}</span>
        </label>
      ))}
    </fieldset>
  )
}
