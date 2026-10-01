# Paris Alignment Score (formula v1)

`GET /api/countries/{iso3}/alignment` returns a 0 to 100 score computed in Java by `scoring/AlignmentScorer` from the country's historical data. It is deterministic: the same data always gives the same score. The LLM never computes or adjusts it; it only receives the finished score as context.

## What it is, and what it is not

It is an indicator of how a country's **recent emissions and electricity trends** compare with what Paris-consistent pathways require.

It is **not** an official Paris Agreement assessment. It does not look at national pledges (NDCs), policies, land use, finance, or historical responsibility. Ratings such as Climate Action Tracker's do. The API response carries this disclaimer.

## Formula

| Component | Weight | Input | 0 points at | 100 points at |
|---|---|---|---|---|
| `emissions_trend` | 40% | Compound annual change in CO2 emissions over the last 10 years of data | +2 %/yr or worse | -6 %/yr or better |
| `emissions_level` | 25% | Latest CO2 per capita | 15 t or more | 2 t or less |
| `clean_power_level` | 20% | Latest low-carbon share of electricity (renewables + nuclear) | 0 % | 100 % |
| `clean_power_momentum` | 15% | Change in that share per year over the last 10 years | 0 pp/yr or falling | the pace needed to reach 100 % by 2050 |

```
sub-score   = clamp(100 * (x - zeroAt) / (fullAt - zeroAt), 0, 100)      linear between the anchors
score       = sum(weight_i * subScore_i) / sum(weight_i)                 over available components only
band        = HIGH if score >= 70, MEDIUM if score >= 40, else LOW
```

Sub-scores are rounded to one decimal before weighting; the score is rounded to one decimal.

### How each input is computed

- **Window.** "Last 10 years" means the 10 calendar years ending at that metric's latest year with data. At least 6 of those years must have data, otherwise the component is unavailable. Older history is ignored.
- **Emissions trend.** Ordinary least squares fit of `ln(CO2)` against year; the annual rate is `exp(slope) - 1`. A log fit gives a compound rate that is not dominated by one noisy year and is comparable across country sizes. If any value in the window is zero or negative the log is undefined and the component is unavailable.
- **Clean power momentum.** Least squares slope of the share (percentage points per year). The full-score pace is country-specific: `(100 - latest share) / (2050 - latest year)`. A country already at 99 % needs almost no growth; a country at 20 % needs about 3 pp/yr.
- **Missing data.** Unavailable components are dropped and the remaining weights rescaled to sum to 1. The response shows each component's nominal and effective weight. **Without `emissions_trend` there is no score**, because emissions are the core of Paris alignment; the response explains why instead.

## Where the constants come from

| Constant | Source or reasoning |
|---|---|
| -6 %/yr = 100 | IPCC AR6 WGIII Summary for Policymakers, C.1.1: in 1.5 °C pathways (no or limited overshoot), global net CO2 falls about 48 % from 2019 to 2030. `0.52^(1/11) - 1 = -5.8 %/yr`, rounded to -6. |
| +2 %/yr = 0 | Judgment call: sustained growth this fast is clearly diverging. For reference, world CO2 grew from 37,087 Mt (2019) to 38,599 Mt (2024) in the ingested OWID data, about +0.8 %/yr. |
| 2 t per capita = 100 | Applying the 48 % cut to 2019 world CO2 (37,087 Mt) and dividing by roughly 8.5 billion people in 2030 gives about 2.3 t. 2 t is slightly stricter. |
| 15 t per capita = 0 | Judgment call: roughly the level of the highest-emitting large economies (USA 14.2 t in 2024). |
| 100 % low-carbon power by 2050 | Fully decarbonised electricity by mid-century is a common feature of 1.5 °C pathways. In the ingested data the world went from 32.8 % (2014) to 40.9 % (2024), about 0.8 pp/yr; reaching 100 % by 2050 from 40.9 % needs about 2.3 pp/yr. |
| Weights 40/25/20/15 | Judgment call: emissions (65 % combined) dominate because they are what Paris limits; electricity (35 %) captures the main decarbonisation lever. |
| Bands 70 / 40 | Judgment call for display only. |

## Results on real data

Computed on the OWID data ingested 2026-10-01 (`GET /api/countries/{iso3}/alignment`):

| Country | Score | Band | CO2 trend | CO2 per capita | Low-carbon power | Momentum |
|---|---|---|---|---|---|---|
| France | 78.7 | HIGH | -2.7 %/yr | 4.0 t | 94.9 % | +0.52 pp/yr (needs 0.21) |
| Denmark | 78.1 | HIGH | -3.0 %/yr | 4.7 t | 91.2 % | +3.03 pp/yr (needs 0.35) |
| Sweden | 77.0 | HIGH | -2.1 %/yr | 3.6 t | 98.8 % | +0.13 pp/yr (needs 0.05) |
| United Kingdom | 76.0 | HIGH | -3.6 %/yr | 4.5 t | 64.4 % | +1.91 pp/yr (needs 1.42) |
| Germany | 71.4 | HIGH | -3.8 %/yr | 6.8 t | 59.1 % | +1.67 pp/yr (needs 1.64) |
| Brazil | 70.3 | HIGH | -0.6 %/yr | 2.3 t | 88.7 % | +0.88 pp/yr (needs 0.45) |
| Poland | 51.5 | MEDIUM | -1.5 %/yr | 7.1 t | 31.5 % | +2.24 pp/yr (needs 2.74) |
| India | 34.3 | LOW | +3.6 %/yr | 2.2 t | 26.7 % | +0.84 pp/yr (needs 2.93) |
| United States | 30.6 | LOW | -1.0 %/yr | 14.2 t | 43.0 % | +0.80 pp/yr (needs 2.28) |
| China | 29.2 | LOW | +2.8 %/yr | 8.7 t | 41.7 % | +1.35 pp/yr (needs 2.33) |
| Saudi Arabia | 6.3 | LOW | +1.0 %/yr | 20.4 t | 2.2 % | +0.18 pp/yr (needs 3.76) |
| Qatar | 2.7 | LOW | +3.0 %/yr | 41.3 t | 4.1 % | +0.48 pp/yr (needs 3.84) |

## Known limitations

- **No equity adjustment.** Fast-growing low-income countries are scored on the same emissions trend as rich ones. The per-capita component partly offsets this (India scores 98.5 on it), but the score does not model "common but differentiated responsibilities".
- **Territorial CO2 only.** Emissions embodied in imports, and land-use emissions, are not counted.
- **Electricity is not all energy.** A clean grid says nothing about transport or industry. The primary-energy share would be better but covers only 79 countries.
- **Sensitive to the window.** One unusual year (e.g. 2020) has some influence on a 10-year fit.
- **Version changes.** Any change to a constant, weight or rule must bump `formulaVersion`, so stored or quoted scores stay comparable.

## Revision history

- **v1** (2026-10-01). The first draft scored momentum against a fixed +2 pp/yr for every country. Testing on real data showed that this penalised grids that were already almost fully low-carbon (Sweden 98.8 % scored 6.5/100 on momentum). Momentum is now measured against each country's own required pace to 100 % by 2050. This was changed before v1 was released, so no published score used the old rule.
