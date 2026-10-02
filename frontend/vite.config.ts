/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: { modules: { classNameStrategy: 'non-scoped' } },
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/api/schema.d.ts', 'src/main.tsx', 'src/vite-env.d.ts'],
      reporter: ['text-summary', 'json-summary', 'html'],
      // Ratchet: just below the measured 89.1% lines, 81.0% branches, 86.4% statements, 83.7% functions
      // (2026-10-01, MEASUREMENTS.md). Raise when coverage rises; never lower silently.
      thresholds: { lines: 88, branches: 80, statements: 85, functions: 82 },
    },
  },
})
