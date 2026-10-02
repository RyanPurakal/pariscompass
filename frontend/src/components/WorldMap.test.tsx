import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { binningFor } from '../lib/scales'
import { ranking } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { WorldMap } from './WorldMap'

function renderMap(onSelect = vi.fn(), onHighlight = vi.fn()) {
  const binning = binningFor('co2_per_capita_t', ranking.entries.map((e) => e.value), 'light')
  const result = renderWithProviders(
    <WorldMap rows={ranking.entries} binning={binning} unit="t CO2/person" metricName="CO2 emissions per capita"
      year={2024} total={4} onSelect={onSelect} onHighlight={onHighlight} />,
  )
  return { ...result, onSelect, onHighlight, binning }
}

describe('WorldMap', () => {
  it('draws every country with an accessible label, colored by its class', async () => {
    const { binning } = renderMap()
    const usa = await screen.findByRole('link', { name: 'United States of America: 14.2 t CO2/person, rank 2 of 4' })
    expect(usa).toHaveAttribute('fill', binning.colorFor(14.197))
    expect(screen.getByRole('link', { name: 'France: no data for 2024' })).toHaveAttribute('fill', binning.noData)
    expect(screen.getByRole('group', { name: /World map of CO2 emissions per capita, 2024/ })).toBeInTheDocument()
  })

  it('is a single tab stop; arrow keys move alphabetically and Enter opens a country', async () => {
    const { onSelect } = renderMap()
    await screen.findAllByRole('link')
    const tabStops = document.querySelectorAll('path[tabindex="0"]')
    expect(tabStops).toHaveLength(1)

    const first = tabStops[0] as SVGPathElement
    first.focus()
    expect(first).toHaveAccessibleName(/^Afghanistan/)

    fireEvent.keyDown(first, { key: 'ArrowRight' })
    expect(document.activeElement).toHaveAccessibleName(/^Albania/)
    fireEvent.keyDown(document.activeElement!, { key: 'End' })
    expect(document.activeElement).toHaveAccessibleName(/^Zimbabwe/)
    fireEvent.keyDown(document.activeElement!, { key: 'Enter' })
    expect(onSelect).toHaveBeenCalledWith('ZWE')
  })

  it('highlights on hover and reports it, so the table can follow', async () => {
    const { onHighlight } = renderMap()
    fireEvent.pointerEnter(await screen.findByRole('link', { name: /^Qatar/ }))
    expect(onHighlight).toHaveBeenCalledWith('QAT')
  })

  it('shows the tooltip for the highlighted country', async () => {
    const binning = binningFor('co2_per_capita_t', [1], 'light')
    renderWithProviders(
      <WorldMap rows={ranking.entries} binning={binning} unit="t CO2/person" metricName="m" year={2024} total={4}
        highlighted="QAT" onSelect={vi.fn()} />,
    )
    const qatar = await screen.findByRole('link', { name: /^Qatar/ })
    fireEvent.focus(qatar)
    expect(await screen.findByText('41.3 t CO2/person')).toBeInTheDocument()
    expect(screen.getByText('Rank 1 of 4 · 2024')).toBeInTheDocument()
  })
})
