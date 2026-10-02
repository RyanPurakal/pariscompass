# Deployment

Production runs on three free plans:

| Part | Host | Why this one |
|---|---|---|
| API (Docker) | Render, free web service | Free Docker hosting. 512 MB RAM, 0.1 CPU, sleeps after 15 idle minutes |
| PostgreSQL | Neon, free plan | Free Postgres that does not expire (Render's free Postgres is deleted after 30 days). 1 GB per project; the data is about 40 MB |
| Frontend | Vercel, Hobby | Static single-page app on a CDN, free for personal projects |

Startup on 0.1 CPU is the main constraint. The Docker image uses a CDS archive and the C1 JIT only, which cut startup from a median of 287.7 s to 43.6 s at `--cpus=0.1` (MEASUREMENTS.md, Phase 5). A visitor after an idle period still waits for Render to wake the service (about a minute) plus that startup.

## One-time setup

Do these in order. No secret goes into the repository; each one is pasted into a dashboard.

### 1. Neon (database)

1. Sign up at neon.com with GitHub, and create a project named `paris-compass` in **AWS US East 2 (Ohio)**, the region `render.yaml` uses. Neon creates PostgreSQL 18; tests (Testcontainers) and `docker-compose.yml` use 18 too, and Flyway is pinned to a version that supports it.
2. On the project dashboard, open **Connect**, turn **Connection pooling off** (use the direct endpoint), and note the host, database, user and password.
3. The API needs JDBC form, with user and password separate:

   ```
   DATABASE_URL=jdbc:postgresql://<host>/<database>?sslmode=require
   DATABASE_USERNAME=<user>
   DATABASE_PASSWORD=<password>
   ```

   The direct endpoint matters: the pooled one (`-pooler` in the host) runs PgBouncer in transaction mode, which does not suit the JDBC driver's server-side prepared statements, and the API's pool is only 5 connections anyway.

### 2. Load the data into Neon (from your machine)

Flyway creates the schema on first connection; the ETL then downloads and loads every source.

```bash
./mvnw -q -DskipTests package
SPRING_PROFILES_ACTIVE=etl \
DATABASE_URL='jdbc:postgresql://<host>/<database>?sslmode=require' \
DATABASE_USERNAME='<user>' DATABASE_PASSWORD='<password>' \
java -jar target/paris-compass-0.0.1-SNAPSHOT.jar
```

It exits 0 when every source loaded. Expect about 153,600 observations.

### 3. Render (API)

1. Sign up at render.com with GitHub and allow access to the `pariscompass` repository.
2. **New > Blueprint**, pick the repository. Render reads `render.yaml` and asks for the secret values:

   | Key | Value |
   |---|---|
   | `DATABASE_URL` | from step 1, JDBC form |
   | `DATABASE_USERNAME` | from step 1 |
   | `DATABASE_PASSWORD` | from step 1 |
   | `GEMINI_API_KEY` | your Gemini key |
   | `CORS_ALLOWED_ORIGINS` | your Vercel URL from step 4, e.g. `https://paris-compass.vercel.app` (enter a placeholder first, update it after step 4) |

3. Deploy. The service URL looks like `https://paris-compass-api.onrender.com`. Check `https://<service>.onrender.com/actuator/health` returns `{"status":"UP"...}`.

Later deploys happen automatically on every push to `main`, after the CI checks pass (`autoDeployTrigger: checksPass`).

### 4. Vercel (frontend)

1. Sign up at vercel.com with GitHub and import the `pariscompass` repository.
2. Set **Root Directory** to `frontend`. Vercel detects Vite (build `npm run build`, output `dist`); `frontend/vercel.json` adds the rewrite that serves `index.html` for `/country/...` and `/compare` URLs.
3. Add the environment variable `VITE_API_URL` = your Render URL (no trailing slash, no `/api`).
4. Deploy, then put the Vercel URL into Render's `CORS_ALLOWED_ORIGINS` (step 3) and let Render redeploy.

### 5. GitHub (monthly data refresh)

Repository **Settings > Secrets and variables > Actions**, add `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` (the same values as step 1). The **Refresh data** workflow then runs on the 3rd of each month and can be started by hand from the Actions tab. GitHub disables scheduled workflows in a repository with no activity for 60 days; re-enable it from the Actions tab if that happens.

## Running the production image locally

```bash
docker compose up -d --build        # Postgres, API (http://localhost:8081), frontend (http://localhost:8080)
docker compose run --rm etl         # load the data the first time
```

To feel the production startup time, run the API image with Render's limits:

```bash
docker run --rm --cpus=0.1 --memory=512m -p 8081:8081 --network pariscompass_default \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/pariscompass -e DATABASE_USERNAME=pariscompass \
  -e DATABASE_PASSWORD=pariscompass -e GEMINI_API_KEY=dummy -e CORS_ALLOWED_ORIGINS=http://localhost:8080 \
  pariscompass-api
```

## Operations

| Task | How |
|---|---|
| Deploy the API | Push to `main`; Render builds the Dockerfile after CI passes |
| Deploy the frontend | Push to `main`; Vercel builds `frontend/` |
| Refresh data | Actions > Refresh data > Run workflow (tick "force" to re-ingest unchanged files) |
| Rotate a secret | Change it in the Render, Vercel or GitHub settings; Render and Vercel redeploy |
| Check the last ETL run | `SELECT * FROM etl_run ORDER BY id DESC LIMIT 1;` on Neon |
