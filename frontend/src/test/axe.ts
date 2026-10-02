import axe from 'axe-core'
import { expect } from 'vitest'

/**
 * Runs axe-core on a rendered container and fails with each violation's id and target.
 * color-contrast is skipped because jsdom does not compute styles; token contrast is checked separately
 * (MEASUREMENTS.md, Phase 3).
 */
export async function expectNoAxeViolations(container: Element) {
  const results = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  const summary = results.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`)
  expect(summary).toEqual([])
}
