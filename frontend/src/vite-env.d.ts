/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin, without a trailing slash or /api, e.g. https://api.example.com. */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
