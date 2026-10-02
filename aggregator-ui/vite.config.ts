import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Port 5173 is fixed because Keycloak and the gateway only trust this origin.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  preview: { port: 5173, strictPort: true },
})
