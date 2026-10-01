# config — Spring Bean Configuration

This package wires together the two external concerns the application depends on:

## Files

### `GeminiConfig.java`
Creates the `com.google.genai.Client` bean that all Gemini API calls flow through.
- Reads `GEMINI_API_KEY` (or `GOOGLE_API_KEY`) from the environment at startup.
- Fails fast with `IllegalStateException` if no key is found — prevents silent failures.
- Exposes `getModelName()` so `GeminiService` can read the configured model without importing Spring's `@Value` directly.

### `CorsConfig.java`
Registers a `CorsFilter` that allows the Vite dev server (`http://localhost:5173`) to call the backend.
- All HTTP methods are permitted; credentials are allowed.
- **To enable production or staging origins**, add them to `setAllowedOrigins` in this class.

## What passes through here

Nothing from the business domain. These classes only produce beans that other packages consume.
