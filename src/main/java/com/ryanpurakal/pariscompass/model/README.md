# model — Data Transfer Objects

Plain data containers. No logic, no Spring annotations beyond Lombok.

## Files

| Class | Direction | Description |
|-------|-----------|-------------|
| `CountryInfo.java` | outbound | Lightweight country stub: `iso3` + `name`. Used for the country list endpoint. |
| `CountryMetrics.java` | outbound | Full climate snapshot for one country: CO₂, renewables, temperature, data sources. Contains nested `SourceInfo`. |
| `ProjectionResponse.java` | outbound | Gemini AI result: projection text, model name, generation timestamp. |
| `CountryProjectionResponse.java` | outbound | Composite response: `CountryMetrics` + `ProjectionResponse`. This is what the frontend's main call receives. |

## Conventions

All classes use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` for boilerplate.  
Numeric fields use boxed types (`Double`, `Integer`) so missing data serializes as `null` in JSON rather than `0`.
