/** The brand mark: a compass rose whose north needle is the accent color. Decorative. */
export function CompassMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <circle cx="16" cy="16" r="14.5" fill="none" stroke="currentColor" strokeWidth="1" opacity="0.5" />
      <circle cx="16" cy="16" r="10" fill="none" stroke="currentColor" strokeWidth="0.75" opacity="0.3" />
      <path d="M16 3 L19 16 L16 14.5 L13 16 Z" fill="var(--accent)" />
      <path d="M16 29 L13 16 L16 17.5 L19 16 Z" fill="currentColor" opacity="0.75" />
      <path d="M3 16 L16 13.6 L16 18.4 Z" fill="currentColor" opacity="0.25" />
      <path d="M29 16 L16 18.4 L16 13.6 Z" fill="currentColor" opacity="0.25" />
    </svg>
  )
}
