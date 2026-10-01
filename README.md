# 🌍 Paris Compass

A full-stack web application that provides country-specific climate metrics and AI-powered 5-year projections using Google Gemini. Visualize climate data on an interactive world map and get insights into CO2 emissions, renewable energy adoption, temperature anomalies, and Paris Agreement alignment risks.

## Features

- **Interactive World Map**: Click on countries or select from dropdown to view climate metrics
- **Real Climate Data**: CO2 emissions, renewable energy share, temperature anomalies from real datasets
- **AI Projections**: 5-year climate projections using Google Gemini AI
- **Clean UI**: Modern, responsive interface with intuitive layout

## Tech Stack

### Backend
- **Spring Boot 3.5.6** - Java 21
- **Google GenAI** - Gemini AI integration
- **OpenCSV** - CSV data loading
- **In-memory data storage** - No database required

### Frontend
- **React 19** - UI framework
- **Vite** - Build tool
- **Leaflet** - Interactive maps
- **React Leaflet** - React bindings for Leaflet

## Prerequisites

- Java 21 or higher
- Node.js 18+ and npm
- Google Gemini API key ([Get one here](https://makersuite.google.com/app/apikey))

## Quick Start

```bash
# 1. Set your Gemini API key
export GEMINI_API_KEY="your_gemini_api_key_here"

# 2. Run backend (in project root)
./mvnw spring-boot:run

# 3. Run frontend (in new terminal)
cd frontend
npm install
npm run dev

# 4. Open http://localhost:5173 in your browser
```

## Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd pariscompass
```

### 2. Set Environment Variable

**Before running the backend**, set your Gemini API key:

```bash
export GEMINI_API_KEY="your_gemini_api_key_here"
```

On Windows (PowerShell):
```powershell
$env:GEMINI_API_KEY="your_gemini_api_key_here"
```

On Windows (CMD):
```cmd
set GEMINI_API_KEY=your_gemini_api_key_here
```

**Note:** You can also use the helper script:
```bash
./run-backend.sh
```

### 3. Run Backend

```bash
./mvnw spring-boot:run
```

Or use the helper script:
```bash
./run-backend.sh
```

The backend will start on `http://localhost:8081`

### 4. Run Frontend

Open a new terminal window:

```bash
cd frontend
npm install
npm run dev
```

The frontend will start on `http://localhost:5173`

## API Endpoints

### `GET /api/countries`
Returns list of all supported countries.

**Response:**
```json
[
  {
    "iso3": "USA",
    "name": "United States"
  },
  {
    "iso3": "IND",
    "name": "India"
  }
]
```

### `GET /api/country/{iso3}`
Returns climate metrics for a specific country.

**Example:** `GET /api/country/USA`

**Response:**
```json
{
  "iso3": "USA",
  "name": "United States",
  "year": 2022,
  "co2PerCapita": 14.2,
  "co2TotalMt": 4713.0,
  "temperatureAnomalyC": 1.2,
  "renewablesSharePct": 21.5,
  "source": {
    "co2": "Our World in Data",
    "temp": "Berkeley Earth",
    "renewables": "OWID"
  }
}
```

### `POST /api/country/{iso3}/projection`
Generates AI projection for a country (combines metrics + projection).

**Example:** `POST /api/country/USA/projection`

**Response:**
```json
{
  "metrics": {
    "iso3": "USA",
    "name": "United States",
    "year": 2022,
    "co2PerCapita": 14.2,
    "co2TotalMt": 4713.0,
    "temperatureAnomalyC": 1.2,
    "renewablesSharePct": 21.5,
    "source": {
      "co2": "Our World in Data",
      "temp": "Berkeley Earth",
      "renewables": "OWID"
    }
  },
  "projection": {
    "country": "United States",
    "projection": "Based on current trends, the United States is projected to...",
    "model": "gemini-2.5-flash",
    "generatedAt": "2024-01-15T10:30:00Z"
  }
}
```

### `GET /actuator/health`
Spring Boot Actuator health endpoint.

## Data Sources

The application uses CSV files located in `src/main/resources/data/`:

- **co2_data.csv**: CO2 emissions data (Our World in Data)
- **renewables_data.csv**: Renewable energy share data (OWID)
- **temperature_data.csv**: Temperature anomaly data (Berkeley Earth)
- **countries.csv**: Country ISO3 codes and names

Data is loaded into memory at application startup for fast access.

## Project Structure

```
paris-compass/
├── src/                              # Spring Boot backend (Java 21)
│   ├── main/
│   │   ├── java/com/ryanpurakal/pariscompass/
│   │   │   ├── ParisCompassApplication.java # Entry point — boots Spring context
│   │   │   ├── config/               # Bean wiring: Gemini client, CORS rules
│   │   │   ├── controller/           # HTTP layer — maps URLs to services
│   │   │   ├── model/                # DTOs shared between controller & service
│   │   │   └── service/              # Core logic: data loading, metrics, AI calls
│   │   └── resources/
│   │       ├── data/                 # CSV datasets loaded at startup
│   │       └── application.properties
│   └── test/                         # Unit tests (JUnit 5 + Mockito)
├── frontend/                         # React 19 + Vite SPA
│   ├── src/
│   │   ├── App.jsx                   # Full UI: map, country list, data panel
│   │   ├── countryCoordinates.js     # ISO3 → [lat, lng] lookup table
│   │   ├── main.jsx                  # React entry point
│   │   └── App.css / index.css       # Global styles and component styles
│   └── package.json
└── pom.xml                           # Maven build configuration
```

## Architecture & Data Flow

```
User clicks country
        │
        ▼
  React (App.jsx)
  POST /api/country/{iso3}/projection
        │
        ▼
  CountryController          ← HTTP boundary: validates iso3, composes response
        │
        ├──► CountryMetricsService   ← looks up latest CSV data for the country
        │         │
        │         └──► DataLoader    ← in-memory maps loaded from CSV at startup
        │
        └──► GeminiService           ← calls Gemini API; caches result 1 hr per country
                  │
                  └──► Google Gemini API (external)
```

**Key design choices:**
- All CSV data is loaded into memory at startup — no database, no per-request I/O.
- Gemini projections are cached per country for 1 hour to avoid redundant API calls.
- CORS origins come from `CORS_ALLOWED_ORIGINS` (dev default `http://localhost:5173`).

## Configuration

Settings live in `src/main/resources/application.yml` with per-profile overrides (`application-dev.yml`, `application-test.yml`, `application-prod.yml`). Every secret or deployment-specific value is an environment variable; see `.env.example` and `frontend/.env.example`.

| Variable | Default | Notes |
|----------|---------|-------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`, `test` or `prod` |
| `GEMINI_API_KEY` | none | Optional in dev (projections return 503), required in prod |
| `GEMINI_MODEL` | `gemini-2.5-flash` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` in dev | Comma-separated; required in prod, `*` rejected |
| `PORT` | `8081` | |
| `VITE_API_BASE_URL` (frontend) | `http://localhost:8081/api` | Baked into the JS bundle at build time |

Prod refuses to start if a required variable is missing, and reports all of them at once.

## Testing

Run backend tests:

```bash
./mvnw test
```

Run frontend tests (if configured):

```bash
cd frontend
npm test
```

## Development Notes

### Adding New Countries

1. Add country to `src/main/resources/data/countries.csv`
2. Add corresponding data rows to:
   - `co2_data.csv`
   - `renewables_data.csv`
   - `temperature_data.csv`

### CORS Configuration

Allowed origins come from `CORS_ALLOWED_ORIGINS` (comma-separated). Dev defaults to `http://localhost:5173`.

### Error Handling

Every error uses the RFC 9457 `application/problem+json` shape:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No country found with ISO3 code 'XXX'",
  "instance": "/api/country/XXX",
  "code": "COUNTRY_NOT_FOUND",
  "timestamp": "2026-10-01T19:27:51Z"
}
```

| Status | `code` | When |
|--------|--------|------|
| 400 | `BAD_REQUEST` | ISO3 path variable is not three letters |
| 404 | `COUNTRY_NOT_FOUND` | ISO3 is well formed but not in the dataset |
| 404 / 405 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` | Unknown route or wrong HTTP method |
| 503 | `PROJECTION_UNAVAILABLE` | Gemini is not configured or the upstream call failed |
| 500 | `INTERNAL_ERROR` | Anything unexpected; details are logged, never returned |

Missing data fields return `null` in JSON responses.

## Troubleshooting

### Backend won't start
- Ensure Java 21 is installed: `java -version`
- Check if port 8081 is available
- In prod, `GEMINI_API_KEY` and `CORS_ALLOWED_ORIGINS` must be set (dev runs without them)

### Frontend can't connect to backend
- Ensure backend is running on port 8081
- Check CORS configuration matches frontend URL
- Verify `VITE_API_BASE_URL` points at the backend (default `http://localhost:8081/api`)

### No data showing
- Check CSV files exist in `src/main/resources/data/`
- Verify CSV format matches expected structure
- Check application logs for data loading errors

### Gemini API errors
- Verify `GEMINI_API_KEY` is set correctly
- Check API key is valid and has quota
- Review application logs for detailed error messages

## License

This project is open source and available for educational purposes.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Acknowledgments

- Climate data sources: Our World in Data, Berkeley Earth
- Mapping: Leaflet and OpenStreetMap
- AI: Google Gemini

