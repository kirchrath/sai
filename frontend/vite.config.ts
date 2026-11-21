import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Im Dev-Modus werden API, MCP und Keycloak an den laufenden Compose-Stack (nginx) weitergeleitet.
const target = process.env.SAI_DEV_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target, changeOrigin: false },
      '/auth': { target, changeOrigin: false },
    },
  },
});
