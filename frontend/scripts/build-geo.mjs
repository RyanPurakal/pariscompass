// Builds public/geo/countries-110m.json, the country shapes for the choropleth.
// Source: Natural Earth 1:110m admin-0 countries (public domain).
// Run with `npm run geo`; the output is committed so builds never depend on the network.
//
// ISO codes come from ISO_A3_EH, not ISO_A3: ISO_A3 is "-99" for France and Norway.
// Territories without an ISO code (Kosovo, N. Cyprus, Somaliland) keep a null id and render as "no data".
import { writeFile, mkdir } from 'node:fs/promises'
import { topology } from 'topojson-server'

const SOURCE =
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson'
const OUTPUT = new URL('../public/geo/countries-110m.json', import.meta.url)
// Quantization trades precision for size; 1e4 is invisible at this map scale.
const QUANTIZATION = 1e4

const response = await fetch(SOURCE)
if (!response.ok) throw new Error(`GET ${SOURCE} returned ${response.status}`)
const source = await response.json()

const features = source.features.map((f) => {
  const iso = f.properties.ISO_A3_EH
  return {
    type: 'Feature',
    id: iso && iso !== '-99' ? iso : null,
    properties: { name: f.properties.NAME },
    geometry: f.geometry,
  }
})

const topo = topology({ countries: { type: 'FeatureCollection', features } }, QUANTIZATION)
await mkdir(new URL('.', OUTPUT), { recursive: true })
await writeFile(OUTPUT, JSON.stringify(topo))

const withoutIso = features.filter((f) => f.id === null).map((f) => f.properties.name)
console.log(`Wrote ${features.length} countries (${withoutIso.length} without ISO code: ${withoutIso.join(', ')})`)
