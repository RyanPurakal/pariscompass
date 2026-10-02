import { describe, expect, it } from 'vitest'
import { assignSlots } from './slots'

describe('assignSlots', () => {
  it('assigns slots in selection order', () => {
    expect([...assignSlots(['USA', 'CHN', 'IND'], {})]).toEqual([['USA', 0], ['CHN', 1], ['IND', 2]])
  })

  it('keeps every remaining country on its slot when one is removed', () => {
    const before = assignSlots(['USA', 'CHN', 'IND', 'DEU'], {})
    const after = assignSlots(['USA', 'IND', 'DEU'], before)
    expect(after.get('IND')).toBe(2)
    expect(after.get('DEU')).toBe(3)
  })

  it('gives a newly added country the lowest free slot', () => {
    const after = assignSlots(['USA', 'IND', 'DEU', 'BRA'], { USA: 0, IND: 2, DEU: 3 })
    expect(after.get('BRA')).toBe(1)
  })
})
