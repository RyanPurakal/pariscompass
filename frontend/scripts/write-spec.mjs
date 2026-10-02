// Writes the backend's OpenAPI spec to openapi.json with object keys sorted, so the file (and the
// types generated from it) only change when the API changes, never because of key order.
// Usage: node scripts/write-spec.mjs <url-or-file>
import { readFile, writeFile } from 'node:fs/promises'

const source = process.argv[2]
if (!source) throw new Error('usage: node scripts/write-spec.mjs <url-or-file>')

const raw = /^https?:\/\//.test(source)
  ? await fetch(source).then((r) => {
      if (!r.ok) throw new Error(`GET ${source} returned ${r.status}`)
      return r.text()
    })
  : await readFile(source, 'utf8')

const sortKeys = (v) =>
  Array.isArray(v)
    ? v.map(sortKeys)
    : v && typeof v === 'object'
      ? Object.fromEntries(Object.keys(v).sort().map((k) => [k, sortKeys(v[k])]))
      : v

await writeFile(new URL('../openapi.json', import.meta.url), JSON.stringify(sortKeys(JSON.parse(raw)), null, 2) + '\n')
console.log(`Wrote openapi.json from ${source}`)
