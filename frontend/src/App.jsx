// Entry point for the entire React UI. See src/README.md for the component and data-flow overview.
// API_BASE and countryCoordinates are the two external dependencies — change the base URL here for staging/production.
import { useState, useEffect, useMemo, useRef } from 'react'
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import './App.css'
import { countryCoordinates } from './countryCoordinates'

// Fix for default marker icons in Leaflet with Vite
import icon from 'leaflet/dist/images/marker-icon.png'
import iconShadow from 'leaflet/dist/images/marker-shadow.png'
import iconRetina from 'leaflet/dist/images/marker-icon-2x.png'

// Fix marker icons for Vite
if (L.Icon.Default.prototype._getIconUrl) {
  delete L.Icon.Default.prototype._getIconUrl
}
L.Icon.Default.mergeOptions({
  iconUrl: icon,
  iconRetinaUrl: iconRetina,
  shadowUrl: iconShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  tooltipAnchor: [16, -28],
  shadowSize: [41, 41]
})

const API_BASE = 'http://localhost:8081/api'

// Component to center map on country
function MapCenter({ center, zoom }) {
  const map = useMap()
  const prevCenter = useRef(null)
  
  useEffect(() => {
    if (center && Array.isArray(center) && center.length === 2) {
      const centerKey = `${center[0]},${center[1]}`
      // Only animate if center actually changed
      if (prevCenter.current !== centerKey) {
        try {
          map.setView(center, zoom || 5, { 
            animate: true, 
            duration: 0.6
          })
          prevCenter.current = centerKey
        } catch (e) {
          console.warn('Map center error:', e)
        }
      }
    }
  }, [map, center, zoom])
  return null
}

// Loading skeleton component
function LoadingSkeleton({ countryName }) {
  return (
    <div className="data-card loading-skeleton">
      <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <div className="skeleton-line" style={{ width: '60%', height: '2rem', margin: '0 auto 1rem' }}></div>
        <div className="skeleton-line" style={{ width: '40%', height: '1rem', margin: '0 auto' }}></div>
      </div>
      {countryName && (
        <div style={{ textAlign: 'center', marginBottom: '1.5rem', color: 'var(--text-secondary)' }}>
          <p>Loading climate data for <strong>{countryName}</strong>...</p>
          <p style={{ fontSize: '0.85rem', marginTop: '0.5rem', color: 'var(--text-muted)' }}>
            Generating AI projection (this may take 10-20 seconds)
          </p>
        </div>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
        {[1, 2, 3, 4].map(i => (
          <div key={i} style={{ padding: '1rem', background: 'var(--bg-tertiary)', borderRadius: '10px', border: '1px solid var(--border)' }}>
            <div className="skeleton-line short" style={{ marginBottom: '0.5rem', height: '0.7rem' }}></div>
            <div className="skeleton-line medium" style={{ height: '1.2rem' }}></div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: '2rem', padding: '1.5rem', background: 'var(--bg-tertiary)', borderRadius: '10px', border: '1px solid var(--border)' }}>
        <div className="skeleton-line" style={{ marginBottom: '0.5rem' }}></div>
        <div className="skeleton-line" style={{ marginBottom: '0.5rem' }}></div>
        <div className="skeleton-line short"></div>
      </div>
    </div>
  )
}

// Metric card with icon and color coding
function MetricCard({ icon, label, value, status = 'neutral' }) {
  return (
    <div className={`metric-card ${status}`}>
      <div className="metric-icon">{icon}</div>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  )
}

// Get metric status based on value
function getMetricStatus(metricType, value) {
  if (metricType === 'co2PerCapita') {
    if (value < 5) return 'good'
    if (value < 10) return 'warning'
    return 'danger'
  }
  if (metricType === 'renewables') {
    if (value > 40) return 'good'
    if (value > 20) return 'warning'
    return 'danger'
  }
  if (metricType === 'temp') {
    if (value < 0.8) return 'good'
    if (value < 1.2) return 'warning'
    return 'danger'
  }
  return 'neutral'
}

// Extract risk level from projection text
function extractRiskLevel(text) {
  if (!text) return null
  const upper = text.toUpperCase()
  
  // Look for explicit risk statements first
  if (upper.includes('**HIGH**') || upper.includes('HIGH RISK') || 
      (upper.includes('HIGH') && upper.includes('RISK'))) return 'high'
  if (upper.includes('**MEDIUM**') || upper.includes('MEDIUM RISK') || 
      (upper.includes('MEDIUM') && upper.includes('RISK'))) return 'medium'
  if (upper.includes('**LOW**') || upper.includes('LOW RISK') || 
      (upper.includes('LOW') && upper.includes('RISK'))) return 'low'
  
  // Look for risk level mentions in context
  const riskPattern = /(?:RISK LEVEL|ALIGNMENT RISK|RISK)[:\s]+(LOW|MEDIUM|HIGH)/i
  const match = text.match(riskPattern)
  if (match) {
    const level = match[1].toLowerCase()
    if (['low', 'medium', 'high'].includes(level)) return level
  }
  
  // Fallback: look for standalone risk words
  if (upper.includes('HIGH') && !upper.includes('HIGHER') && !upper.includes('HIGHLY')) return 'high'
  if (upper.includes('LOW')) return 'low'
  
  return 'medium' // default
}

// Format large numbers
function formatNumber(num) {
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toFixed(1)
}

function App() {
  const [countries, setCountries] = useState([])
  const [selectedCountry, setSelectedCountry] = useState(null)
  const [metrics, setMetrics] = useState(null)
  const [projection, setProjection] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [mapCenter, setMapCenter] = useState([20, 0])
  const [mapZoom, setMapZoom] = useState(2)
  const mapRef = useRef(null)

  useEffect(() => {
    fetchCountries()
  }, [])

  // Debug: Log when countries load
  useEffect(() => {
    if (countries.length > 0) {
      console.log(`✅ Loaded ${countries.length} countries`)
    }
  }, [countries])

  const fetchCountries = async () => {
    try {
      const response = await fetch(`${API_BASE}/countries`)
      const data = await response.json()
      setCountries(data)
    } catch (err) {
      console.error('Error fetching countries:', err)
      setError('Failed to load countries list')
    }
  }

  const handleCountrySelect = async (iso3) => {
    if (!iso3) {
      console.warn('No ISO3 provided')
      return
    }
    
    console.log('🌍 Selecting country:', iso3)
    setLoading(true)
    setError(null)
    setMetrics(null)
    setProjection(null)
    setSelectedCountry(iso3)

    // Pan map to country immediately for better UX
    const coords = countryCoordinates[iso3]
    if (coords && Array.isArray(coords) && coords.length === 2) {
      setMapCenter(coords)
      setMapZoom(5)
    }

    try {
      console.log('📡 Fetching data for:', iso3)
      const startTime = Date.now()
      
      // Add timeout
      const controller = new AbortController()
      const timeoutId = setTimeout(() => controller.abort(), 30000) // 30 second timeout
      
      const response = await fetch(`${API_BASE}/country/${iso3}/projection`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        signal: controller.signal
      })

      clearTimeout(timeoutId)

      if (!response.ok) {
        const errorText = await response.text()
        console.error('❌ API error:', response.status, errorText)
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const data = await response.json()
      const duration = ((Date.now() - startTime) / 1000).toFixed(2)
      console.log(`✅ Data received in ${duration}s:`, data)
      
      setMetrics(data.metrics)
      setProjection(data.projection)
    } catch (err) {
      console.error('❌ Error fetching country data:', err)
      if (err.name === 'AbortError') {
        setError(`Request timed out for ${iso3}. The AI projection may take longer to generate.`)
      } else {
        setError(`Failed to load data for ${iso3}. ${err.message}`)
      }
    } finally {
      setLoading(false)
    }
  }

  // Filter countries based on search
  const filteredCountries = useMemo(() => {
    if (!searchQuery) return countries
    const query = searchQuery.toLowerCase()
    return countries.filter(country => 
      country.name.toLowerCase().includes(query) ||
      country.iso3.toLowerCase().includes(query)
    )
  }, [countries, searchQuery])

  const selectedCoords = selectedCountry ? countryCoordinates[selectedCountry] : null
  const riskLevel = projection ? extractRiskLevel(projection.projection) : null

  return (
    <div className="app-container">
      {/* Minimal header */}
      <header style={{ 
        height: '60px', 
        borderBottom: '1px solid var(--border)',
        display: 'flex',
        alignItems: 'center',
        padding: '0 var(--spacing-xl)',
        background: 'var(--bg-secondary)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-sm)' }}>
          <span style={{ fontSize: '1.5rem' }}>🧭</span>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 600, letterSpacing: '-0.01em' }}>
            Paris Compass
          </h1>
        </div>
      </header>

      <div className="main-content">
        {/* Left Navigation Panel */}
        <div className="nav-panel">
          <div className="app-title">
            <div className="compass-icon">🧭</div>
            <h1>Paris Compass</h1>
          </div>

          <div className="search-container">
            <input
              type="text"
              placeholder="Search countries..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <div className="country-list">
            {filteredCountries.slice(0, 50).map(country => (
              <div
                key={country.iso3}
                className={`country-list-item ${selectedCountry === country.iso3 ? 'active' : ''}`}
                onClick={() => handleCountrySelect(country.iso3)}
              >
                {country.name}
              </div>
            ))}
            {filteredCountries.length === 0 && (
              <div style={{ padding: 'var(--spacing-md)', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                No countries found
              </div>
            )}
          </div>

          <div className="legend">
            <h3>Legend</h3>
            <div className="legend-item">
              <div className="legend-color" style={{ background: 'var(--green)' }}></div>
              <span>Low Risk / Good Performance</span>
            </div>
            <div className="legend-item">
              <div className="legend-color" style={{ background: 'var(--yellow)' }}></div>
              <span>Medium Risk / Moderate</span>
            </div>
            <div className="legend-item">
              <div className="legend-color" style={{ background: 'var(--red)' }}></div>
              <span>High Risk / Concerning</span>
            </div>
          </div>
        </div>

        {/* Center Map Panel */}
        <div className="map-panel" id="map-panel">
          {countries.length > 0 ? (
            <MapContainer
              center={[20, 0]}
              zoom={2}
              style={{ 
                height: 'calc(100vh - 60px)', 
                width: '100%', 
                minHeight: '600px'
              }}
              scrollWheelZoom={true}
              zoomControl={true}
              whenCreated={(mapInstance) => {
                mapRef.current = mapInstance
                console.log('🗺️ Map created')
                // Invalidate size after render to fix any sizing issues
                setTimeout(() => {
                  try {
                    mapInstance.invalidateSize()
                    console.log('✅ Map size invalidated - should be visible now')
                  } catch (e) {
                    console.error('❌ Map error:', e)
                  }
                }, 300)
              }}
            >
              {selectedCountry && countryCoordinates[selectedCountry] && (
                <MapCenter 
                  center={countryCoordinates[selectedCountry]} 
                  zoom={5} 
                />
              )}
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors | <a href="https://carto.com/attributions">CARTO</a>'
                url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
                opacity={0.95}
                maxZoom={18}
                minZoom={2}
              />
              {countries.map(country => {
                const coords = countryCoordinates[country.iso3]
                if (!coords || !Array.isArray(coords) || coords.length !== 2) return null
                
                return (
                  <Marker
                    key={country.iso3}
                    position={coords}
                    eventHandlers={{
                      click: () => {
                        console.log('📍 Marker clicked:', country.iso3, country.name)
                        handleCountrySelect(country.iso3)
                      }
                    }}
                  >
                    <Popup>
                      <div 
                        style={{ 
                          cursor: 'pointer', 
                          textAlign: 'center', 
                          color: '#fff',
                          minWidth: '120px'
                        }} 
                        onClick={() => {
                          console.log('💬 Popup clicked:', country.iso3, country.name)
                          handleCountrySelect(country.iso3)
                        }}
                      >
                        <strong style={{ 
                          fontSize: '1rem', 
                          color: '#fff', 
                          display: 'block', 
                          marginBottom: '0.25rem' 
                        }}>
                          {country.name}
                        </strong>
                        <small style={{ color: 'rgba(255,255,255,0.7)' }}>
                          Click to view data
                        </small>
                      </div>
                    </Popup>
                  </Marker>
                )
              })}
            </MapContainer>
          ) : (
            <div style={{ 
              height: '100%', 
              minHeight: '600px',
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center', 
              background: 'var(--bg-tertiary)',
              color: 'var(--text-muted)'
            }}>
              <p>Loading countries...</p>
            </div>
          )}
        </div>

        {/* Right Data Panel */}
        <div className="data-panel">
          <div className="data-panel-content">
            {loading && <LoadingSkeleton countryName={selectedCountry ? countries.find(c => c.iso3 === selectedCountry)?.name : null} />}

            {error && (
              <div className="error-state">
                <div className="error-state-icon">⚠️</div>
                <h3>Error Loading Data</h3>
                <p>{error}</p>
              </div>
            )}

            {!loading && !error && metrics && (
              <>
                <div className="data-card">
                  <div className="country-header">
                    <div className="country-name">{metrics.name}</div>
                    <div className="country-code">{metrics.iso3}</div>
                  </div>

                  <div className="metrics-grid">
                    {metrics.co2PerCapita != null && (
                      <MetricCard
                        icon="💨"
                        label="CO₂ per Capita"
                        value={`${metrics.co2PerCapita.toFixed(1)} t`}
                        status={getMetricStatus('co2PerCapita', metrics.co2PerCapita)}
                      />
                    )}
                    {metrics.co2TotalMt != null && (
                      <MetricCard
                        icon="🌍"
                        label="Total CO₂"
                        value={`${formatNumber(metrics.co2TotalMt)} MT`}
                        status="neutral"
                      />
                    )}
                    {metrics.renewablesSharePct != null && (
                      <MetricCard
                        icon="🌱"
                        label="Renewable Energy"
                        value={`${metrics.renewablesSharePct.toFixed(1)}%`}
                        status={getMetricStatus('renewables', metrics.renewablesSharePct)}
                      />
                    )}
                    {metrics.temperatureAnomalyC != null && (
                      <MetricCard
                        icon="🌡️"
                        label="Temp. Anomaly"
                        value={`+${metrics.temperatureAnomalyC.toFixed(1)}°C`}
                        status={getMetricStatus('temp', metrics.temperatureAnomalyC)}
                      />
                    )}
                  </div>

                  {metrics.source && (
                    <div className="sources">
                      <strong>Data Sources:</strong>
                      <div>
                        CO₂: {metrics.source.co2} | 
                        Temperature: {metrics.source.temp} | 
                        Renewables: {metrics.source.renewables}
                      </div>
                    </div>
                  )}
                </div>

                {projection && (
                  <div className="projection-card">
                    <div className="projection-header">
                      <h3>AI PROJECTION</h3>
                      <span className="ai-badge">5-YEAR OUTLOOK</span>
                    </div>
                    
                    {riskLevel && (
                      <div className={`risk-badge ${riskLevel}`}>
                        {riskLevel.toUpperCase()} PARIS AGREEMENT RISK
                      </div>
                    )}
                    
                    <div className="projection-meta">
                      Model: {projection.model} • Generated: {new Date(projection.generatedAt).toLocaleString()}
                    </div>
                    
                    <div className="projection-text">
                      {projection.projection.split('\n').map((para, i) => (
                        para.trim() && <p key={i}>{para.trim()}</p>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}

            {!loading && !error && !metrics && (
              <div className="empty-state">
                <div className="empty-state-icon">🧭</div>
                <h3>Select a Country</h3>
                <p>
                  Click on a country marker on the map or select from the list to explore its climate trajectory and AI-powered 5-year projections.
                </p>
                <p style={{ marginTop: 'var(--spacing-md)', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  {countries.length} countries available
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default App
