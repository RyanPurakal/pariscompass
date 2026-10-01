import { interpolateLab } from 'd3-interpolate'

export type Theme = 'light' | 'dark'

/**
 * Chart colors. Categorical slots are validated (dataviz validate_palette.js) for adjacent use in both
 * modes: light worst CVD dE 9.1, dark 8.4. Light slots 3-4 are below 3:1 on the surface, so every chart
 * using them also has direct labels and a table view. Colors follow the entity, never its rank.
 */
export const SERIES: Record<Theme, readonly string[]> = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500'],
}

/** One-hue blue ramp, steps 100 to 700. Low values recede toward the surface, so dark mode reverses it. */
const BLUE = ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b'] as const
export const SEQUENTIAL: Record<Theme, readonly string[]> = {
  light: BLUE,
  dark: [...BLUE].reverse(),
}

/** Blue (below) to red (above) with a neutral gray midpoint that reads as "no change". */
function divergingRamp(mid: string, cold: string, warm: string): string[] {
  const arm = (pole: string) => [1, 2 / 3, 1 / 3].map((t) => interpolateLab(mid, pole)(t))
  return [...arm(cold), mid, ...arm(warm).reverse()]
}
export const DIVERGING: Record<Theme, readonly string[]> = {
  light: divergingRamp('#f0efec', '#184f95', '#b42f2d'),
  dark: divergingRamp('#383835', '#86b6ef', '#f08a85'),
}

export const NO_DATA: Record<Theme, string> = { light: '#e1e0d9', dark: '#2c2c2a' }

/** Reserved status colors, always shown with an icon and a label. */
export const STATUS = { good: '#0ca30c', warning: '#fab219', critical: '#d03b3b' } as const
