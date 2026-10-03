import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev proxy makes the UI same-origin as the gateway + auth-server from the
// browser's point of view. Zero CORS to configure server-side during dev.
// Production: serve the built dist/ from the gateway (or nginx sidecar) or
// configure real CORS on the gateway + auth-server.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/admin':  { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:9010', changeOrigin: true },
    },
  },
});
