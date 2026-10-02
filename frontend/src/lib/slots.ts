/**
 * Keeps each country's existing color slot and gives new countries the lowest free one.
 * Pure, so it can run during render; the previous assignment is passed in.
 */
export function assignSlots(selected: string[], previous: Record<string, number> | Map<string, number>): Map<string, number> {
  const prev = previous instanceof Map ? previous : new Map(Object.entries(previous))
  const result = new Map<string, number>()
  for (const iso of selected) {
    const slot = prev.get(iso)
    if (slot !== undefined) result.set(iso, slot)
  }
  for (const iso of selected) {
    if (!result.has(iso)) {
      const used = new Set(result.values())
      result.set(iso, [0, 1, 2, 3].find((s) => !used.has(s)) ?? 0)
    }
  }
  return result
}
