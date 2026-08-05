import axios from 'axios'

/**
 * Base URL strategy:
 * - In dev: relative "/api" → Vite proxies to http://localhost:9010 (see vite.config.ts)
 * - In prod: set VITE_API_URL env var at build time to the deployed backend URL
 */
const baseURL = import.meta.env.VITE_API_URL ?? '/api'

export const api = axios.create({
  baseURL,
  timeout: 10_000
})

// Later (when OAuth lands) we'll attach the JWT here on every request:
//
// api.interceptors.request.use((config) => {
//   const token = localStorage.getItem('token')
//   if (token) config.headers.Authorization = `Bearer ${token}`
//   return config
// })
