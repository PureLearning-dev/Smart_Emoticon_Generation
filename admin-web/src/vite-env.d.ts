/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_ADMIN_USERNAME: string
  readonly VITE_ADMIN_PASSWORD: string
  readonly VITE_SEND_AUTH_HEADER: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
